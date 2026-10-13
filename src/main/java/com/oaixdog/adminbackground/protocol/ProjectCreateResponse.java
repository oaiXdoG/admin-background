package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Project;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectCreateResponse {

    private long projectId;
    private String appKey;
    private String name;
    private String description;
    private long createdAt;
    private Long roleId;
    private String roleName;
    private long currentProjectId;

    public static ProjectCreateResponse valueOf(Project project, Long roleId, String roleName) {
        ProjectCreateResponse response = new ProjectCreateResponse();
        response.projectId = project.getId();
        response.appKey = project.getAppKey();
        response.name = project.getName();
        response.description = project.getDescription();
        response.createdAt = project.getCreatedAt();
        response.roleId = roleId;
        response.roleName = roleName;
        response.currentProjectId = project.getId();
        return response;
    }
}
