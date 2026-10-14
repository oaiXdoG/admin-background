package com.oaixdog.adminbackground;

import com.oaixdog.adminbackground.config.ApplicationProperties;
import com.oaixdog.adminbackground.protocol.AccessMenuRequest;
import com.oaixdog.adminbackground.protocol.AccountCreateRequest;
import com.oaixdog.adminbackground.protocol.AccountListRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectListRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectOptionsRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectRolesRequest;
import com.oaixdog.adminbackground.protocol.AccountProjectUpdateRequest;
import com.oaixdog.adminbackground.protocol.AccountRoleOptionsRequest;
import com.oaixdog.adminbackground.protocol.AccountRoleUpdateRequest;
import com.oaixdog.adminbackground.protocol.AccountStatusRequest;
import com.oaixdog.adminbackground.protocol.AccountUpdateRequest;
import com.oaixdog.adminbackground.protocol.BackupDownloadRequest;
import com.oaixdog.adminbackground.protocol.BackupGetRequest;
import com.oaixdog.adminbackground.protocol.BackupRemoveRequest;
import com.oaixdog.adminbackground.protocol.BackupRunRequest;
import com.oaixdog.adminbackground.protocol.BackupUpdateRequest;
import com.oaixdog.adminbackground.protocol.DataQueryRequest;
import com.oaixdog.adminbackground.protocol.LogStorageGetRequest;
import com.oaixdog.adminbackground.protocol.LogStorageRetentionRequest;
import com.oaixdog.adminbackground.protocol.LogStorageTestRequest;
import com.oaixdog.adminbackground.protocol.LogStorageUpdateRequest;
import com.oaixdog.adminbackground.protocol.LoginRequest;
import com.oaixdog.adminbackground.protocol.LogoutRequest;
import com.oaixdog.adminbackground.protocol.MenuCreateRequest;
import com.oaixdog.adminbackground.protocol.MenuListRequest;
import com.oaixdog.adminbackground.protocol.MenuRemoveRequest;
import com.oaixdog.adminbackground.protocol.MenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.OperationLogListRequest;
import com.oaixdog.adminbackground.protocol.ProfileGetRequest;
import com.oaixdog.adminbackground.protocol.ProfilePasswordRequest;
import com.oaixdog.adminbackground.protocol.ProfileUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectCloseRequest;
import com.oaixdog.adminbackground.protocol.ProjectCreateRequest;
import com.oaixdog.adminbackground.protocol.ProjectListRequest;
import com.oaixdog.adminbackground.protocol.ProjectManageListRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberAddRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberListRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberRemoveRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberRolesRequest;
import com.oaixdog.adminbackground.protocol.ProjectMemberUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectReopenRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleCreateRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleImportRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleListRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleMenuListRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleMenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleRemoveRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleTemplatesRequest;
import com.oaixdog.adminbackground.protocol.ProjectRoleUpdateRequest;
import com.oaixdog.adminbackground.protocol.ProjectSwitchRequest;
import com.oaixdog.adminbackground.protocol.ProjectTemplateOptionsRequest;
import com.oaixdog.adminbackground.protocol.ProjectUpdateRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateCreateRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateListRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateRemoveRequest;
import com.oaixdog.adminbackground.protocol.QueryTemplateUpdateRequest;
import com.oaixdog.adminbackground.protocol.RoleCreateRequest;
import com.oaixdog.adminbackground.protocol.RoleListRequest;
import com.oaixdog.adminbackground.protocol.RoleMenuListRequest;
import com.oaixdog.adminbackground.protocol.RoleMenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.RoleRemoveRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateCreateRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateListRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateMenuListRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateMenuUpdateRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateRemoveRequest;
import com.oaixdog.adminbackground.protocol.RoleTemplateUpdateRequest;
import com.oaixdog.adminbackground.protocol.RoleUpdateRequest;
import com.oaixdog.adminbackground.protocol.SystemSettingGetRequest;
import com.oaixdog.adminbackground.protocol.SystemSettingUpdateRequest;
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
                case "POST /api/account/update" -> parseRequest(rawRequest, AccountUpdateRequest.class);
                case "POST /api/account/project/list" -> parseRequest(rawRequest, AccountProjectListRequest.class);
                case "POST /api/account/project/update" -> parseRequest(rawRequest, AccountProjectUpdateRequest.class);
                case "POST /api/logout" -> parseRequest(rawRequest, LogoutRequest.class);
                case "POST /api/role-template/list" -> parseRequest(rawRequest, RoleTemplateListRequest.class);
                case "POST /api/role-template/create" -> parseRequest(rawRequest, RoleTemplateCreateRequest.class);
                case "POST /api/role-template/update" -> parseRequest(rawRequest, RoleTemplateUpdateRequest.class);
                case "POST /api/role-template/remove" -> parseRequest(rawRequest, RoleTemplateRemoveRequest.class);
                case "POST /api/role-template/menu/list" -> parseRequest(rawRequest, RoleTemplateMenuListRequest.class);
                case "POST /api/role-template/menu/update" -> parseRequest(rawRequest, RoleTemplateMenuUpdateRequest.class);
                case "POST /api/project/role/list" -> parseRequest(rawRequest, ProjectRoleListRequest.class);
                case "POST /api/project/role/create" -> parseRequest(rawRequest, ProjectRoleCreateRequest.class);
                case "POST /api/project/role/update" -> parseRequest(rawRequest, ProjectRoleUpdateRequest.class);
                case "POST /api/project/role/remove" -> parseRequest(rawRequest, ProjectRoleRemoveRequest.class);
                case "POST /api/project/role/menu/list" -> parseRequest(rawRequest, ProjectRoleMenuListRequest.class);
                case "POST /api/project/role/menu/update" -> parseRequest(rawRequest, ProjectRoleMenuUpdateRequest.class);
                case "POST /api/project/role/import" -> parseRequest(rawRequest, ProjectRoleImportRequest.class);
                case "POST /api/project/member/list" -> parseRequest(rawRequest, ProjectMemberListRequest.class);
                case "POST /api/project/member/add" -> parseRequest(rawRequest, ProjectMemberAddRequest.class);
                case "POST /api/project/member/update" -> parseRequest(rawRequest, ProjectMemberUpdateRequest.class);
                case "POST /api/project/member/remove" -> parseRequest(rawRequest, ProjectMemberRemoveRequest.class);
                case "POST /api/project/manage/list" -> parseRequest(rawRequest, ProjectManageListRequest.class);
                case "POST /api/project/reopen" -> parseRequest(rawRequest, ProjectReopenRequest.class);
                case "POST /api/account/status" -> parseRequest(rawRequest, AccountStatusRequest.class);
                case "POST /api/account/role/update" -> parseRequest(rawRequest, AccountRoleUpdateRequest.class);
                case "POST /api/account/project/roles" -> parseRequest(rawRequest, AccountProjectRolesRequest.class);
                case "POST /api/account/project/options" -> parseRequest(rawRequest, AccountProjectOptionsRequest.class);
                case "POST /api/profile/get" -> parseRequest(rawRequest, ProfileGetRequest.class);
                case "POST /api/profile/update" -> parseRequest(rawRequest, ProfileUpdateRequest.class);
                case "POST /api/profile/password" -> parseRequest(rawRequest, ProfilePasswordRequest.class);
                case "POST /api/system/setting/get" -> parseRequest(rawRequest, SystemSettingGetRequest.class);
                case "POST /api/system/setting/update" -> parseRequest(rawRequest, SystemSettingUpdateRequest.class);
                case "POST /api/project/role/templates" -> parseRequest(rawRequest, ProjectRoleTemplatesRequest.class);
                case "POST /api/project/templates" -> parseRequest(rawRequest, ProjectTemplateOptionsRequest.class);
                case "POST /api/account/role/options" -> parseRequest(rawRequest, AccountRoleOptionsRequest.class);
                case "POST /api/project/member/roles" -> parseRequest(rawRequest, ProjectMemberRolesRequest.class);
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
                case "POST /api/system/log-storage/get" -> parseRequest(rawRequest, LogStorageGetRequest.class);
                case "POST /api/system/log-storage/update" -> parseRequest(rawRequest, LogStorageUpdateRequest.class);
                case "POST /api/system/log-storage/test" -> parseRequest(rawRequest, LogStorageTestRequest.class);
                case "POST /api/system/log-storage/retention" -> parseRequest(rawRequest, LogStorageRetentionRequest.class);
                case "POST /api/data/template/list" -> parseRequest(rawRequest, QueryTemplateListRequest.class);
                case "POST /api/data/template/create" -> parseRequest(rawRequest, QueryTemplateCreateRequest.class);
                case "POST /api/data/template/update" -> parseRequest(rawRequest, QueryTemplateUpdateRequest.class);
                case "POST /api/data/template/remove" -> parseRequest(rawRequest, QueryTemplateRemoveRequest.class);
                case "POST /api/data/query" -> parseRequest(rawRequest, DataQueryRequest.class);
                case "POST /api/system/operation-log/list" -> parseRequest(rawRequest, OperationLogListRequest.class);
                case "POST /api/system/backup/get" -> parseRequest(rawRequest, BackupGetRequest.class);
                case "POST /api/system/backup/update" -> parseRequest(rawRequest, BackupUpdateRequest.class);
                case "POST /api/system/backup/run" -> parseRequest(rawRequest, BackupRunRequest.class);
                case "POST /api/system/backup/download" -> parseRequest(rawRequest, BackupDownloadRequest.class);
                case "POST /api/system/backup/remove" -> parseRequest(rawRequest, BackupRemoveRequest.class);
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
