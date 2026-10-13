package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleResponse {

    private long id;
    private int roleType;
    private Long projectId;
    private Long templateId;
    private String templateName;
    private String name;
    private String code;
    private String description;
    private int status;
    private boolean builtIn;
    private long createdAt;

    public static RoleResponse valueOf(Role role) {
        return valueOf(role, null);
    }

    public static RoleResponse valueOf(Role role, String templateName) {
        RoleResponse response = new RoleResponse();
        response.id = role.getId();
        response.roleType = role.getRoleType();
        response.projectId = role.getProjectId();
        response.templateId = role.getTemplateId();
        response.templateName = templateName;
        response.name = role.getName();
        response.code = role.getCode();
        response.description = role.getDescription();
        response.status = role.getStatus() == null ? 1 : role.getStatus();
        response.builtIn = Boolean.TRUE.equals(role.getBuiltIn());
        response.createdAt = role.getCreatedAt() == null ? 0 : role.getCreatedAt();
        return response;
    }
}
