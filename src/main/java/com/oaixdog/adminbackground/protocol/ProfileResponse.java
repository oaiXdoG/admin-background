package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Account;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProfileResponse {

    private long id;
    private String account;
    private String username;
    private String avatar;
    private String remark;
    private Long lastLoginAt;
    private long createdAt;
    private boolean superAdmin;
    private List<String> platformRoles;
    private List<ProjectRole> projects;

    public static ProfileResponse valueOf(Account account, boolean superAdmin, List<String> platformRoles, List<ProjectRole> projects) {
        ProfileResponse response = new ProfileResponse();
        response.id = account.getId();
        response.account = account.getAccount();
        response.username = account.getUsername();
        response.avatar = account.getAvatar() == null ? "" : account.getAvatar();
        response.remark = account.getRemark();
        response.lastLoginAt = account.getLastLoginAt();
        response.createdAt = account.getCreatedAt() == null ? 0 : account.getCreatedAt();
        response.superAdmin = superAdmin;
        response.platformRoles = platformRoles;
        response.projects = projects;
        return response;
    }

    public record ProjectRole(long projectId, String projectName, String roleName) {
    }
}
