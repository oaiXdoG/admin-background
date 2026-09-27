package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.AccountCreateRequest;
import com.oaixdog.adminbackground.protocol.AccountCreateResponse;
import com.oaixdog.adminbackground.protocol.AccountListRequest;
import com.oaixdog.adminbackground.protocol.AccountResponse;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.service.AccountCommandService;
import com.zfoo.net.NetContext;
import com.zfoo.net.anno.PacketReceiver;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.net.session.Session;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AccountCommandController {

    private static final Logger log = LoggerFactory.getLogger(AccountCommandController.class);

    private final AccountCommandService accountCommandService;

    @PacketReceiver
    public void atAccountCreateRequest(Session session, AccountCreateRequest request, HttpAttachment attachment) {
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            String authorization = attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
            AccountCreateResponse data = accountCommandService.create(
                    authorization,
                    request.getAccount(),
                    request.getPassword(),
                    request.getUsername());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
        } catch (BusinessException exception) {
            log.warn("创建账号业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atAccountListRequest(Session session, AccountListRequest request, HttpAttachment attachment) {
        try {
            if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            String authorization = attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
            List<AccountResponse> data = accountCommandService.list(authorization);
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
        } catch (BusinessException exception) {
            log.warn("账号列表业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }
}
