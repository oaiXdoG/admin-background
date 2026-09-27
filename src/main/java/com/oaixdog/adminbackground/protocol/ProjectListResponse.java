package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProjectListResponse {

    private Long currentProjectId;
    private List<ProjectItem> projects;

    public static ProjectListResponse valueOf(Long currentProjectId, List<ProjectItem> projects) {
        ProjectListResponse response = new ProjectListResponse();
        response.currentProjectId = currentProjectId;
        response.projects = projects;
        return response;
    }

    @Getter
    @Setter
    public static class ProjectItem {

        private long projectId;
        private String name;
        private Long roleId;
        private String roleName;
    }
}
