package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.ProjectMemberAddRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberListRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberRemoveRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberUpdateRequest;
import com.oaixdog.adminbackground.service.ProjectMemberService;
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
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @PacketReceiver
    public void atProjectMemberListRequest(Session session, ProjectMemberListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> projectMemberService.list(auth(attachment), request.getProjectId()));
    }

    @PacketReceiver
    public void atProjectMemberAddRequest(Session session, ProjectMemberAddRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            projectMemberService.add(auth(attachment), request.getProjectId(), request.getAccount(), request.getRoleId());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectMemberUpdateRequest(Session session, ProjectMemberUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            projectMemberService.update(auth(attachment), request.getProjectId(), request.getAccountId(), request.getRoleId());
            return null;
        });
    }

    @PacketReceiver
    public void atProjectMemberRemoveRequest(Session session, ProjectMemberRemoveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            projectMemberService.remove(auth(attachment), request.getProjectId(), request.getAccountId());
            return null;
        });
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
