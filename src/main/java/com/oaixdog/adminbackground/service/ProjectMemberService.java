package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.protocol.ProjectMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目成员：项目管理员按登录名把已有账号拉进当前项目，并分配本项目的项目角色。
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private static final String MENU = "project_member";

    private final PermissionService permissionService;
    private final AccountMapper accountMapper;
    private final ProjectUserMapper projectUserMapper;
    private final RoleMapper roleMapper;

    public List<ProjectMemberResponse> list(String authorization, Long projectId) {
        permissionService.requireProjectMenu(authorization, projectId, MENU);
        List<ProjectUser> members = projectUserMapper.selectList(Wrappers.<ProjectUser>lambdaQuery()
                .eq(ProjectUser::getProjectId, projectId).orderByAsc(ProjectUser::getId));
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, Account> accounts = accountMapper.selectBatchIds(members.stream().map(ProjectUser::getAccountId).toList())
                .stream().collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<Long, Role> roles = roleMapper.selectBatchIds(members.stream().map(ProjectUser::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        return members.stream().filter(member -> accounts.containsKey(member.getAccountId()))
                .map(member -> ProjectMemberResponse.valueOf(member, accounts.get(member.getAccountId()), roles.get(member.getRoleId())))
                .toList();
    }

    public void add(String authorization, Long projectId, String account, Long roleId) {
        permissionService.requireProjectMenu(authorization, projectId, MENU + ":add");
        String loginAccount = account == null ? "" : account.trim();
        if (loginAccount.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Account target = accountMapper.selectOne(Wrappers.<Account>lambdaQuery().eq(Account::getAccount, loginAccount));
        if (target == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        requireProjectRole(projectId, roleId);
        if (findMember(projectId, target.getId()) != null) {
            throw new BusinessException(ErrorCode.MEMBER_EXISTS);
        }
        ProjectUser member = new ProjectUser();
        member.setProjectId(projectId);
        member.setAccountId(target.getId());
        member.setRoleId(roleId);
        member.setCreatedAt(System.currentTimeMillis());
        projectUserMapper.insert(member);
    }

    public void update(String authorization, Long projectId, Long accountId, Long roleId) {
        permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        ProjectUser member = requireMember(projectId, accountId);
        requireProjectRole(projectId, roleId);
        projectUserMapper.update(null, Wrappers.<ProjectUser>lambdaUpdate().eq(ProjectUser::getId, member.getId())
                .set(ProjectUser::getRoleId, roleId));
    }

    public void remove(String authorization, Long projectId, Long accountId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":remove");
        if (Objects.equals(access.account().getId(), accountId)) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        ProjectUser member = requireMember(projectId, accountId);
        projectUserMapper.deleteById(member.getId());
    }

    private ProjectUser findMember(Long projectId, Long accountId) {
        return projectUserMapper.selectOne(Wrappers.<ProjectUser>lambdaQuery()
                .eq(ProjectUser::getProjectId, projectId).eq(ProjectUser::getAccountId, accountId));
    }

    private ProjectUser requireMember(Long projectId, Long accountId) {
        ProjectUser member = accountId == null ? null : findMember(projectId, accountId);
        if (member == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    private void requireProjectRole(Long projectId, Long roleId) {
        Role role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null || !Objects.equals(role.getRoleType(), Role.TYPE_PROJECT) || !Objects.equals(role.getProjectId(), projectId)
                || !Objects.equals(role.getStatus(), Status.ENABLED)) {
            throw new BusinessException(ErrorCode.ACCOUNT_PROJECT_INVALID);
        }
    }
}
