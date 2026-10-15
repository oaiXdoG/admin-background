package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** Agent 用一次性安装令牌注册，换取服务器 ID 和签名密钥 */
@Getter
@Setter
@Protocol(id = 3116)
public class AgentRegisterRequest {

    private String token;
    private String hostname;
    private String os;
    /** 本机网卡的内网 IPv4，多个用逗号分隔 */
    private String privateIp;
    private Integer cpuCores;
    private Long memoryTotal;
    private String agentVersion;
}
