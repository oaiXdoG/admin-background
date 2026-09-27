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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCommandService {

    private static final int PROJECT_NAME_MAX_LENGTH = 64;

    private final PermissionService permissionService;
    private final AccountMapper accountMapper;
    private final ProjectMapper projectMapper;
    private final ProjectUserMapper projectUserMapper;

    @Transactional
    public ProjectCreateResponse create(String authorization, String name) {
        Account account = permissionService.requireAccount(authorization);
        permissionService.requireMenu(authorization, "manage_project");
        String projectName = name == null ? "" : name.trim();
        if (StringUtils.isBlank(projectName) || projectName.length() > PROJECT_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Project project = new Project();
        project.setName(projectName);
        projectMapper.insert(project);
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getId, account.getId())
                .set(Account::getLastProjectId, project.getId()));
        return ProjectCreateResponse.valueOf(project.getId(), project.getName(), null, null);
    }

    @Transactional
    public ProjectSwitchResponse switchProject(String authorization, Long projectId) {
        Account account = permissionService.requireAccount(authorization);
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
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

}
