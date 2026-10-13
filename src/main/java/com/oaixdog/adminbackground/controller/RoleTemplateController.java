package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.ProjectTemplateOptionsRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateCreateRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateListRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateMenuListRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateMenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateRemoveRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateUpdateRequest;
import com.oaixdog.adminbackground.service.RoleService;
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
public class RoleTemplateController {

    private final RoleService roleService;

    @PacketReceiver
    public void atRoleTemplateListRequest(Session session, RoleTemplateListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.list(auth(attachment), RoleService.Scope.template()));
    }

    @PacketReceiver
    public void atRoleTemplateCreateRequest(Session session, RoleTemplateCreateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.create(auth(attachment), RoleService.Scope.template(), request.getName(), request.getCode(), request.getDescription(), request.getStatus()));
    }

    @PacketReceiver
    public void atRoleTemplateUpdateRequest(Session session, RoleTemplateUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.update(auth(attachment), RoleService.Scope.template(), request.getRoleId(), request.getName(), request.getCode(), request.getDescription(), request.getStatus()));
    }

    @PacketReceiver
    public void atRoleTemplateRemoveRequest(Session session, RoleTemplateRemoveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            roleService.remove(auth(attachment), RoleService.Scope.template(), request.getRoleId());
            return null;
        });
    }

    @PacketReceiver
    public void atRoleTemplateMenuListRequest(Session session, RoleTemplateMenuListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.listMenus(auth(attachment), RoleService.Scope.template(), request.getRoleId()));
    }

    @PacketReceiver
    public void atRoleTemplateMenuUpdateRequest(Session session, RoleTemplateMenuUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            roleService.updateMenus(auth(attachment), RoleService.Scope.template(), request.getRoleId(), request.getMenuIds());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectTemplateOptionsRequest(Session session, ProjectTemplateOptionsRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.options(auth(attachment), RoleService.OptionSource.PROJECT_CREATE, null));
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
