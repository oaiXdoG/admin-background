package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.ProjectCreateRequest;
import com.oaixdog.adminbackground.protocol.ProjectUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectCloseRequest;
import com.oaixdog.adminbackground.protocol.ProjectCreateResponse;
import com.oaixdog.adminbackground.protocol.ProjectSwitchRequest;
import com.oaixdog.adminbackground.protocol.ProjectSwitchResponse;
import com.oaixdog.adminbackground.service.ProjectCommandService;
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

@Component
@RequiredArgsConstructor
public class ProjectCommandController {

    private static final Logger log = LoggerFactory.getLogger(ProjectCommandController.class);

    private final ProjectCommandService projectCommandService;

    @PacketReceiver
    public void atProjectCreateRequest(Session session, ProjectCreateRequest request, HttpAttachment attachment) {
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            ProjectCreateResponse data = projectCommandService.create(authorization(attachment), request.getAppKey(), request.getName());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
        } catch (BusinessException exception) {
            log.warn("创建项目业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atProjectUpdateRequest(Session session, ProjectUpdateRequest request, HttpAttachment attachment) {
        try {
            if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            projectCommandService.update(authorization(attachment), request.getProjectId(), request.getAppKey(), request.getName());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(null), attachment);
        } catch (BusinessException exception) {
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atProjectCloseRequest(Session session, ProjectCloseRequest request, HttpAttachment attachment) {
        try {
            if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            projectCommandService.close(authorization(attachment), request.getProjectId());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(null), attachment);
        } catch (BusinessException exception) {
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atProjectSwitchRequest(Session session, ProjectSwitchRequest request, HttpAttachment attachment) {
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            ProjectSwitchResponse data = projectCommandService.switchProject(authorization(attachment), request.getProjectId());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
        } catch (BusinessException exception) {
            log.warn("切换项目业务失败: {}", exception.getMessage());
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    private static String authorization(HttpAttachment attachment) {
        return attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
    }
}
