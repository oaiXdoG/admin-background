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
 * 平台日志存储：只用于管理普通日志的保留期（ClickHouse 的 TTL 作用于整张表，影响共用这张表的所有项目，所以归平台管理）。
 * 配置存在 system_setting 的 logStorage.* 键里，管理账号密码加密保存。各项目查询日志用的只读连接在「日志中心 → 日志源」里各自配置。
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

    /** 管理密码留空表示沿用已保存的密码 */
    @Transactional
    public LogStorageResponse update(String authorization, String url, String database, String adminUser, String adminPassword) {
        permissionService.requirePlatformMenu(authorization, EDIT_BUTTON);
        Map<String, String> saved = load();
        Values values = validate(saved, url, database, adminUser, adminPassword);
        Map<String, String> next = new LinkedHashMap<>();
        next.put(URL, values.url());
        next.put(DATABASE, values.database());
        next.put(ADMIN_USER, values.adminUser());
        next.put(ADMIN_PASSWORD, StringUtils.isBlank(adminPassword) ? saved.getOrDefault(ADMIN_PASSWORD, "") : secretCipher.encrypt(adminPassword));
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

    /** 用表单里的值测试管理账号，并读出当前保留期；连接失败不是业务错误，结果在 adminOk / adminMessage 里 */
    public LogStorageTestResponse test(String authorization, String url, String database, String adminUser, String adminPassword) {
        permissionService.requirePlatformMenu(authorization, EDIT_BUTTON);
        Values values = validate(load(), url, database, adminUser, adminPassword);
        ClickHouseClient.Connection admin = new ClickHouseClient.Connection(values.url(), values.database(), values.adminUser(), values.adminPassword());
        LogStorageTestResponse response = new LogStorageTestResponse();
        try {
            response.setVersion(String.valueOf(clickHouseClient.query(admin, "SELECT version() AS version", Map.of(), Map.of()).getFirst().get("version")));
            response.setRetentionDays(retentionDays(admin));
            response.setAdminOk(true);
        } catch (ClickHouseClient.ClickHouseException exception) {
            response.setAdminOk(false);
            response.setAdminMessage(exception.getMessage());
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

    private Integer retentionDays(ClickHouseClient.Connection connection) {
        List<Map<String, Object>> rows = clickHouseClient.query(connection,
                "SELECT engine_full FROM system.tables WHERE database = {database:String} AND name = {table:String}",
                Map.of("database", connection.database(), "table", TABLE), Map.of());
        if (rows.isEmpty()) {
            return null;
        }
        Matcher matcher = RETENTION.matcher(String.valueOf(rows.getFirst().get("engine_full")));
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
    }

    private Values validate(Map<String, String> saved, String url, String database, String adminUser, String adminPassword) {
        String storageUrl = url == null ? "" : url.trim();
        String storageDatabase = database == null ? "" : database.trim();
        String storageAdminUser = adminUser == null ? "" : adminUser.trim();
        if (!validUrl(storageUrl) || !IDENTIFIER.matcher(storageDatabase).matches() || !USER.matcher(storageAdminUser).matches()
                || (adminPassword != null && adminPassword.length() > PASSWORD_MAX_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String plainAdmin = StringUtils.isBlank(adminPassword) ? secretCipher.decrypt(saved.get(ADMIN_PASSWORD)) : adminPassword;
        if (plainAdmin == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new Values(storageUrl, storageDatabase, storageAdminUser, plainAdmin);
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

    /** 平台 ClickHouse 管理账号连接（服务器监控数据也存在这个库），未配置时为 null */
    public ClickHouseClient.Connection adminConnection() {
        Map<String, String> saved = load();
        String adminPassword = secretCipher.decrypt(saved.get(ADMIN_PASSWORD));
        if (StringUtils.isBlank(saved.get(URL)) || StringUtils.isBlank(saved.get(DATABASE)) || StringUtils.isBlank(saved.get(ADMIN_USER)) || adminPassword == null) {
            return null;
        }
        return new ClickHouseClient.Connection(saved.get(URL), saved.get(DATABASE), saved.get(ADMIN_USER), adminPassword);
    }

    private Map<String, String> load() {
        return systemSettingMapper.selectList(Wrappers.<SystemSetting>lambdaQuery().likeRight(SystemSetting::getKey, PREFIX)).stream()
                .collect(Collectors.toMap(SystemSetting::getKey, SystemSetting::getValue));
    }

    private LogStorageResponse response(Map<String, String> saved) {
        LogStorageResponse response = new LogStorageResponse();
        response.setUrl(saved.getOrDefault(URL, ""));
        response.setDatabase(saved.getOrDefault(DATABASE, ""));
        response.setAdminUser(saved.getOrDefault(ADMIN_USER, ""));
        response.setAdminPasswordSet(secretCipher.decrypt(saved.get(ADMIN_PASSWORD)) != null);
        return response;
    }

    private record Values(String url, String database, String adminUser, String adminPassword) {
    }
}
