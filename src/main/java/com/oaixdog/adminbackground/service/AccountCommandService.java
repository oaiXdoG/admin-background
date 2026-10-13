package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.protocol.AccountCreateResponse;
import com.oaixdog.adminbackground.protocol.AccountMembership;
import com.oaixdog.adminbackground.protocol.AccountResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountCommandService {

    private static final int ACCOUNT_MAX_LENGTH = 64;
    private static final int PASSWORD_MAX_LENGTH = 128;
    private static final int USERNAME_MAX_LENGTH = 64;

    private static final int ENABLED = 1;

    private final AccountMapper accountMapper;
    private final ProjectMapper projectMapper;
    private final ProjectUserMapper projectUserMapper;
    private final RoleMapper roleMapper;

    public AccountCreateResponse create(String authorization, String account, String password, String username) {
        permissionService.requireMenu(authorization, "manage_user");
        String loginAccount = account == null ? "" : account.trim();
        String displayName = username == null ? "" : username.trim();
        if (StringUtils.isBlank(loginAccount) || loginAccount.length() > ACCOUNT_MAX_LENGTH
                || StringUtils.isBlank(password) || password.length() > PASSWORD_MAX_LENGTH
                || StringUtils.isBlank(displayName) || displayName.length() > USERNAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long existing = accountMapper.selectCount(Wrappers.<Account>lambdaQuery().eq(Account::getAccount, loginAccount));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXISTS);
        }
        Account created = new Account();
        created.setAccount(loginAccount);
        created.setPassword(password);
        created.setUsername(displayName);
        created.setAdmin(false);
        accountMapper.insert(created);
        return AccountCreateResponse.valueOf(created.getId(), created.getAccount(), created.getUsername());
    }

    public List<AccountResponse> list(String authorization) {
        permissionService.requireMenu(authorization, "manage_user");
        return accountMapper.selectList(null).stream().map(AccountResponse::valueOf).toList();
    }

    public void update(String authorization, Long accountId, String username, String password) {
        permissionService.requireMenu(authorization, "manage_user");
        requireAccount(accountId);
        String displayName = username == null ? "" : username.trim();
        if (StringUtils.isBlank(displayName) || displayName.length() > USERNAME_MAX_LENGTH
                || (password != null && (StringUtils.isBlank(password) || password.length() > PASSWORD_MAX_LENGTH))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Account updated = new Account();
        updated.setId(accountId);
        updated.setUsername(displayName);
        if (password != null) {
            updated.setPassword(password);
        }
        accountMapper.updateById(updated);
    }

    public List<AccountMembership> listProjects(String authorization, Long accountId) {
        permissionService.requireMenu(authorization, "manage_user");
        requireAccount(accountId);
        Set<Long> openProjectIds = openProjectIds();
        return projectUserMapper.selectList(Wrappers.<ProjectUser>lambdaQuery().eq(ProjectUser::getAccountId, accountId)
                        .orderByAsc(ProjectUser::getProjectId)).stream()
                .filter(row -> openProjectIds.contains(row.getProjectId()))
                .map(row -> AccountMembership.valueOf(row.getProjectId(), row.getRoleId()))
                .toList();
    }

    @Transactional
    public void updateProjects(String authorization, Long accountId, List<AccountMembership> memberships) {
        permissionService.requireMenu(authorization, "manage_user");
        requireAccount(accountId);
        if (memberships == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<Long> openProjectIds = openProjectIds();
        Map<Long, Role> roles = roleMapper.selectList(null).stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        Set<Long> projectIds = new HashSet<>();
        for (AccountMembership membership : memberships) {
            Role role = membership == null ? null : roles.get(membership.getRoleId());
            boolean valid = membership != null && membership.getProjectId() != null
                    && openProjectIds.contains(membership.getProjectId()) && projectIds.add(membership.getProjectId())
                    && role != null && !Boolean.TRUE.equals(role.getGlobalRole())
                    && role.getStatus() != null && role.getStatus() == ENABLED;
            if (!valid) {
                throw new BusinessException(ErrorCode.ACCOUNT_PROJECT_INVALID);
            }
        }
        // 已关闭项目的关联不在列表里展示，保存时保留，重新启用项目后成员关系不丢失
        if (!openProjectIds.isEmpty()) {
            projectUserMapper.delete(Wrappers.<ProjectUser>lambdaQuery()
                    .eq(ProjectUser::getAccountId, accountId).in(ProjectUser::getProjectId, openProjectIds));
        }
        for (AccountMembership membership : memberships) {
            ProjectUser projectUser = new ProjectUser();
            projectUser.setAccountId(accountId);
            projectUser.setProjectId(membership.getProjectId());
            projectUser.setRoleId(membership.getRoleId());
            projectUserMapper.insert(projectUser);
        }
    }

    private void requireAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (accountMapper.selectById(accountId) == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private Set<Long> openProjectIds() {
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery().eq(Project::getClosed, false)).stream()
                .map(Project::getId).collect(Collectors.toSet());
    }

    private final PermissionService permissionService;

}
