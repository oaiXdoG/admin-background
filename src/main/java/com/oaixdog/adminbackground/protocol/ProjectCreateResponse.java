package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectCreateResponse {

    private long projectId;
    private String name;
    private Long roleId;
    private String roleName;
    private long currentProjectId;

    public static ProjectCreateResponse valueOf(long projectId, String name, Long roleId, String roleName) {
        ProjectCreateResponse response = new ProjectCreateResponse();
        response.projectId = projectId;
        response.name = name;
        response.roleId = roleId;
        response.roleName = roleName;
        response.currentProjectId = projectId;
        return response;
    }
}
