package com.oaixdog.adminbackground;

import com.oaixdog.adminbackground.config.ApplicationProperties;
import com.oaixdog.adminbackground.protocol.AccountCreateRequest;
import com.oaixdog.adminbackground.protocol.AccountListRequest;
import com.oaixdog.adminbackground.protocol.AccessMenuRequest;
import com.oaixdog.adminbackground.protocol.LoginRequest;
import com.oaixdog.adminbackground.protocol.MenuCreateRequest;
import com.oaixdog.adminbackground.protocol.MenuListRequest;
import com.oaixdog.adminbackground.protocol.MenuRemoveRequest;
import com.oaixdog.adminbackground.protocol.MenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectCreateRequest;
import com.oaixdog.adminbackground.protocol.ProjectUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectCloseRequest;
import com.oaixdog.adminbackground.protocol.ProjectListRequest;
import com.oaixdog.adminbackground.protocol.ProjectSwitchRequest;
import com.oaixdog.adminbackground.protocol.RoleCreateRequest;
import com.oaixdog.adminbackground.protocol.RoleListRequest;
import com.oaixdog.adminbackground.protocol.RoleMenuListRequest;
import com.oaixdog.adminbackground.protocol.RoleMenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.RoleRemoveRequest;
import com.oaixdog.adminbackground.protocol.RoleUpdateRequest;
import com.zfoo.net.core.HostAndPort;
import com.zfoo.net.core.http.HttpServer;
import com.zfoo.net.packet.DecodedPacketInfo;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.ThreadUtils;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class Application {
    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("config.xml");

        ApplicationProperties properties = context.getBean(ApplicationProperties.class);
        context.registerShutdownHook();

        Function<FullHttpRequest, DecodedPacketInfo> requestResolver = rawRequest -> {
            HttpAttachment attachment = HttpAttachment.valueOf(rawRequest, HttpResponseStatus.OK);
            QueryStringDecoder uri = new QueryStringDecoder(rawRequest.uri());
            String route = rawRequest.method().name() + " " + uri.path();

            Object request = switch (route) {
                case "POST /api/login" -> parseLoginRequest(rawRequest);
                case "POST /api/project/list" -> parseProjectListRequest(rawRequest);
                case "POST /api/project/create" -> parseRequest(rawRequest, ProjectCreateRequest.class);
                case "POST /api/project/update" -> parseRequest(rawRequest, ProjectUpdateRequest.class);
                case "POST /api/project/close" -> parseRequest(rawRequest, ProjectCloseRequest.class);
                case "POST /api/project/switch" -> parseRequest(rawRequest, ProjectSwitchRequest.class);
                case "POST /api/account/create" -> parseRequest(rawRequest, AccountCreateRequest.class);
                case "POST /api/account/list" -> parseRequest(rawRequest, AccountListRequest.class);
                case "POST /api/menu/list" -> parseRequest(rawRequest, MenuListRequest.class);
                case "POST /api/access/menu" -> parseRequest(rawRequest, AccessMenuRequest.class);
                case "POST /api/role/list" -> parseRequest(rawRequest, RoleListRequest.class);
                case "POST /api/role/create" -> parseRequest(rawRequest, RoleCreateRequest.class);
                case "POST /api/role/update" -> parseRequest(rawRequest, RoleUpdateRequest.class);
                case "POST /api/role/remove" -> parseRequest(rawRequest, RoleRemoveRequest.class);
                case "POST /api/role/menu/list" -> parseRequest(rawRequest, RoleMenuListRequest.class);
                case "POST /api/role/menu/update" -> parseRequest(rawRequest, RoleMenuUpdateRequest.class);
                case "POST /api/menu/create" -> parseRequest(rawRequest, MenuCreateRequest.class);
                case "POST /api/menu/update" -> parseRequest(rawRequest, MenuUpdateRequest.class);
                case "POST /api/menu/remove" -> parseRequest(rawRequest, MenuRemoveRequest.class);
                default -> new RuntimeException("Unknown route: " + route);
            };

            return DecodedPacketInfo.valueOf(request, attachment);
        };

        HttpServer server = new HttpServer(HostAndPort.valueOf(properties.getHost(), properties.getPort()), requestResolver);

        server.start();
        ThreadUtils.sleep(Long.MAX_VALUE);
    }

    private static LoginRequest parseLoginRequest(FullHttpRequest rawRequest) {
        return parseRequest(rawRequest, LoginRequest.class);
    }

    private static ProjectListRequest parseProjectListRequest(FullHttpRequest rawRequest) {
        return parseRequest(rawRequest, ProjectListRequest.class);
    }

    private static <T> T parseRequest(FullHttpRequest rawRequest, Class<T> requestClass) {
        try {
            return parseJsonBody(rawRequest, requestClass);
        } catch (RuntimeException exception) {
            try {
                return requestClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException reflectionException) {
                throw new IllegalStateException("无法创建请求对象: " + requestClass.getName(), reflectionException);
            }
        }
    }

    private static <T> T parseJsonBody(FullHttpRequest rawRequest, Class<T> requestClass) {
        String body = rawRequest.content().toString(StandardCharsets.UTF_8);
        return JsonUtils.string2Object(body, requestClass);
    }
}
