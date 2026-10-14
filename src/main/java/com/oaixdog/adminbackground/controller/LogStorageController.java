package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.LogStorageGetRequest;
import com.oaixdog.adminbackground.protocol.LogStorageRetentionRequest;
import com.oaixdog.adminbackground.protocol.LogStorageTestRequest;
import com.oaixdog.adminbackground.protocol.LogStorageUpdateRequest;
import com.oaixdog.adminbackground.service.LogStorageService;
import com.oaixdog.adminbackground.service.OperationLogService;
import com.zfoo.net.NetContext;
import com.zfoo.net.anno.PacketReceiver;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.net.session.Session;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogStorageController {

    private final LogStorageService logStorageService;
    private final OperationLogService operationLogService;


    @PacketReceiver
    public void atLogStorageGetRequest(Session session, LogStorageGetRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logStorageService.get(auth(attachment)));
    }

    @PacketReceiver
    public void atLogStorageUpdateRequest(Session session, LogStorageUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logStorageService.update(auth(attachment), request.getUrl(), request.getDatabase(),
                request.getReadUser(), request.getReadPassword(), request.getAdminUser(), request.getAdminPassword()));
    }

    @PacketReceiver
    public void atLogStorageTestRequest(Session session, LogStorageTestRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logStorageService.test(auth(attachment), request.getUrl(), request.getDatabase(),
                request.getReadUser(), request.getReadPassword(), request.getAdminUser(), request.getAdminPassword()));
    }

    @PacketReceiver
    public void atLogStorageRetentionRequest(Session session, LogStorageRetentionRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> Map.of("retentionDays", logStorageService.updateRetention(auth(attachment), request.getRetentionDays())));
    }

    private void handle(Session session, HttpAttachment attachment, Object request, Handler handler) {
        OperationLogService.Operation operation = operationLogService.start(attachment);
        try {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.success(handler.run()), attachment);
            operationLogService.finish(operation, request, 0);
        } catch (BusinessException exception) {
            attachment.setHttpResponseStatus(HttpResponseStatus.OK);
            NetContext.getRouter().send(session, ApiResponse.businessError(exception.getCode(), exception.getMessage()), attachment);
            operationLogService.finish(operation, request, exception.getCode());
        }
    }

    private static String auth(HttpAttachment attachment) {
        return attachment.getFullHttpRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
    }

    private interface Handler {
        Object run();
    }
}
