package com.oaixdog.adminbackground.controller;

import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.ApiResponse;
import com.oaixdog.adminbackground.protocol.LogEventDictListRequest;
import com.oaixdog.adminbackground.protocol.LogEventFieldsRequest;
import com.oaixdog.adminbackground.protocol.LogEventListRequest;
import com.oaixdog.adminbackground.protocol.LogEventRemoveRequest;
import com.oaixdog.adminbackground.protocol.LogEventSaveRequest;
import com.oaixdog.adminbackground.protocol.LogExportRequest;
import com.oaixdog.adminbackground.protocol.LogQueryRequest;
import com.oaixdog.adminbackground.protocol.LogSourceGetRequest;
import com.oaixdog.adminbackground.protocol.LogSourceSaveRequest;
import com.oaixdog.adminbackground.protocol.LogSourceTestRequest;
import com.oaixdog.adminbackground.service.LogEventService;
import com.oaixdog.adminbackground.service.LogQueryService;
import com.oaixdog.adminbackground.service.LogSourceService;
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

/** 日志中心：日志源、事件字典、日志查询 */
@Component
@RequiredArgsConstructor
public class LogCenterController {

    private final LogSourceService logSourceService;
    private final LogEventService logEventService;
    private final LogQueryService logQueryService;
    private final OperationLogService operationLogService;

    @PacketReceiver
    public void atLogSourceGetRequest(Session session, LogSourceGetRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logSourceService.get(auth(attachment), request.getProjectId()));
    }

    @PacketReceiver
    public void atLogSourceSaveRequest(Session session, LogSourceSaveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logSourceService.save(auth(attachment), request.getProjectId(), request.getUrl(),
                request.getDatabase(), request.getReadUser(), request.getReadPassword()));
    }

    @PacketReceiver
    public void atLogSourceTestRequest(Session session, LogSourceTestRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logSourceService.test(auth(attachment), request.getProjectId(), request.getUrl(),
                request.getDatabase(), request.getReadUser(), request.getReadPassword()));
    }

    @PacketReceiver
    public void atLogEventDictListRequest(Session session, LogEventDictListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> Map.of("list", logEventService.list(auth(attachment), request.getProjectId())));
    }

    @PacketReceiver
    public void atLogEventSaveRequest(Session session, LogEventSaveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logEventService.save(auth(attachment), request.getProjectId(), request.getEvent(),
                request.getLabel(), request.getDescription(), request.getFields()));
    }

    @PacketReceiver
    public void atLogEventRemoveRequest(Session session, LogEventRemoveRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> {
            logEventService.remove(auth(attachment), request.getProjectId(), request.getEvent());
            return null;
        });
    }

    @PacketReceiver
    public void atLogQueryRequest(Session session, LogQueryRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logQueryService.queryLogs(auth(attachment), request.getProjectId(),
                new LogQueryService.LogFilter(request.getStartTime(), request.getEndTime(), request.getEvents(),
                        request.getActor(), request.getServer(), request.getKeyword(), request.getRelation(), request.getConditions()),
                request.getPage(), request.getPageSize()));
    }

    @PacketReceiver
    public void atLogExportRequest(Session session, LogExportRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logQueryService.exportLogs(auth(attachment), request.getProjectId(),
                new LogQueryService.LogFilter(request.getStartTime(), request.getEndTime(), request.getEvents(),
                        request.getActor(), request.getServer(), request.getKeyword(), request.getRelation(), request.getConditions())));
    }

    @PacketReceiver
    public void atLogEventListRequest(Session session, LogEventListRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> Map.of("list", logQueryService.listEvents(auth(attachment), request.getProjectId(),
                request.getDays())));
    }

    @PacketReceiver
    public void atLogEventFieldsRequest(Session session, LogEventFieldsRequest request, HttpAttachment attachment) {
        handle(session, attachment, request, () -> logQueryService.eventFields(auth(attachment), request.getProjectId(),
                request.getEvent(), request.getDays()));
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
