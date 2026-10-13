package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.ProjectCloseRequest;
import com.oaixdog.adminbackground.protocol.ProjectCreateRequest;
import com.oaixdog.adminbackground.protocol.ProjectManageListRequest;
import com.oaixdog.adminbackground.protocol.ProjectReopenRequest;
import com.oaixdog.adminbackground.protocol.ProjectSwitchRequest;
import com.oaixdog.adminbackground.protocol.ProjectUpdateRequest;
import com.oaixdog.adminbackground.service.ProjectCommandService;
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
public class ProjectCommandController {

    private final ProjectCommandService projectCommandService;

    @PacketReceiver
    public void atProjectManageListRequest(Session session, ProjectManageListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> projectCommandService.manageList(auth(attachment)));
    }

    @PacketReceiver
    public void atProjectCreateRequest(Session session, ProjectCreateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> projectCommandService.create(auth(attachment), request.getAppKey(), request.getName(), request.getDescription(), request.getTemplateIds()));
    }

    @PacketReceiver
    public void atProjectUpdateRequest(Session session, ProjectUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            projectCommandService.update(auth(attachment), request.getProjectId(), request.getAppKey(), request.getName(), request.getDescription());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectCloseRequest(Session session, ProjectCloseRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            projectCommandService.close(auth(attachment), request.getProjectId());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectReopenRequest(Session session, ProjectReopenRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            projectCommandService.reopen(auth(attachment), request.getProjectId());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectSwitchRequest(Session session, ProjectSwitchRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> projectCommandService.switchProject(auth(attachment), request.getProjectId()));
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
