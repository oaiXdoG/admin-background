package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.entity.ServerHost;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.AgentFileResultRequest;
import com.oaixdog.adminbackground.protocol.AgentHeartbeatRequest;
import com.oaixdog.adminbackground.protocol.AgentPackageRequest;
import com.oaixdog.adminbackground.protocol.AgentWaitRequest;
import com.oaixdog.adminbackground.protocol.AgentRegisterRequest;
import com.oaixdog.adminbackground.protocol.AgentResultRequest;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.service.AgentService;
import com.oaixdog.adminbackground.service.ServerDeployService;
import com.oaixdog.adminbackground.service.ServerFileService;
import com.zfoo.net.NetContext;
import com.zfoo.net.anno.PacketReceiver;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.net.session.Session;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 服务器 Agent 调用的接口：注册、心跳、指令结果。不需要登录，心跳和结果按服务器密钥验签 */
@Component
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final ServerDeployService deployService;
    private final ServerFileService fileService;

    @PacketReceiver
    public void atAgentRegisterRequest(Session session, AgentRegisterRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> agentService.register(request, LoginController.clientIp(session, attachment)));
    }

    @PacketReceiver
    public void atAgentHeartbeatRequest(Session session, AgentHeartbeatRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> agentService.heartbeat(authenticate(attachment), request, LoginController.clientIp(session, attachment)));
    }

    @PacketReceiver
    public void atAgentResultRequest(Session session, AgentResultRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            agentService.result(authenticate(attachment), request.getTaskId(), request.getSuccess(), request.getOutput());
            return null;
        });
    }

    /** 长轮询：不在这里立即回复，有指令或 25 秒超时后由 AgentWaitService 回复 */
    @PacketReceiver
    public void atAgentWaitRequest(Session session, AgentWaitRequest request, HttpAttachment attachment) {
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            agentService.await(authenticate(attachment), data -> {
                attachment.setHttpResponseStatus(HttpResponseStatus.OK);
                NetContext.getRouter().send(session, ApiResponse.success(data), attachment);
            });
        } catch (BusinessException exception) {
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
        }
    }

    @PacketReceiver
    public void atAgentPackageRequest(Session session, AgentPackageRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> deployService.chunk(authenticate(attachment), request.getTaskId(), request.getIndex()));
    }

    @PacketReceiver
    public void atAgentFileResultRequest(Session session, AgentFileResultRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            fileService.agentResult(authenticate(attachment), request.getOpId(), request.getSuccess(), request.getMessage(), request.getData());
            return null;
        });
    }

    private ServerHost authenticate(HttpAttachment attachment) {
        HttpHeaders headers = attachment.getFullHttpRequest().headers();
        return agentService.authenticate(headers.get("X-Agent-Server"), headers.get("X-Agent-Timestamp"), headers.get("X-Agent-Signature"),
                headers.get(AgentService.BODY_DIGEST_HEADER));
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

    private interface Handler {
        Object run();
    }
}
