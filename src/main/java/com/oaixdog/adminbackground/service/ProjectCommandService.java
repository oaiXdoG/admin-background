package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.protocol.ProjectCreateResponse;
import com.oaixdog.adminbackground.protocol.ProjectSwitchResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCommandService {

    private static final int PROJECT_NAME_MAX_LENGTH = 64;
    private static final int APP_KEY_MAX_LENGTH = 64;

    private final PermissionService permissionService;
    private final AccountMapper accountMapper;
    private final ProjectMapper projectMapper;
    private final ProjectUserMapper projectUserMapper;

    @Transactional
    public ProjectCreateResponse create(String authorization, String appKey, String name) {
        Account account = permissionService.requireAccount(authorization);
        permissionService.requireMenu(authorization, "manage_project");
        String projectName = name == null ? "" : name.trim();
        String projectAppKey = appKey == null ? "" : appKey.trim();
        validate(projectAppKey, projectName);
        if (projectMapper.selectCount(Wrappers.<Project>lambdaQuery().eq(Project::getAppKey, projectAppKey)) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_EXISTS);
        }
        Project project = new Project();
        project.setAppKey(projectAppKey);
        project.setName(projectName);
        project.setCreatedAt(System.currentTimeMillis());
        project.setClosed(false);
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_EXISTS);
        }
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getId, account.getId())
                .set(Account::getLastProjectId, project.getId()));
        return ProjectCreateResponse.valueOf(project, null, null);
    }

    @Transactional
    public void update(String authorization, Long projectId, String appKey, String name) {
        permissionService.requireMenu(authorization, "manage_project");
        requireOpenProject(projectId);
        String projectName = name == null ? "" : name.trim();
        String projectAppKey = appKey == null ? "" : appKey.trim();
        validate(projectAppKey, projectName);
        if (projectMapper.selectCount(Wrappers.<Project>lambdaQuery().eq(Project::getAppKey, projectAppKey).ne(Project::getId, projectId)) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_EXISTS);
        }
        try {
            int updated = projectMapper.update(null, Wrappers.<Project>lambdaUpdate().eq(Project::getId, projectId).eq(Project::getClosed, false)
                    .set(Project::getAppKey, projectAppKey).set(Project::getName, projectName));
            if (updated == 0) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROJECT_APP_KEY_EXISTS);
        }
    }

    @Transactional
    public void close(String authorization, Long projectId) {
        permissionService.requireMenu(authorization, "manage_project");
        requireOpenProject(projectId);
        projectMapper.update(null, Wrappers.<Project>lambdaUpdate().eq(Project::getId, projectId).set(Project::getClosed, true));
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getLastProjectId, projectId).set(Account::getLastProjectId, null));
    }

    @Transactional
    public ProjectSwitchResponse switchProject(String authorization, Long projectId) {
        Account account = permissionService.requireAccount(authorization);
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || Boolean.TRUE.equals(project.getClosed())) {
            throw new BusinessException(ErrorCode.PROJECT_SWITCH_FORBIDDEN);
        }
        boolean global = permissionService.hasGlobalRole(account);
        Long membershipId = projectUserMapper.selectCount(Wrappers.<ProjectUser>lambdaQuery()
                .eq(ProjectUser::getAccountId, account.getId())
                .eq(ProjectUser::getProjectId, projectId));
        if (!global && (membershipId == null || membershipId == 0)) {
            throw new BusinessException(ErrorCode.PROJECT_SWITCH_FORBIDDEN);
        }
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getId, account.getId())
                .set(Account::getLastProjectId, projectId));
        return ProjectSwitchResponse.valueOf(projectId);
    }

    private void requireOpenProject(Long projectId) {
        if (projectId == null || projectId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        Project project = projectMapper.selectById(projectId);
        if (project == null || Boolean.TRUE.equals(project.getClosed())) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
    }

    private void validate(String appKey, String name) {
        if (StringUtils.isBlank(name) || name.length() > PROJECT_NAME_MAX_LENGTH
                || StringUtils.isBlank(appKey) || appKey.length() > APP_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

}
