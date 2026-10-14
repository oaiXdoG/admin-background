package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.AccountRole;
import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.AccountRoleMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.protocol.AccountCreateResponse;
import com.oaixdog.adminbackground.protocol.AccountMembership;
import com.oaixdog.adminbackground.protocol.AccountResponse;
import com.oaixdog.adminbackground.protocol.ProjectManageResponse;
import com.oaixdog.adminbackground.protocol.RoleResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 平台的用户管理：账号只在这里创建；平台角色写入 account_role，项目关联写入 project_user，项目关联的角色必须属于对应项目。
 */
@Service
@RequiredArgsConstructor
public class AccountCommandService {

    private static final String MENU = "manage_user";
    private static final int ACCOUNT_MAX_LENGTH = 64;
    private static final int USERNAME_MAX_LENGTH = 64;
    private static final int REMARK_MAX_LENGTH = 128;

    private final PermissionService permissionService;
    private final LoginService loginService;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper accountMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final ProjectMapper projectMapper;
    private final ProjectUserMapper projectUserMapper;
    private final RoleMapper roleMapper;

    public List<AccountResponse> list(String authorization) {
        permissionService.requirePlatformMenu(authorization, MENU);
        Map<Long, Role> platformRoles = roleMapper.selectList(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_PLATFORM))
                .stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<Long, List<Role>> rolesByAccount = accountRoleMapper.selectList(null).stream()
                .filter(row -> platformRoles.containsKey(row.getRoleId()))
                .collect(Collectors.groupingBy(AccountRole::getAccountId, Collectors.mapping(row -> platformRoles.get(row.getRoleId()), Collectors.toList())));
        Set<Long> openProjectIds = openProjectIds();
        Map<Long, Long> projectCounts = projectUserMapper.selectList(null).stream()
                .filter(row -> openProjectIds.contains(row.getProjectId()))
                .collect(Collectors.groupingBy(ProjectUser::getAccountId, Collectors.counting()));
        return accountMapper.selectList(Wrappers.<Account>lambdaQuery().orderByAsc(Account::getId)).stream()
                .map(account -> AccountResponse.valueOf(account, rolesByAccount.getOrDefault(account.getId(), List.of()),
                        projectCounts.getOrDefault(account.getId(), 0L).intValue()))
                .toList();
    }

    public AccountCreateResponse create(String authorization, String account, String password, String username, String remark) {
        permissionService.requirePlatformMenu(authorization, MENU + ":create");
        String loginAccount = account == null ? "" : account.trim();
        String displayName = username == null ? "" : username.trim();
        String accountRemark = remark == null ? "" : remark.trim();
        if (StringUtils.isBlank(loginAccount) || loginAccount.length() > ACCOUNT_MAX_LENGTH
                || StringUtils.isBlank(displayName) || displayName.length() > USERNAME_MAX_LENGTH || accountRemark.length() > REMARK_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!PasswordPolicy.valid(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        if (accountMapper.selectCount(Wrappers.<Account>lambdaQuery().eq(Account::getAccount, loginAccount)) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXISTS);
        }
        long now = System.currentTimeMillis();
        Account created = new Account();
        created.setAccount(loginAccount);
        created.setPasswordHash(passwordEncoder.encode(password));
        created.setUsername(displayName);
        created.setRemark(accountRemark);
        created.setStatus(Status.ENABLED);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        accountMapper.insert(created);
        return AccountCreateResponse.valueOf(created.getId(), created.getAccount(), created.getUsername());
    }

    /** 修改显示名和备注；提交密码时重置密码，并让该账号已登录的会话失效 */
    public void update(String authorization, Long accountId, String username, String password, String remark) {
        permissionService.requirePlatformMenu(authorization, MENU + ":update");
        requireAccount(accountId);
        String displayName = username == null ? "" : username.trim();
        String accountRemark = remark == null ? "" : remark.trim();
        if (StringUtils.isBlank(displayName) || displayName.length() > USERNAME_MAX_LENGTH || accountRemark.length() > REMARK_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (password != null && !PasswordPolicy.valid(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        Account updated = new Account();
        updated.setId(accountId);
        updated.setUsername(displayName);
        updated.setRemark(accountRemark);
        updated.setUpdatedAt(System.currentTimeMillis());
        if (password != null) {
            updated.setPasswordHash(passwordEncoder.encode(password));
        }
        accountMapper.updateById(updated);
        if (password != null) {
            loginService.invalidateAccount(accountId);
        }
    }

    /** 启用或禁用账号；禁用后已登录的会话立即失效。不能修改自己的状态 */
    public void updateStatus(String authorization, Long accountId, Integer status) {
        Account operator = permissionService.requirePlatformMenu(authorization, MENU + ":status");
        requireAccount(accountId);
        if (!Status.valid(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (Objects.equals(operator.getId(), accountId)) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getId, accountId)
                .set(Account::getStatus, status).set(Account::getUpdatedAt, System.currentTimeMillis()));
        if (status == Status.DISABLED) {
            loginService.invalidateAccount(accountId);
        }
    }

    /**
     * 整体替换账号的平台角色。分配或撤销「超级管理员」只能由超级管理员操作，并且不能撤销自己的超级管理员。
     */
    @Transactional
    public void updateRoles(String authorization, Long accountId, List<Long> roleIds) {
        Account operator = permissionService.requirePlatformMenu(authorization, MENU + ":role");
        requireAccount(accountId);
        if (roleIds == null || roleIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<Long> target = new HashSet<>(roleIds);
        Map<Long, Role> platformRoles = roleMapper.selectList(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_PLATFORM))
                .stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        if (!platformRoles.keySet().containsAll(target)) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        Set<Long> current = new HashSet<>(accountRoleMapper.listRoleIds(accountId));
        Long superAdminId = platformRoles.values().stream().filter(role -> Role.SUPER_ADMIN_CODE.equals(role.getCode()))
                .map(Role::getId).findFirst().orElse(null);
        boolean superAdminChanged = superAdminId != null && current.contains(superAdminId) != target.contains(superAdminId);
        if (superAdminChanged && !permissionService.isSuperAdmin(operator)) {
            throw new BusinessException(ErrorCode.SUPER_ADMIN_REQUIRED);
        }
        if (superAdminChanged && Objects.equals(operator.getId(), accountId)) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        accountRoleMapper.delete(Wrappers.<AccountRole>lambdaQuery().eq(AccountRole::getAccountId, accountId));
        for (Long roleId : target) {
            AccountRole accountRole = new AccountRole();
            accountRole.setAccountId(accountId);
            accountRole.setRoleId(roleId);
            accountRoleMapper.insert(accountRole);
        }
    }

    public List<AccountMembership> listProjects(String authorization, Long accountId) {
        permissionService.requirePlatformMenu(authorization, MENU);
        requireAccount(accountId);
        Set<Long> openProjectIds = openProjectIds();
        return projectUserMapper.selectList(Wrappers.<ProjectUser>lambdaQuery().eq(ProjectUser::getAccountId, accountId)
                        .orderByAsc(ProjectUser::getProjectId)).stream()
                .filter(row -> openProjectIds.contains(row.getProjectId()))
                .map(row -> AccountMembership.valueOf(row.getProjectId(), row.getRoleId()))
                .toList();
    }

    /** 项目关联弹窗的项目下拉：全部未关闭项目，不要求操作者本身能进入这些项目 */
    public List<ProjectManageResponse> listProjectOptions(String authorization) {
        permissionService.requirePlatformMenu(authorization, MENU);
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery().eq(Project::getStatus, Status.ENABLED).orderByAsc(Project::getId))
                .stream().map(project -> ProjectManageResponse.valueOf(project, 0)).toList();
    }

    /** 项目关联弹窗的角色下拉：指定项目里启用的项目角色 */
    public List<RoleResponse> listProjectRoles(String authorization, Long projectId) {
        permissionService.requirePlatformMenu(authorization, MENU);
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_PROJECT)
                        .eq(Role::getProjectId, projectId).eq(Role::getStatus, Status.ENABLED).orderByAsc(Role::getId))
                .stream().map(RoleResponse::valueOf).toList();
    }

    @Transactional
    public void updateProjects(String authorization, Long accountId, List<AccountMembership> memberships) {
        permissionService.requirePlatformMenu(authorization, MENU + ":project");
        requireAccount(accountId);
        if (memberships == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<Long> openProjectIds = openProjectIds();
        Map<Long, Role> roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_PROJECT))
                .stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        Set<Long> projectIds = new HashSet<>();
        for (AccountMembership membership : memberships) {
            Role role = membership == null ? null : roles.get(membership.getRoleId());
            boolean valid = membership != null && membership.getProjectId() != null
                    && openProjectIds.contains(membership.getProjectId()) && projectIds.add(membership.getProjectId())
                    && role != null && Objects.equals(role.getProjectId(), membership.getProjectId())
                    && Objects.equals(role.getStatus(), Status.ENABLED);
            if (!valid) {
                throw new BusinessException(ErrorCode.ACCOUNT_PROJECT_INVALID);
            }
        }
        // 已关闭项目的关联不在列表里展示，保存时保留，重新启用项目后成员关系不丢失；仍保留的项目沿用原加入时间
        Map<Long, Long> joinedAt = projectUserMapper.selectList(Wrappers.<ProjectUser>lambdaQuery().eq(ProjectUser::getAccountId, accountId))
                .stream().collect(Collectors.toMap(ProjectUser::getProjectId, ProjectUser::getCreatedAt));
        if (!openProjectIds.isEmpty()) {
            projectUserMapper.delete(Wrappers.<ProjectUser>lambdaQuery()
                    .eq(ProjectUser::getAccountId, accountId).in(ProjectUser::getProjectId, openProjectIds));
        }
        long now = System.currentTimeMillis();
        for (AccountMembership membership : memberships) {
            ProjectUser projectUser = new ProjectUser();
            projectUser.setAccountId(accountId);
            projectUser.setProjectId(membership.getProjectId());
            projectUser.setRoleId(membership.getRoleId());
            projectUser.setCreatedAt(joinedAt.getOrDefault(membership.getProjectId(), now));
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
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery().eq(Project::getStatus, Status.ENABLED)).stream()
                .map(Project::getId).collect(Collectors.toSet());
    }
}
