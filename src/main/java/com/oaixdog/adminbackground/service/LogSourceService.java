package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.LogSource;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.LogSourceMapper;
import com.oaixdog.adminbackground.protocol.LogSourceResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 日志源：每个项目一个，配置查询日志用的 ClickHouse（地址、库名、只读账号）。集群对外也只有一个入口，不需要多个。
 * 密码用 security.secret-key 加密保存，接口不返回明文。查询时仍强制按本项目的 App Key 过滤。
 */
@Service
@RequiredArgsConstructor
public class LogSourceService {

    private static final String MENU = "log_source";
    private static final int URL_MAX_LENGTH = 256;
    private static final int PASSWORD_MAX_LENGTH = 128;
    private static final Pattern USER = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private final PermissionService permissionService;
    private final LogSourceMapper logSourceMapper;
    private final SecretCipher secretCipher;
    private final ClickHouseClient clickHouseClient;

    /** 未配置时 source 为 null */
    public Map<String, Object> get(String authorization, Long projectId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        LogSource source = find(access.projectId());
        return Collections.singletonMap("source", source == null ? null : LogSourceResponse.valueOf(source));
    }

    /** 新增或覆盖；首次保存密码必填，之后留空表示沿用 */
    public LogSourceResponse save(String authorization, Long projectId, String url, String database, String readUser, String readPassword) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        LogSource source = find(access.projectId());
        if (source == null && StringUtils.isBlank(readPassword)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Values values = validate(url, database, readUser);
        if (readPassword != null && readPassword.length() > PASSWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long now = System.currentTimeMillis();
        if (source == null) {
            source = new LogSource();
            source.setProjectId(access.projectId());
            source.setCreatedAt(now);
        }
        source.setUrl(values.url());
        source.setDatabase(values.database());
        source.setReadUser(values.readUser());
        if (StringUtils.isNotBlank(readPassword)) {
            source.setReadPassword(secretCipher.encrypt(readPassword));
        }
        source.setUpdatedAt(now);
        if (source.getId() == null) {
            logSourceMapper.insert(source);
        } else {
            logSourceMapper.updateById(source);
        }
        return LogSourceResponse.valueOf(source);
    }

    /** 用表单里的值测试，不保存：返回 ClickHouse 版本；连接失败抛出带原因的业务错误 */
    public Map<String, Object> test(String authorization, Long projectId, String url, String database, String readUser, String readPassword) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        Values values = validate(url, database, readUser);
        String password = readPassword;
        if (StringUtils.isBlank(readPassword)) {
            LogSource saved = find(access.projectId());
            password = saved == null ? null : secretCipher.decrypt(saved.getReadPassword());
        }
        if (StringUtils.isBlank(password)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        ClickHouseClient.Connection connection = new ClickHouseClient.Connection(values.url(), values.database(), values.readUser(), password);
        try {
            requireAllowedHost(values.url());
            Object version = clickHouseClient.query(connection, "SELECT version() AS version", Map.of(), Map.of()).getFirst().get("version");
            return Map.of("version", String.valueOf(version));
        } catch (ClickHouseClient.ClickHouseException exception) {
            throw new BusinessException(ErrorCode.LOG_SOURCE_FAILED, exception.getMessage());
        }
    }

    /** 查询用的只读连接 */
    public ClickHouseClient.Connection requireConnection(long projectId) {
        LogSource source = find(projectId);
        if (source == null) {
            throw new BusinessException(ErrorCode.LOG_STORAGE_NOT_CONFIGURED);
        }
        String password = secretCipher.decrypt(source.getReadPassword());
        if (password == null) {
            throw new BusinessException(ErrorCode.LOG_SOURCE_FAILED, "密码无法解密，请重新填写日志源密码");
        }
        requireAllowedHost(source.getUrl());
        return new ClickHouseClient.Connection(source.getUrl(), source.getDatabase(), source.getReadUser(), password);
    }

    private LogSource find(long projectId) {
        return logSourceMapper.selectOne(Wrappers.<LogSource>lambdaQuery().eq(LogSource::getProjectId, projectId));
    }

    private static Values validate(String url, String database, String readUser) {
        String sourceUrl = url == null ? "" : url.trim().replaceAll("/+$", "");
        String sourceDatabase = database == null ? "" : database.trim();
        String user = readUser == null ? "" : readUser.trim();
        if (!validUrl(sourceUrl) || !LogStorageService.IDENTIFIER.matcher(sourceDatabase).matches() || !USER.matcher(user).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new Values(sourceUrl, sourceDatabase, user);
    }

    private static boolean validUrl(String url) {
        if (url.isEmpty() || url.length() > URL_MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && StringUtils.isNotBlank(uri.getHost())
                    && uri.getQuery() == null && uri.getFragment() == null && uri.getUserInfo() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /** 与数据源相同：拒绝链路本地（含云厂商元数据）、组播和通配地址 */
    private static void requireAllowedHost(String url) {
        String host = URI.create(url).getHost();
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLinkLocalAddress() || address.isMulticastAddress() || address.isAnyLocalAddress()) {
                    throw new BusinessException(ErrorCode.LOG_SOURCE_FAILED, "不允许访问该地址");
                }
            }
        } catch (UnknownHostException exception) {
            throw new BusinessException(ErrorCode.LOG_SOURCE_FAILED, "无法解析主机名");
        }
    }

    private record Values(String url, String database, String readUser) {
    }
}
