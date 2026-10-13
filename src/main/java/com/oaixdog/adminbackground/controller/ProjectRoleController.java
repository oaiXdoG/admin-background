package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.ProjectMemberRolesRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleCreateRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleImportRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleListRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleMenuListRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleMenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleRemoveRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleTemplatesRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleUpdateRequest;
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
public class ProjectRoleController {

    private final RoleService roleService;

    @PacketReceiver
    public void atProjectRoleListRequest(Session session, ProjectRoleListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.list(auth(attachment), RoleService.Scope.project(request.getProjectId())));
    }

    @PacketReceiver
    public void atProjectRoleCreateRequest(Session session, ProjectRoleCreateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.create(auth(attachment), RoleService.Scope.project(request.getProjectId()), request.getName(), request.getCode(), request.getDescription(), request.getStatus()));
    }

    @PacketReceiver
    public void atProjectRoleUpdateRequest(Session session, ProjectRoleUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.update(auth(attachment), RoleService.Scope.project(request.getProjectId()), request.getRoleId(), request.getName(), request.getCode(), request.getDescription(), request.getStatus()));
    }

    @PacketReceiver
    public void atProjectRoleRemoveRequest(Session session, ProjectRoleRemoveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            roleService.remove(auth(attachment), RoleService.Scope.project(request.getProjectId()), request.getRoleId());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectRoleMenuListRequest(Session session, ProjectRoleMenuListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.listMenus(auth(attachment), RoleService.Scope.project(request.getProjectId()), request.getRoleId()));
    }

    @PacketReceiver
    public void atProjectRoleMenuUpdateRequest(Session session, ProjectRoleMenuUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            roleService.updateMenus(auth(attachment), RoleService.Scope.project(request.getProjectId()), request.getRoleId(), request.getMenuIds());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectRoleImportRequest(Session session, ProjectRoleImportRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.importTemplates(auth(attachment), request.getProjectId(), request.getTemplateIds()));
    }

    @PacketReceiver
    public void atProjectRoleTemplatesRequest(Session session, ProjectRoleTemplatesRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.options(auth(attachment), RoleService.OptionSource.PROJECT_ROLE_IMPORT, request.getProjectId()));
    }

    @PacketReceiver
    public void atProjectMemberRolesRequest(Session session, ProjectMemberRolesRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> roleService.options(auth(attachment), RoleService.OptionSource.PROJECT_MEMBER, request.getProjectId()));
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
