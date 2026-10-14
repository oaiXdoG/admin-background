package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.protocol.ProjectCreateResponse;
import com.oaixdog.adminbackground.protocol.ProjectManageResponse;
import com.oaixdog.adminbackground.protocol.ProjectSwitchResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectCommandService {

    private static final String MENU = "manage_project";
    private static final int PROJECT_NAME_MAX_LENGTH = 64;
    private static final int APP_KEY_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MAX_LENGTH = 128;

    private final PermissionService permissionService;
    private final LoginService loginService;
    private final RoleService roleService;
    private final AccountMapper accountMapper;
    private final ProjectMapper projectMapper;
    private final ProjectUserMapper projectUserMapper;

    /** 项目管理列表：包含已关闭项目和成员数 */
    public List<ProjectManageResponse> manageList(String authorization) {
        permissionService.requirePlatformMenu(authorization, MENU);
        Map<Long, Long> memberCounts = projectUserMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(ProjectUser::getProjectId, Collectors.counting()));
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery().orderByAsc(Project::getId)).stream()
                .map(project -> ProjectManageResponse.valueOf(project, memberCounts.getOrDefault(project.getId(), 0L).intValue()))
                .toList();
    }

    /** 新建项目：生成「项目管理员」并复制勾选的模板，创建者的会话切到新项目 */
    @Transactional
    public ProjectCreateResponse create(String authorization, String appKey, String name, String description, List<Long> templateIds) {
        Account account = permissionService.requirePlatformMenu(authorization, MENU + ":create");
        ProjectValues values = validate(appKey, name, description);
        if (projectMapper.selectCount(Wrappers.<Project>lambdaQuery().eq(Project::getAppKey, values.appKey())) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_EXISTS);
        }
        long now = System.currentTimeMillis();
        Project project = new Project();
        project.setAppKey(values.appKey());
        project.setName(values.name());
        project.setDescription(values.description());
        project.setStatus(Status.ENABLED);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_EXISTS);
        }
        roleService.initializeProject(project.getId(), templateIds);
        if (permissionService.canAccessProject(account, project.getId())) {
            loginService.setSessionProjectId(authorization, project.getId());
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getId, account.getId())
                    .set(Account::getLastProjectId, project.getId()));
        }
        return ProjectCreateResponse.valueOf(project, null, null);
    }

    /** App Key 是日志按项目隔离的依据，创建后不可修改：请求可以不带，带了必须与原值一致 */
    public void update(String authorization, Long projectId, String appKey, String name, String description) {
        permissionService.requirePlatformMenu(authorization, MENU + ":update");
        Project project = requireProject(projectId);
        if (StringUtils.isNotBlank(appKey) && !appKey.trim().equals(project.getAppKey())) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_IMMUTABLE);
        }
        ProjectValues values = validate(project.getAppKey(), name, description);
        projectMapper.update(null, Wrappers.<Project>lambdaUpdate().eq(Project::getId, projectId)
                .set(Project::getName, values.name()).set(Project::getDescription, values.description())
                .set(Project::getUpdatedAt, System.currentTimeMillis()));
    }

    /** 关闭后成员关系和角色保留，重新启用即恢复 */
    @Transactional
    public void close(String authorization, Long projectId) {
        permissionService.requirePlatformMenu(authorization, MENU + ":status");
        Project project = requireProject(projectId);
        if (!Objects.equals(project.getStatus(), Status.ENABLED)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        setStatus(projectId, Status.DISABLED);
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getLastProjectId, projectId).set(Account::getLastProjectId, null));
    }

    public void reopen(String authorization, Long projectId) {
        permissionService.requirePlatformMenu(authorization, MENU + ":status");
        requireProject(projectId);
        setStatus(projectId, Status.ENABLED);
    }

    public ProjectSwitchResponse switchProject(String authorization, Long projectId) {
        Account account = permissionService.requireAccount(authorization);
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!permissionService.canAccessProject(account, projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_SWITCH_FORBIDDEN);
        }
        loginService.setSessionProjectId(authorization, projectId);
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getId, account.getId()).set(Account::getLastProjectId, projectId));
        return ProjectSwitchResponse.valueOf(projectId);
    }

    private void setStatus(Long projectId, int status) {
        projectMapper.update(null, Wrappers.<Project>lambdaUpdate().eq(Project::getId, projectId)
                .set(Project::getStatus, status).set(Project::getUpdatedAt, System.currentTimeMillis()));
    }

    private Project requireProject(Long projectId) {
        Project project = projectId == null || projectId <= 0 ? null : projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private ProjectValues validate(String appKey, String name, String description) {
        String projectName = name == null ? "" : name.trim();
        String projectAppKey = appKey == null ? "" : appKey.trim();
        String projectDescription = description == null ? "" : description.trim();
        if (StringUtils.isBlank(projectName) || projectName.length() > PROJECT_NAME_MAX_LENGTH
                || StringUtils.isBlank(projectAppKey) || projectAppKey.length() > APP_KEY_MAX_LENGTH
                || projectDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new ProjectValues(projectAppKey, projectName, projectDescription);
    }

    private record ProjectValues(String appKey, String name, String description) {
    }
}
