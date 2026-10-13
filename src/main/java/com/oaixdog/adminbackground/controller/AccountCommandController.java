package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.AccountCreateRequest;
import com.oaixdog.adminbackground.protocol.AccountListRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectListRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectOptionsRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectRolesRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectUpdateRequest;
import com.oaixdog.adminbackground.protocol.AccountRoleUpdateRequest;
import com.oaixdog.adminbackground.protocol.AccountStatusRequest;
import com.oaixdog.adminbackground.protocol.AccountUpdateRequest;
import com.oaixdog.adminbackground.service.AccountCommandService;
import com.zfoo.net.NetContext;
import com.zfoo.net.anno.PacketReceiver;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.net.session.Session;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountCommandController {

    private final AccountCommandService accountCommandService;

    @PacketReceiver
    public void atAccountListRequest(Session session, AccountListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> accountCommandService.list(auth(attachment)));
    }

    @PacketReceiver
    public void atAccountCreateRequest(Session session, AccountCreateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> accountCommandService.create(auth(attachment), request.getAccount(), request.getPassword(), request.getUsername(), request.getRemark()));
    }

    @PacketReceiver
    public void atAccountUpdateRequest(Session session, AccountUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            accountCommandService.update(auth(attachment), request.getAccountId(), request.getUsername(), request.getPassword(), request.getRemark());
            return null;
        });
    }

    @PacketReceiver
    public void atAccountStatusRequest(Session session, AccountStatusRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            accountCommandService.updateStatus(auth(attachment), request.getAccountId(), request.getStatus());
            return null;
        });
    }

    @PacketReceiver
    public void atAccountRoleUpdateRequest(Session session, AccountRoleUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            accountCommandService.updateRoles(auth(attachment), request.getAccountId(), request.getRoleIds());
            return null;
        });
    }

    @PacketReceiver
    public void atAccountProjectListRequest(Session session, AccountProjectListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> accountCommandService.listProjects(auth(attachment), request.getAccountId()));
    }

    @PacketReceiver
    public void atAccountProjectRolesRequest(Session session, AccountProjectRolesRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> accountCommandService.listProjectRoles(auth(attachment), request.getProjectId()));
    }

    @PacketReceiver
    public void atAccountProjectUpdateRequest(Session session, AccountProjectUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            accountCommandService.updateProjects(auth(attachment), request.getAccountId(), request.getMemberships());
            return null;
        });
    }

    @PacketReceiver
    public void atAccountProjectOptionsRequest(Session session, AccountProjectOptionsRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> accountCommandService.listProjectOptions(auth(attachment)));
    }

    private void handle(Session session, HttpAttachment attachment, Object request, Handler handler) {
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(handler.run()), attachment);
        } catch (BusinessException exception) {
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    private static String auth(HttpAttachment attachment) {
        return attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
    }

    private interface Handler {
        Object run();
    }
}
