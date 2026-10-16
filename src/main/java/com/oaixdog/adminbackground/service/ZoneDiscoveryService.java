package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.config.ApplicationProperties;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.KeeperException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按区自动发现：读取 ZooKeeper 上本项目的游戏服节点 /danmu/<App Key>/logic/<区ID>（规则见 danmu-login docs/service-discovery.md），
 * 每个区的查询接口为节点里的 innerHost:innerPort（连接方式为公网时用节点的 host，即公网地址），innerPort 为 0 的区不提供查询接口。第一次使用时才连接 ZooKeeper。
 */
@Service
@RequiredArgsConstructor
public class ZoneDiscoveryService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final ApplicationProperties properties;

    private CuratorFramework curator;

    /** 区 ID、查询接口地址 */
    public record Zone(int zoneId, String url) {
    }

    /** 当前可查询的区，按区 ID 排序；节点不存在时返回空列表 */
    @SuppressWarnings("unchecked")
    public List<Zone> zones(String appKey, boolean publicNetwork) {
        CuratorFramework client = client();
        String path = "/danmu/" + appKey + "/logic";
        List<Zone> zones = new ArrayList<>();
        try {
            for (String child : client.getChildren().forPath(path)) {
                byte[] bytes = client.getData().forPath(path + "/" + child);
                Map<String, Object> node = JsonUtils.string2Object(new String(bytes, StandardCharsets.UTF_8), LinkedHashMap.class);
                Object host = node.get(publicNetwork ? "host" : "innerHost");
                int port = node.get("innerPort") instanceof Number number ? number.intValue() : 0;
                if (host == null || StringUtils.isBlank(String.valueOf(host)) || port <= 0 || !child.matches("\\d{1,9}")) {
                    continue;
                }
                zones.add(new Zone(Integer.parseInt(child), "http://" + host + ":" + port));
            }
        } catch (KeeperException.NoNodeException exception) {
            return List.of();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.DATA_SOURCE_FAILED, "读取 ZooKeeper 失败：" + exception.getMessage());
        }
        zones.sort(Comparator.comparingInt(Zone::zoneId));
        return zones;
    }

    private synchronized CuratorFramework client() {
        if (StringUtils.isBlank(properties.getZookeeperAddress())) {
            throw new BusinessException(ErrorCode.ZOOKEEPER_NOT_CONFIGURED);
        }
        if (curator == null) {
            curator = CuratorFrameworkFactory.builder().connectString(properties.getZookeeperAddress())
                    .connectionTimeoutMs(CONNECT_TIMEOUT_MS).sessionTimeoutMs(30_000).retryPolicy(new RetryNTimes(1, 1_000)).build();
            curator.start();
        }
        return curator;
    }

    @PreDestroy
    public synchronized void close() {
        if (curator != null) {
            curator.close();
        }
    }
}
