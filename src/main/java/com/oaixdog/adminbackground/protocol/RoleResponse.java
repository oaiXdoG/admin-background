package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleResponse {

    private long id;
    private String name;
    private String code;
    private String description;
    private int status;
    private boolean globalRole;
    private boolean builtIn;

    public static RoleResponse valueOf(Role role) {
        RoleResponse response = new RoleResponse();
        response.id = role.getId();
        response.name = role.getName();
        response.code = role.getCode();
        response.description = role.getDescription();
        response.status = role.getStatus() == null ? 1 : role.getStatus();
        response.globalRole = Boolean.TRUE.equals(role.getGlobalRole());
        response.builtIn = Boolean.TRUE.equals(role.getBuiltIn());
        return response;
    }
}
