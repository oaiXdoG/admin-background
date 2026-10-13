package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Project;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectManageResponse {

    private long projectId;
    private String appKey;
    private String name;
    private String description;
    private int status;
    private int memberCount;
    private long createdAt;
    private long updatedAt;

    public static ProjectManageResponse valueOf(Project project, int memberCount) {
        ProjectManageResponse response = new ProjectManageResponse();
        response.projectId = project.getId();
        response.appKey = project.getAppKey();
        response.name = project.getName();
        response.description = project.getDescription();
        response.status = project.getStatus();
        response.memberCount = memberCount;
        response.createdAt = project.getCreatedAt();
        response.updatedAt = project.getUpdatedAt();
        return response;
    }
}
