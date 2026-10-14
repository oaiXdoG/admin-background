package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.SystemSetting;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.SystemSettingMapper;
import com.oaixdog.adminbackground.protocol.LogStorageResponse;
import com.oaixdog.adminbackground.protocol.LogStorageTestResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 日志存储（ClickHouse）连接配置，存在 system_setting 的 logStorage.* 键里，密码加密保存，接口不返回明文。
 * 只读账号用于数据中心查询，管理账号只用于修改保留期。
 */
@Service
@RequiredArgsConstructor
public class LogStorageService {

    private static final Logger log = LoggerFactory.getLogger(LogStorageService.class);

    public static final String TABLE = "event";
    private static final String VIEW_MENU = "manage_setting";
    private static final String EDIT_BUTTON = "manage_setting:storage";
    private static final String PREFIX = "logStorage.";
    private static final String URL = PREFIX + "url";
    private static final String DATABASE = PREFIX + "database";
    private static final String READ_USER = PREFIX + "readUser";
    private static final String READ_PASSWORD = PREFIX + "readPassword";
    private static final String ADMIN_USER = PREFIX + "adminUser";
    private static final String ADMIN_PASSWORD = PREFIX + "adminPassword";
    private static final int URL_MAX_LENGTH = 256;
    private static final int PASSWORD_MAX_LENGTH = 128;
    private static final int RETENTION_MAX_DAYS = 3650;
    public static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
    private static final Pattern USER = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final Pattern RETENTION = Pattern.compile("toIntervalDay\\((\\d+)\\)|INTERVAL\\s+(\\d+)\\s+DAY", Pattern.CASE_INSENSITIVE);

    private final PermissionService permissionService;
    private final SystemSettingMapper systemSettingMapper;
    private final SecretCipher secretCipher;
    private final ClickHouseClient clickHouseClient;

    public LogStorageResponse get(String authorization) {
        permissionService.requirePlatformMenu(authorization, VIEW_MENU);
        return response(load());
    }

