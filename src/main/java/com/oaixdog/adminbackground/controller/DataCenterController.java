package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.DataQueryRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateCreateRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateListRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateRemoveRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateUpdateRequest;
import com.oaixdog.adminbackground.service.DataQueryService;
import com.oaixdog.adminbackground.service.LogStorageService;
import com.oaixdog.adminbackground.service.OperationLogService;
import com.oaixdog.adminbackground.service.QueryTemplateService;
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
public class DataCenterController {

    private final QueryTemplateService queryTemplateService;
    private final DataQueryService dataQueryService;
    private final LogStorageService logStorageService;
    private final OperationLogService operationLogService;


    @PacketReceiver
    public void atQueryTemplateListRequest(Session session, QueryTemplateListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> Map.of(
                "list", queryTemplateService.list(auth(attachment), request.getProjectId(), request.getCategory()),
                "database", logStorageService.database()));
    }

    @PacketReceiver
    public void atQueryTemplateCreateRequest(Session session, QueryTemplateCreateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> queryTemplateService.create(auth(attachment), request.getProjectId(), request.getCategory(),
                request.getName(), request.getEvent(), request.getFields()));
    }

    @PacketReceiver
    public void atQueryTemplateUpdateRequest(Session session, QueryTemplateUpdateRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> queryTemplateService.update(auth(attachment), request.getProjectId(), request.getTemplateId(),
                request.getCategory(), request.getName(), request.getEvent(), request.getFields()));
    }

    @PacketReceiver
    public void atQueryTemplateRemoveRequest(Session session, QueryTemplateRemoveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            queryTemplateService.remove(auth(attachment), request.getProjectId(), request.getTemplateId());
            return null;
        });
    }

    @PacketReceiver
    public void atDataQueryRequest(Session session, DataQueryRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> dataQueryService.query(auth(attachment), request.getProjectId(), request.getTemplateId(),
                request.getStartTime(), request.getEndTime(), request.getRelation(), request.getConditions(), request.getPage(), request.getPageSize()));
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
