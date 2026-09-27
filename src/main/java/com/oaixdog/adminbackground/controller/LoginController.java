package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.LoginRequest;
import com.oaixdog.adminbackground.protocol.LoginResponse;
import com.oaixdog.adminbackground.service.LoginService;
import com.zfoo.net.NetContext;
import com.zfoo.net.anno.PacketReceiver;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.net.session.Session;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final LoginService loginService;

    @PacketReceiver
    public void atLoginRequest(Session session, LoginRequest request, HttpAttachment attachment) {
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            LoginService.LoginResult result = loginService.login(request.getAccount(), request.getPassword());
            LoginResponse data = LoginResponse.valueOf(result.token(), result.id(), result.account(), result.username());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
        } catch (BusinessException exception) {
            log.warn("登录业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }
}
