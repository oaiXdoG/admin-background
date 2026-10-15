package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 服务器：Agent 接入的业务服务器；secret 为加密保存的 Agent 签名密钥，空表示还没有接入 */
@Getter
@Setter
@TableName("server_host")
public class ServerHost {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String groupName;
    private String remark;
    private String secret;
    private String hostname;
    private String ip;
    private String os;
    private Integer cpuCores;
    private Long memoryTotal;
    private String agentVersion;
    private Long registeredAt;
    private Long createdAt;
    private Long updatedAt;
}
