package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.AccountRole;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.RoleMenu;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountRoleMapper;
import com.oaixdog.adminbackground.mapper.MenuMapper;
import com.oaixdog.adminbackground.mapper.ProjectUserMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.mapper.RoleMenuMapper;
import com.oaixdog.adminbackground.protocol.MenuResponse;
import com.oaixdog.adminbackground.protocol.RoleMenuListResponse;
import com.oaixdog.adminbackground.protocol.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final int NAME_MAX_LENGTH = 32;
    private static final int CODE_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MAX_LENGTH = 128;

    private final PermissionService permissionService;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final ProjectUserMapper projectUserMapper;
    private final AccountRoleMapper accountRoleMapper;

    public List<RoleResponse> list(String authorization) {
        permissionService.requireMenu(authorization, "manage_role");
        return roleMapper.selectList(null).stream().map(RoleResponse::valueOf).toList();
    }

    public RoleResponse create(String authorization, String name, String code, String description, Integer status) {
        permissionService.requireMenu(authorization, "manage_role");
        String roleName = name == null ? "" : name.trim();
        String roleCode = code == null ? "" : code.trim();
        String roleDescription = description == null ? "" : description.trim();
        if (!valid(roleName, roleCode, roleDescription, status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (roleMapper.selectCount(Wrappers.<Role>lambdaQuery().eq(Role::getName, roleName)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_NAME_EXISTS);
        }
        if (roleMapper.selectCount(Wrappers.<Role>lambdaQuery().eq(Role::getCode, roleCode)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }
        Role role = new Role();
        role.setName(roleName);
        role.setCode(roleCode);
        role.setDescription(roleDescription);
        role.setStatus(status);
        role.setGlobalRole(false);
        role.setBuiltIn(false);
        roleMapper.insert(role);
        return RoleResponse.valueOf(role);
    }

    public RoleResponse update(String authorization, Long roleId, String name, String code, String description, Integer status) {
        permissionService.requireMenu(authorization, "manage_role");
        Role role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_FORBIDDEN);
        }
        String roleName = name == null ? "" : name.trim();
        String roleCode = code == null ? "" : code.trim();
        String roleDescription = description == null ? "" : description.trim();
        if (!valid(roleName, roleCode, roleDescription, status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long duplicate = roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .eq(Role::getName, roleName).ne(Role::getId, roleId));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.ROLE_NAME_EXISTS);
        }
        Long codeDuplicate = roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .eq(Role::getCode, roleCode).ne(Role::getId, roleId));
        if (codeDuplicate != null && codeDuplicate > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }
        role.setName(roleName);
        role.setCode(roleCode);
        role.setDescription(roleDescription);
        role.setStatus(status);
        roleMapper.updateById(role);
        return RoleResponse.valueOf(role);
    }

    public void remove(String authorization, Long roleId) {
        permissionService.requireMenu(authorization, "manage_role");
        Role role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_FORBIDDEN);
        }
        if (projectUserMapper.selectCount(Wrappers.<ProjectUser>lambdaQuery().eq(ProjectUser::getRoleId, roleId)) > 0
                || accountRoleMapper.selectCount(Wrappers.<AccountRole>lambdaQuery().eq(AccountRole::getRoleId, roleId)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    public RoleMenuListResponse listMenus(String authorization, Long roleId) {
        permissionService.requireMenu(authorization, "manage_role");
        requireRole(roleId);
        List<Menu> menus = menuMapper.selectList(null).stream()
                .sorted(Comparator.comparing((Menu menu) -> menu.getParentId() == null ? 0L : menu.getParentId())
                        .thenComparing(menu -> menu.getSort() == null ? 0 : menu.getSort())
                        .thenComparing(Menu::getId)).toList();
        List<Long> menuIds = roleMenuMapper.selectList(Wrappers.<RoleMenu>lambdaQuery()
                        .eq(RoleMenu::getRoleId, roleId)).stream().map(RoleMenu::getMenuId).toList();
        return RoleMenuListResponse.valueOf(menus.stream().map(MenuResponse::valueOf).toList(), menuIds);
    }

    @Transactional
    public void updateMenus(String authorization, Long roleId, List<Long> menuIds) {
        permissionService.requireMenu(authorization, "manage_role");
        Role role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_FORBIDDEN);
        }
        if (menuIds == null || menuIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.ROLE_MENU_INVALID);
        }
        Set<Long> assigned = new HashSet<>(menuIds);
        List<Menu> menus = assigned.isEmpty() ? List.of() : menuMapper.selectBatchIds(assigned);
        // 菜单必须存在，并且上级目录要一起分配，否则侧栏无法从目录生成这些菜单
        if (menus.size() != assigned.size()
                || menus.stream().anyMatch(menu -> menu.getParentId() != null && !assigned.contains(menu.getParentId()))) {
            throw new BusinessException(ErrorCode.ROLE_MENU_INVALID);
        }
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds.stream().distinct().toList()) {
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }

    private Role requireRole(Long roleId) {
        if (roleId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private boolean valid(String name, String code, String description, Integer status) {
        return !name.isBlank() && name.length() <= NAME_MAX_LENGTH
                && !code.isBlank() && code.length() <= CODE_MAX_LENGTH
                && description.length() <= DESCRIPTION_MAX_LENGTH
                && status != null && (status == 1 || status == 2);
    }
}
