package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMemberResponse {

    private long accountId;
    private String account;
    private String username;
    private int status;
    private long roleId;
    private String roleName;
    private long createdAt;

    public static ProjectMemberResponse valueOf(ProjectUser member, Account account, Role role) {
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.accountId = account.getId();
        response.account = account.getAccount();
        response.username = account.getUsername();
        response.status = account.getStatus();
        response.roleId = member.getRoleId();
        response.roleName = role == null ? "" : role.getName();
        response.createdAt = member.getCreatedAt() == null ? 0 : member.getCreatedAt();
        return response;
    }
}