    @Transactional
    public LogStorageResponse update(String authorization, String url, String database, String readUser, String readPassword,
                                     String adminUser, String adminPassword) {
        permissionService.requirePlatformMenu(authorization, EDIT_BUTTON);
        Map<String, String> saved = load();
        Values values = validate(saved, url, database, readUser, readPassword, adminUser, adminPassword);
        Map<String, String> next = new LinkedHashMap<>();
        next.put(URL, values.url());
        next.put(DATABASE, values.database());
        next.put(READ_USER, values.readUser());
        next.put(READ_PASSWORD, StringUtils.isBlank(readPassword) ? saved.getOrDefault(READ_PASSWORD, "") : secretCipher.encrypt(readPassword));
        next.put(ADMIN_USER, values.adminUser());
        String encryptedAdmin = StringUtils.isBlank(adminPassword) ? saved.getOrDefault(ADMIN_PASSWORD, "") : secretCipher.encrypt(adminPassword);
        next.put(ADMIN_PASSWORD, StringUtils.isBlank(values.adminUser()) ? "" : encryptedAdmin);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> entry : next.entrySet()) {
            SystemSetting setting = new SystemSetting();
            setting.setKey(entry.getKey());
            setting.setValue(entry.getValue());
            setting.setUpdatedAt(now);
            if (systemSettingMapper.updateById(setting) == 0) {
                systemSettingMapper.insert(setting);
            }
        }
        return response(load());
    }

    /** 用表单里的值测试连接，密码留空时使用已保存的密码；返回只读账号和管理账号各自的结果 */
    public LogStorageTestResponse test(String authorization, String url, String database, String readUser, String readPassword,
                                       String adminUser, String adminPassword) {
        permissionService.requirePlatformMenu(authorization, EDIT_BUTTON);
        Values values = validate(load(), url, database, readUser, readPassword, adminUser, adminPassword);
        LogStorageTestResponse response = new LogStorageTestResponse();
        ClickHouseClient.Connection reader = new ClickHouseClient.Connection(values.url(), values.database(), values.readUser(), values.readPassword());
        try {
            response.setVersion(String.valueOf(clickHouseClient.query(reader, "SELECT version() AS version", Map.of(), Map.of()).getFirst().get("version")));
            response.setRetentionDays(retentionDays(reader));
            response.setReadOk(true);
        } catch (ClickHouseClient.ClickHouseException exception) {
            response.setReadOk(false);
            response.setReadMessage(exception.getMessage());
        }
        if (StringUtils.isNotBlank(values.adminUser())) {
            ClickHouseClient.Connection admin = new ClickHouseClient.Connection(values.url(), values.database(), values.adminUser(), values.adminPassword());
            try {
                clickHouseClient.query(admin, "SELECT 1 AS ok", Map.of(), Map.of());
                response.setAdminOk(true);
            } catch (ClickHouseClient.ClickHouseException exception) {
                response.setAdminOk(false);
                response.setAdminMessage(exception.getMessage());
            }
        }
        return response;
    }

    /** 修改普通日志的保留天数，永久保存的日志（retention = 'permanent'）不受影响 */
    public int updateRetention(String authorization, Integer retentionDays) {
        permissionService.requirePlatformMenu(authorization, EDIT_BUTTON);
        if (retentionDays == null || retentionDays < 1 || retentionDays > RETENTION_MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Map<String, String> saved = load();
        String adminPassword = secretCipher.decrypt(saved.get(ADMIN_PASSWORD));
        if (StringUtils.isBlank(saved.get(URL)) || StringUtils.isBlank(saved.get(ADMIN_USER)) || adminPassword == null) {
            throw new BusinessException(ErrorCode.LOG_STORAGE_ADMIN_MISSING);
        }
        String database = saved.get(DATABASE);
        ClickHouseClient.Connection admin = new ClickHouseClient.Connection(saved.get(URL), database, saved.get(ADMIN_USER), adminPassword);
        // 天数已校验为整数、库名已校验为标识符，DDL 不支持查询参数，只能拼接
        String sql = "ALTER TABLE `" + database + "`." + TABLE + " MODIFY TTL toDateTime(time) + INTERVAL " + retentionDays
                + " DAY DELETE WHERE retention = 'normal'";
        try {
            clickHouseClient.execute(admin, sql);
        } catch (ClickHouseClient.ClickHouseException exception) {
            log.warn("修改日志保留期失败: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.LOG_STORAGE_UNAVAILABLE);
        }
        return retentionDays;
    }

    /** 数据中心查询使用的只读连接；未配置或密码无法解密时提示先配置 */
    public ClickHouseClient.Connection requireReader() {
        Map<String, String> saved = load();
        String password = secretCipher.decrypt(saved.get(READ_PASSWORD));
        if (StringUtils.isBlank(saved.get(URL)) || StringUtils.isBlank(saved.get(DATABASE)) || StringUtils.isBlank(saved.get(READ_USER)) || password == null) {
            throw new BusinessException(ErrorCode.LOG_STORAGE_NOT_CONFIGURED);
        }
        return new ClickHouseClient.Connection(saved.get(URL), saved.get(DATABASE), saved.get(READ_USER), password);
    }

    /** 当前连接的库名，未配置时为空字符串 */
    public String database() {
        return load().getOrDefault(DATABASE, "");
    }

    private Integer retentionDays(ClickHouseClient.Connection reader) {
        List<Map<String, Object>> rows = clickHouseClient.query(reader,
                "SELECT engine_full FROM system.tables WHERE database = {database:String} AND name = {table:String}",
                Map.of("database", reader.database(), "table", TABLE), Map.of());
        if (rows.isEmpty()) {
            return null;
        }
        Matcher matcher = RETENTION.matcher(String.valueOf(rows.getFirst().get("engine_full")));
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
    }

    private Values validate(Map<String, String> saved, String url, String database, String readUser, String readPassword,
                            String adminUser, String adminPassword) {
        String storageUrl = url == null ? "" : url.trim();
        String storageDatabase = database == null ? "" : database.trim();
        String storageReadUser = readUser == null ? "" : readUser.trim();
        String storageAdminUser = adminUser == null ? "" : adminUser.trim();
        if (!validUrl(storageUrl) || !IDENTIFIER.matcher(storageDatabase).matches() || !USER.matcher(storageReadUser).matches()
                || (StringUtils.isNotBlank(storageAdminUser) && !USER.matcher(storageAdminUser).matches())
                || (readPassword != null && readPassword.length() > PASSWORD_MAX_LENGTH)
                || (adminPassword != null && adminPassword.length() > PASSWORD_MAX_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String plainRead = StringUtils.isBlank(readPassword) ? secretCipher.decrypt(saved.get(READ_PASSWORD)) : readPassword;
        String plainAdmin = StringUtils.isBlank(adminPassword) ? secretCipher.decrypt(saved.get(ADMIN_PASSWORD)) : adminPassword;
        if (plainRead == null || (StringUtils.isNotBlank(storageAdminUser) && plainAdmin == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new Values(storageUrl, storageDatabase, storageReadUser, plainRead, storageAdminUser, plainAdmin);
    }

    private static boolean validUrl(String url) {
        if (url.isEmpty() || url.length() > URL_MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && StringUtils.isNotBlank(uri.getHost())
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private Map<String, String> load() {
        return systemSettingMapper.selectList(Wrappers.<SystemSetting>lambdaQuery().likeRight(SystemSetting::getKey, PREFIX)).stream()
                .collect(Collectors.toMap(SystemSetting::getKey, SystemSetting::getValue));
    }

    private LogStorageResponse response(Map<String, String> saved) {
        LogStorageResponse response = new LogStorageResponse();
        response.setUrl(saved.getOrDefault(URL, ""));
        response.setDatabase(saved.getOrDefault(DATABASE, ""));
        response.setReadUser(saved.getOrDefault(READ_USER, ""));
        response.setReadPasswordSet(secretCipher.decrypt(saved.get(READ_PASSWORD)) != null);
        response.setAdminUser(saved.getOrDefault(ADMIN_USER, ""));
        response.setAdminPasswordSet(secretCipher.decrypt(saved.get(ADMIN_PASSWORD)) != null);
        return response;
    }

    private record Values(String url, String database, String readUser, String readPassword, String adminUser, String adminPassword) {
    }
}
