package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.AccountRoleMapper;
import com.oaixdog.adminbackground.mapper.MenuMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.mapper.RoleMenuMapper;
import com.oaixdog.adminbackground.protocol.MenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final int ENABLED = 1;

    private final LoginService loginService;
    private final AccountMapper accountMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final ProjectUserMapper projectUserMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    public Account requireAccount(String authorization) {
        Long accountId = loginService.findAccountId(authorization);
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return account;
    }

    public List<MenuResponse> listMenus(String authorization) {
        Account account = requireAccount(authorization);
        List<Long> roleIds = roleIds(account);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> menuIds = new HashSet<>(roleMenuMapper.listMenuIds(roleIds));
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<Menu> menus = menuMapper.selectBatchIds(menuIds).stream()
                .filter(menu -> menu.getStatus() != null && menu.getStatus() == ENABLED)
                .sorted(Comparator.comparing((Menu menu) -> menu.getParentId() == null ? 0L : menu.getParentId())
                        .thenComparing(menu -> menu.getSort() == null ? 0 : menu.getSort())
                        .thenComparing(Menu::getId))
                .toList();
        return MenuResponse.listOf(menus);
    }

    public void requireMenu(String authorization, String routeName) {
        Account account = requireAccount(authorization);
        List<Long> roleIds = roleIds(account);
        if (roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        List<Long> menuIds = roleMenuMapper.listMenuIds(roleIds);
        if (menuIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        Long count = menuMapper.selectCount(Wrappers.<Menu>lambdaQuery()
                .in(Menu::getId, menuIds)
                .eq(Menu::getRouteName, routeName)
                .eq(Menu::getStatus, ENABLED));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }

    public boolean hasGlobalRole(Account account) {
        List<Long> roleIds = accountRoleMapper.listRoleIds(account.getId());
        if (roleIds.isEmpty()) {
            return false;
        }
        Long count = roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .in(Role::getId, roleIds)
                .eq(Role::getGlobalRole, true));
        return count != null && count > 0;
    }

    public List<Long> roleIds(Account account) {
        Set<Long> roleIds = new HashSet<>(accountRoleMapper.listRoleIds(account.getId()));
        if (account.getLastProjectId() != null) {
            ProjectUser membership = projectUserMapper.selectOne(Wrappers.<ProjectUser>lambdaQuery()
                    .eq(ProjectUser::getAccountId, account.getId())
                    .eq(ProjectUser::getProjectId, account.getLastProjectId()));
            if (membership != null) {
                roleIds.add(membership.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getStatus() == null || role.getStatus() == 1)
                .map(Role::getId)
                .toList();
    }
}
