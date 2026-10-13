package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.AccountCreateRequest;
import com.oaixdog.adminbackground.protocol.AccountCreateResponse;
import com.oaixdog.adminbackground.protocol.AccountListRequest;
import com.oaixdog.adminbackground.protocol.AccountMembership;
import com.oaixdog.adminbackground.protocol.AccountProjectListRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectUpdateRequest;
import com.oaixdog.adminbackground.protocol.AccountResponse;
import com.oaixdog.adminbackground.protocol.AccountUpdateRequest;
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

    @PacketReceiver
    public void atAccountUpdateRequest(Session session, AccountUpdateRequest request, HttpAttachment attachment) {
        try {
            if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            String authorization = attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
            accountCommandService.update(authorization, request.getAccountId(), request.getUsername(), request.getPassword());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(null), attachment);
        } catch (BusinessException exception) {
            log.warn("编辑账号业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atAccountProjectListRequest(Session session, AccountProjectListRequest request, HttpAttachment attachment) {
        try {
            if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            String authorization = attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
            List<AccountMembership> data = accountCommandService.listProjects(authorization, request.getAccountId());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
        } catch (BusinessException exception) {
            log.warn("账号项目关联查询业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atAccountProjectUpdateRequest(Session session, AccountProjectUpdateRequest request, HttpAttachment attachment) {
        try {
            if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            String authorization = attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
            accountCommandService.updateProjects(authorization, request.getAccountId(), request.getMemberships());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(null), attachment);
        } catch (BusinessException exception) {
            log.warn("账号项目关联保存业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }
}
