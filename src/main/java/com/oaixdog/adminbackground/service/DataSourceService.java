package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.DataSource;
import com.oaixdog.adminbackground.entity.DataTemplate;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.DataSourceMapper;
import com.oaixdog.adminbackground.mapper.DataTemplateMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.protocol.DataSourceResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数据源：每个项目可以配置多个业务服务器查询接口（地址 + 签名密钥），密钥用 security.secret-key 加密保存，接口不返回明文。
 * 管理需要 data_source 下的按钮；查询模板页面选择数据源时只需要 data_template。
 */
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private static final String MENU = "data_source";
    private static final String TEMPLATE_MENU = "data_template";
    private static final int NAME_MAX_LENGTH = 64;
    private static final int URL_MAX_LENGTH = 256;
    private static final int SECRET_MAX_LENGTH = 256;
    private static final int REMARK_MAX_LENGTH = 128;
    private static final int TIMEOUT_MIN = 1_000;
    private static final int TIMEOUT_MAX = 60_000;
    private static final int TIMEOUT_DEFAULT = 10_000;
    public static final String QUERIES_PATH = "/admin/queries";
    public static final String QUERY_PATH = "/admin/query";

    private final PermissionService permissionService;
    private final DataSourceMapper dataSourceMapper;
    private final DataTemplateMapper dataTemplateMapper;
    private final ProjectMapper projectMapper;
    private final SecretCipher secretCipher;
    private final BusinessClient businessClient;
    private final ZoneDiscoveryService zoneDiscoveryService;

    public List<DataSourceResponse> list(String authorization, Long projectId) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, MENU, TEMPLATE_MENU);
        return dataSourceMapper.selectList(Wrappers.<DataSource>lambdaQuery().eq(DataSource::getProjectId, access.projectId()).orderByAsc(DataSource::getId))
                .stream().map(source -> DataSourceResponse.valueOf(source, templateCount(source.getId()))).toList();
    }

    public DataSourceResponse create(String authorization, Long projectId, String name, String type, String network, String url, String secret, Integer timeoutMs, String remark) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":create");
        if (StringUtils.isBlank(secret)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        DataSource source = new DataSource();
        source.setProjectId(access.projectId());
        fill(source, name, type, network, url, secret, timeoutMs, remark);
        long now = System.currentTimeMillis();
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        requireUniqueName(source);
        try {
            dataSourceMapper.insert(source);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_NAME_EXISTS);
        }
        return DataSourceResponse.valueOf(source, 0);
    }

    /** 密钥留空表示沿用已保存的密钥 */
    public DataSourceResponse update(String authorization, Long projectId, Long sourceId, String name, String type, String network, String url, String secret, Integer timeoutMs, String remark) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        DataSource source = requireSource(access.projectId(), sourceId);
        fill(source, name, type, network, url, secret, timeoutMs, remark);
        source.setUpdatedAt(System.currentTimeMillis());
        requireUniqueName(source);
        try {
            dataSourceMapper.updateById(source);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_NAME_EXISTS);
        }
        return DataSourceResponse.valueOf(source, templateCount(source.getId()));
    }

    public void remove(String authorization, Long projectId, Long sourceId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":remove");
        DataSource source = requireSource(access.projectId(), sourceId);
        if (templateCount(source.getId()) > 0) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_IN_USE);
        }
        dataSourceMapper.deleteById(source.getId());
    }

    /** 用表单里的值调用查询清单接口（按区自动发现时调用第一个可用的区）；成功返回查询数量和发现的区，失败抛出带原因的业务错误 */
    public Map<String, Object> test(String authorization, Long projectId, Long sourceId, String type, String network, String url, String secret, Integer timeoutMs) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, MENU + ":create", MENU + ":update");
        String plainSecret = secret;
        if (StringUtils.isBlank(secret)) {
            plainSecret = sourceId == null ? null : secretCipher.decrypt(requireSource(access.projectId(), sourceId).getSecret());
        }
        boolean zone = DataSource.TYPE_ZONE.equals(type);
        String targetUrl = url == null ? "" : url.trim();
        if ((!zone && !validUrl(targetUrl)) || StringUtils.isBlank(plainSecret)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String appKey = appKey(access.projectId());
        List<ZoneDiscoveryService.Zone> zones = zone ? zoneDiscoveryService.zones(appKey, DataSource.NETWORK_PUBLIC.equals(network)) : List.of();
        if (zone && zones.isEmpty()) {
            throw new BusinessException(ErrorCode.ZONE_NOT_AVAILABLE);
        }
        String callUrl = zone ? zones.getFirst().url() : targetUrl;
        BusinessClient.Target target = new BusinessClient.Target(callUrl, plainSecret, timeout(timeoutMs), appKey, access.account().getAccount());
        Map<String, Object> data = businessClient.call(target, QUERIES_PATH, Map.of());
        Object queries = data.get("queries");
        return Map.of("queryCount", queries instanceof List<?> list ? list.size() : 0, "zones", zones.stream().map(DataSourceService::zoneResponse).toList());
    }

    /** 数据源提供的查询清单，建查询模板时自动带出参数和结果字段；原样返回业务服务器的 queries */
    public Object queries(String authorization, Long projectId, Long sourceId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, TEMPLATE_MENU);
        DataSource source = requireSource(access.projectId(), sourceId);
        BusinessClient.Target target = DataSource.TYPE_ZONE.equals(source.getType()) ? firstZoneTarget(access, source) : target(access, source);
        Map<String, Object> data = businessClient.call(target, QUERIES_PATH, Map.of());
        Object queries = data.get("queries");
        return Map.of("queries", queries instanceof List<?> ? queries : List.of());
    }

    /** 按区自动发现的数据源当前可查询的区；固定地址的数据源返回空列表 */
    public List<Map<String, Object>> zones(String authorization, Long projectId, Long sourceId) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, "data_query", TEMPLATE_MENU);
        DataSource source = requireSource(access.projectId(), sourceId);
        if (!DataSource.TYPE_ZONE.equals(source.getType())) {
            return List.of();
        }
        return zones(access, source).stream().map(DataSourceService::zoneResponse).toList();
    }

    /** 按区自动发现：区 ID → 调用目标；请求的区不在线（不在当前发现的区里）时目标为 null */
    public Map<Integer, BusinessClient.Target> zoneTargets(PermissionService.ProjectAccess access, DataSource source, List<Long> zoneIds) {
        Map<Integer, String> urls = new LinkedHashMap<>();
        for (ZoneDiscoveryService.Zone zone : zones(access, source)) {
            urls.put(zone.zoneId(), zone.url());
        }
        BusinessClient.Target base = target(access, source);
        Map<Integer, BusinessClient.Target> result = new LinkedHashMap<>();
        zoneIds.stream().map(Long::intValue).distinct().sorted().forEach(zoneId -> {
            String zoneUrl = urls.get(zoneId);
            result.put(zoneId, zoneUrl == null ? null : new BusinessClient.Target(zoneUrl, base.secret(), base.timeoutMs(), base.appKey(), base.operator()));
        });
        return result;
    }

    private BusinessClient.Target firstZoneTarget(PermissionService.ProjectAccess access, DataSource source) {
        List<ZoneDiscoveryService.Zone> zones = zones(access, source);
        if (zones.isEmpty()) {
            throw new BusinessException(ErrorCode.ZONE_NOT_AVAILABLE);
        }
        BusinessClient.Target base = target(access, source);
        return new BusinessClient.Target(zones.getFirst().url(), base.secret(), base.timeoutMs(), base.appKey(), base.operator());
    }

    private List<ZoneDiscoveryService.Zone> zones(PermissionService.ProjectAccess access, DataSource source) {
        return zoneDiscoveryService.zones(appKey(access.projectId()), DataSource.NETWORK_PUBLIC.equals(source.getNetwork()));
    }

    private static Map<String, Object> zoneResponse(ZoneDiscoveryService.Zone zone) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zoneId", zone.zoneId());
        result.put("url", zone.url());
        result.put("status", zone.status());
        return result;
    }

    /** 调用业务服务器用的目标：解密密钥，带上项目 App Key 和操作人 */
    public BusinessClient.Target target(PermissionService.ProjectAccess access, DataSource source) {
        String secret = secretCipher.decrypt(source.getSecret());
        if (secret == null) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_FAILED, "密钥无法解密，请重新填写数据源密钥");
        }
        return new BusinessClient.Target(source.getUrl(), secret, source.getTimeoutMs(), appKey(access.projectId()), access.account().getAccount());
    }

    public DataSource requireSource(long projectId, Long sourceId) {
        DataSource source = sourceId == null ? null : dataSourceMapper.selectById(sourceId);
        if (source == null || !Objects.equals(source.getProjectId(), projectId)) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_NOT_FOUND);
        }
        return source;
    }

    private void fill(DataSource source, String name, String type, String network, String url, String secret, Integer timeoutMs, String remark) {
        String sourceType = StringUtils.isBlank(type) ? DataSource.TYPE_HTTP : type.trim();
        boolean zone = DataSource.TYPE_ZONE.equals(sourceType);
        String sourceName = name == null ? "" : name.trim();
        String sourceUrl = zone || url == null ? "" : url.trim();
        String sourceRemark = remark == null ? "" : remark.trim();
        String sourceNetwork = StringUtils.isBlank(network) ? DataSource.NETWORK_INNER : network.trim();
        if (!DataSource.TYPE_HTTP.equals(sourceType) && !zone || !DataSource.NETWORK_INNER.equals(sourceNetwork) && !DataSource.NETWORK_PUBLIC.equals(sourceNetwork)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (sourceName.isEmpty() || sourceName.length() > NAME_MAX_LENGTH || (!zone && !validUrl(sourceUrl)) || sourceRemark.length() > REMARK_MAX_LENGTH
                || (secret != null && secret.length() > SECRET_MAX_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        source.setName(sourceName);
        source.setType(sourceType);
        source.setNetwork(sourceNetwork);
        source.setUrl(sourceUrl.replaceAll("/+$", ""));
        source.setTimeoutMs(timeout(timeoutMs));
        source.setRemark(sourceRemark);
        if (StringUtils.isNotBlank(secret)) {
            source.setSecret(secretCipher.encrypt(secret));
        }
    }

    private static int timeout(Integer timeoutMs) {
        int value = timeoutMs == null ? TIMEOUT_DEFAULT : timeoutMs;
        if (value < TIMEOUT_MIN || value > TIMEOUT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return value;
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

    private void requireUniqueName(DataSource source) {
        if (dataSourceMapper.selectCount(Wrappers.<DataSource>lambdaQuery().eq(DataSource::getProjectId, source.getProjectId())
                .eq(DataSource::getName, source.getName()).ne(source.getId() != null, DataSource::getId, source.getId())) > 0) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_NAME_EXISTS);
        }
    }

    private long templateCount(Long sourceId) {
        return dataTemplateMapper.selectCount(Wrappers.<DataTemplate>lambdaQuery().eq(DataTemplate::getSourceId, sourceId));
    }

    private String appKey(long projectId) {
        return projectMapper.selectById(projectId).getAppKey();
    }
}
