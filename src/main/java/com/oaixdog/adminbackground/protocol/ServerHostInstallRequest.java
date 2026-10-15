package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 生成新的一次性安装令牌；已接入的服务器重新安装后更换密钥 */
@Getter
@Setter
@Protocol(id = 3104)
public class ServerHostInstallRequest {

    private Long projectId;
    private Long serverId;
}
