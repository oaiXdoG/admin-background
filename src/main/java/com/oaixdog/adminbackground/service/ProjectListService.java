package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.AccountProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.protocol.ProjectListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 项目切换器的数据：超级管理员看到全部正常项目，其他账号看到自己加入的正常项目。
 * 当前项目依次取会话项目、上次使用的项目、列表第一个，并写回会话。
 */
@Service
@RequiredArgsConstructor
public class ProjectListService {

    private final LoginService loginService;
    private final AccountMapper accountMapper;
    private final AccountProjectMapper accountProjectMapper;
    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;

    public ProjectListResponse list(String authorization) {
        Account account = permissionService.requireAccount(authorization);
        List<ProjectListResponse.ProjectItem> projects = new ArrayList<>();
        if (permissionService.isSuperAdmin(account)) {
            projectMapper.selectList(Wrappers.<Project>lambdaQuery().eq(Project::getStatus, Status.ENABLED).orderByAsc(Project::getId))
                    .forEach(project -> projects.add(item(project.getId(), project.getAppKey(), project.getName(), project.getCreatedAt(), null, "超级管理员")));
        } else {
            accountProjectMapper.listByAccountId(account.getId()).forEach(row ->
                    projects.add(item(row.getProjectId(), row.getAppKey(), row.getName(), row.getCreatedAt(), row.getRoleId(), row.getRoleName())));
        }
        List<Long> projectIds = projects.stream().map(ProjectListResponse.ProjectItem::getProjectId).toList();
        Long sessionProjectId = loginService.findSessionProjectId(authorization);
        Long currentProjectId = projectIds.contains(sessionProjectId) ? sessionProjectId
                : projectIds.contains(account.getLastProjectId()) ? account.getLastProjectId()
                : projectIds.isEmpty() ? null : projectIds.get(0);
        if (!Objects.equals(sessionProjectId, currentProjectId)) {
            loginService.setSessionProjectId(authorization, currentProjectId);
        }
        if (currentProjectId != null && !Objects.equals(account.getLastProjectId(), currentProjectId)) {
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getId, account.getId())
                    .set(Account::getLastProjectId, currentProjectId));
        }
        return ProjectListResponse.valueOf(currentProjectId, projects);
    }

    private static ProjectListResponse.ProjectItem item(Long projectId, String appKey, String name, Long createdAt, Long roleId, String roleName) {
        ProjectListResponse.ProjectItem item = new ProjectListResponse.ProjectItem();
        item.setProjectId(projectId);
        item.setAppKey(appKey);
        item.setName(name);
        item.setCreatedAt(createdAt);
        item.setRoleId(roleId);
        item.setRoleName(roleName);
        return item;
    }
}
