package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.AccountRoleMapper;
import com.oaixdog.adminbackground.mapper.MenuMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.mapper.RoleMenuMapper;
import com.oaixdog.adminbackground.protocol.MenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 权限判断分两条路：平台接口只认账号级的平台角色；项目接口只认当前会话项目里的项目角色，超级管理员在任何项目里拥有全部项目菜单。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final LoginService loginService;
    private final AccountMapper accountMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final ProjectUserMapper projectUserMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final ProjectMapper projectMapper;

    public Account requireAccount(String authorization) {
        Long accountId = loginService.findAccountId(authorization);
        Account account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null || !Objects.equals(account.getStatus(), Status.ENABLED)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        loginService.touch(authorization, account.getId());
        return account;
    }

    public Account requirePlatformMenu(String authorization, String routeName) {
        Account account = requireAccount(authorization);
        Long menuId = menuIdByRoute(routeName, Menu.SCOPE_PLATFORM);
        if (menuId == null || !menuIds(platformRoleIds(account), Menu.SCOPE_PLATFORM).contains(menuId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return account;
    }

    /**
     * 项目接口入口：请求携带的项目必须与会话当前项目一致，项目未关闭，账号是成员或超级管理员，并且拥有目标菜单。
     */
    public ProjectAccess requireProjectMenu(String authorization, Long projectId, String routeName) {
        Account account = requireAccount(authorization);
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!Objects.equals(projectId, loginService.findSessionProjectId(authorization))) {
            throw new BusinessException(ErrorCode.PROJECT_CONTEXT_MISMATCH);
        }
        if (!canAccessProject(account, projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_SWITCH_FORBIDDEN);
        }
        Long menuId = menuIdByRoute(routeName, Menu.SCOPE_PROJECT);
        if (menuId == null || !projectMenuIds(account, projectId).contains(menuId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return new ProjectAccess(account, projectId);
    }

    public List<MenuResponse> listMenus(String authorization, Long projectId) {
        Account account = requireAccount(authorization);
        Long sessionProjectId = loginService.findSessionProjectId(authorization);
        if (!Objects.equals(projectId, sessionProjectId)) {
            throw new BusinessException(ErrorCode.PROJECT_CONTEXT_MISMATCH);
        }
        Set<Long> ids = new HashSet<>(menuIds(platformRoleIds(account), Menu.SCOPE_PLATFORM));
        if (sessionProjectId != null && canAccessProject(account, sessionProjectId)) {
            ids.addAll(projectMenuIds(account, sessionProjectId));
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Menu> menus = menuMapper.selectBatchIds(ids).stream()
                .sorted(Comparator.comparing((Menu menu) -> menu.getParentId() == null ? 0L : menu.getParentId())
                        .thenComparing(menu -> menu.getSort() == null ? 0 : menu.getSort())
                        .thenComparing(Menu::getId))
                .toList();
        return MenuResponse.listOf(menus);
    }

    public boolean isSuperAdmin(Account account) {
        List<Long> roleIds = accountRoleMapper.listRoleIds(account.getId());
        return !roleIds.isEmpty() && roleMapper.selectCount(Wrappers.<Role>lambdaQuery().in(Role::getId, roleIds)
                .eq(Role::getRoleType, Role.TYPE_PLATFORM).eq(Role::getCode, Role.SUPER_ADMIN_CODE)
                .eq(Role::getStatus, Status.ENABLED)) > 0;
    }

    /** 项目未关闭，并且账号是超级管理员或该项目成员 */
    public boolean canAccessProject(Account account, Long projectId) {
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getStatus(), Status.ENABLED)) {
            return false;
        }
        return isSuperAdmin(account) || projectUserMapper.selectCount(Wrappers.<ProjectUser>lambdaQuery()
                .eq(ProjectUser::getAccountId, account.getId()).eq(ProjectUser::getProjectId, projectId)) > 0;
    }

    private Set<Long> projectMenuIds(Account account, Long projectId) {
        if (isSuperAdmin(account)) {
            return new HashSet<>(menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getScope, Menu.SCOPE_PROJECT)
                    .eq(Menu::getStatus, Status.ENABLED)).stream().map(Menu::getId).toList());
        }
        ProjectUser membership = projectUserMapper.selectOne(Wrappers.<ProjectUser>lambdaQuery()
                .eq(ProjectUser::getAccountId, account.getId()).eq(ProjectUser::getProjectId, projectId));
        if (membership == null) {
            return Set.of();
        }
        Role role = roleMapper.selectById(membership.getRoleId());
        boolean valid = role != null && Objects.equals(role.getRoleType(), Role.TYPE_PROJECT)
                && Objects.equals(role.getProjectId(), projectId) && Objects.equals(role.getStatus(), Status.ENABLED);
        return valid ? menuIds(List.of(role.getId()), Menu.SCOPE_PROJECT) : Set.of();
    }

    private List<Long> platformRoleIds(Account account) {
        List<Long> roleIds = accountRoleMapper.listRoleIds(account.getId());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().in(Role::getId, roleIds)
                .eq(Role::getRoleType, Role.TYPE_PLATFORM).eq(Role::getStatus, Status.ENABLED)).stream().map(Role::getId).toList();
    }

    /** 角色拥有的、指定范围内的启用菜单 */
    private Set<Long> menuIds(List<Long> roleIds, int scope) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = new ArrayList<>(new HashSet<>(roleMenuMapper.listMenuIds(roleIds)));
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(menuMapper.selectList(Wrappers.<Menu>lambdaQuery().in(Menu::getId, ids)
                .eq(Menu::getScope, scope).eq(Menu::getStatus, Status.ENABLED)).stream().map(Menu::getId).toList());
    }

    private Long menuIdByRoute(String routeName, int scope) {
        Menu menu = menuMapper.selectOne(Wrappers.<Menu>lambdaQuery().eq(Menu::getRouteName, routeName)
                .eq(Menu::getScope, scope).eq(Menu::getStatus, Status.ENABLED));
        return menu == null ? null : menu.getId();
    }

    public record ProjectAccess(Account account, long projectId) {
    }
}
