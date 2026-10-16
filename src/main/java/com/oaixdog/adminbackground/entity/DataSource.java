package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 数据源：业务服务器按约定提供的查询接口，secret 为加密保存的签名密钥 */
@Getter
@Setter
@TableName("data_source")
public class DataSource {

    public static final String TYPE_HTTP = "http";
    /** 按区自动发现：地址从 ZooKeeper 的游戏服节点读取，url 为空 */
    public static final String TYPE_ZONE = "zone";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String type;
    private String url;
    private String secret;
    private Integer timeoutMs;
    private String remark;
    private Long createdAt;
    private Long updatedAt;
}
