package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectSwitchResponse {

    private long currentProjectId;

    public static ProjectSwitchResponse valueOf(long currentProjectId) {
        ProjectSwitchResponse response = new ProjectSwitchResponse();
        response.currentProjectId = currentProjectId;
        return response;
    }
}
