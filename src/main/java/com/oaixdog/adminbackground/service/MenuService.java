package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.RoleMenu;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.MenuMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.mapper.RoleMenuMapper;
import com.oaixdog.adminbackground.protocol.MenuCreateRequest;
import com.oaixdog.adminbackground.protocol.MenuResponse;
import com.oaixdog.adminbackground.protocol.MenuUpdateRequest;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final int DIRECTORY = 1;
    private static final int MENU = 2;
    private static final int ENABLED = 1;
    private static final int DISABLED = 2;
    private static final int NAME_MAX_LENGTH = 64;
    private static final int ICON_MAX_LENGTH = 64;
    private static final int ROUTE_NAME_MAX_LENGTH = 64;
    private static final int ROUTE_PATH_MAX_LENGTH = 128;

    private final PermissionService permissionService;
    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    public List<MenuResponse> list(String authorization) {
        permissionService.requireMenu(authorization, "manage_menu");
        List<Menu> menus = menuMapper.selectList(Wrappers.<Menu>lambdaQuery());
        menus.sort(Comparator.comparing((Menu menu) -> menu.getParentId() == null ? 0L : menu.getParentId())
                .thenComparing(menu -> menu.getSort() == null ? 0 : menu.getSort())
                .thenComparing(Menu::getId));
        return MenuResponse.listOf(menus);
    }

    @Transactional
    public MenuResponse create(String authorization, MenuCreateRequest request) {
        permissionService.requireMenu(authorization, "manage_menu");
        Long menuId = request.getMenuId();
        if (menuId == null || menuId <= 0 || menuId > 9007199254740991L) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (menuMapper.selectById(menuId) != null) {
            throw new BusinessException(ErrorCode.MENU_ID_EXISTS);
        }
        Menu menu = new Menu();
        menu.setId(menuId);
        fill(menu, request.getParentId(), request.getMenuType(), request.getName(), request.getIcon(),
                request.getRouteName(), request.getRoutePath(), request.getStatus(), request.getHidden(), request.getSort());
        requireValidParent(menuId, menu.getParentId());
        try {
            menuMapper.insert(menu);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.MENU_ID_EXISTS);
        }
        assignToSuperAdmin(menu.getId());
        return MenuResponse.valueOf(menu);
    }

    public MenuResponse update(String authorization, MenuUpdateRequest request) {
        permissionService.requireMenu(authorization, "manage_menu");
        if (request.getMenuId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Menu menu = menuMapper.selectById(request.getMenuId());
        if (menu == null) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(menu.getBuiltIn())) {
            throw new BusinessException(ErrorCode.BUILT_IN_MENU_FORBIDDEN);
        }
        fill(menu, request.getParentId(), request.getMenuType(), request.getName(), request.getIcon(),
                request.getRouteName(), request.getRoutePath(), request.getStatus(), request.getHidden(), request.getSort());
        requireValidParent(menu.getId(), menu.getParentId());
        if (menu.getMenuType() == MENU && childCount(menu.getId()) > 0) {
            throw new BusinessException(ErrorCode.MENU_TYPE_CHANGE_FORBIDDEN);
        }
        menuMapper.update(null, Wrappers.<Menu>lambdaUpdate()
                .eq(Menu::getId, menu.getId())
                .set(Menu::getParentId, menu.getParentId())
                .set(Menu::getMenuType, menu.getMenuType())
                .set(Menu::getName, menu.getName())
                .set(Menu::getIcon, menu.getIcon())
                .set(Menu::getRouteName, menu.getRouteName())
                .set(Menu::getRoutePath, menu.getRoutePath())
                .set(Menu::getStatus, menu.getStatus())
                .set(Menu::getHidden, menu.getHidden())
                .set(Menu::getSort, menu.getSort()));
        return MenuResponse.valueOf(menu);
    }

    @Transactional
    public void remove(String authorization, Long menuId) {
        permissionService.requireMenu(authorization, "manage_menu");
        if (menuId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(menu.getBuiltIn())) {
            throw new BusinessException(ErrorCode.BUILT_IN_MENU_FORBIDDEN);
        }
        if (childCount(menuId) > 0) {
            throw new BusinessException(ErrorCode.MENU_NOT_EMPTY);
        }
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getMenuId, menuId));
        menuMapper.deleteById(menuId);
    }

    private void fill(Menu menu, Long parentId, Integer menuType, String name, String icon, String routeName,
                      String routePath, Integer status, Boolean hidden, Integer sort) {
        if (menuType == null || (menuType != DIRECTORY && menuType != MENU)
                || status == null || (status != ENABLED && status != DISABLED)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String menuName = name == null ? "" : name.trim();
        String menuIcon = icon == null ? "" : icon.trim();
        String menuRouteName = routeName == null ? "" : routeName.trim();
        String menuRoutePath = routePath == null ? "" : routePath.trim();
        if (StringUtils.isBlank(menuName) || menuName.length() > NAME_MAX_LENGTH
                || menuIcon.length() > ICON_MAX_LENGTH
                || menuRouteName.length() > ROUTE_NAME_MAX_LENGTH
                || menuRoutePath.length() > ROUTE_PATH_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        menu.setParentId(parentId == null || parentId == 0 ? null : parentId);
        menu.setMenuType(menuType);
        menu.setName(menuName);
        menu.setIcon(menuIcon);
        menu.setRouteName(menuRouteName);
        menu.setRoutePath(menuRoutePath);
        menu.setStatus(status);
        menu.setHidden(Boolean.TRUE.equals(hidden));
        menu.setBuiltIn(false);
        menu.setSort(sort == null ? 0 : sort);
    }

    private void requireValidParent(Long menuId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (menuId != null && menuId.equals(parentId)) {
            throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
        }
        Long cursor = parentId;
        while (cursor != null) {
            if (menuId != null && menuId.equals(cursor)) {
                throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
            }
            Menu parent = menuMapper.selectById(cursor);
            if (parent == null) {
                throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
            }
            if (cursor.equals(parentId) && parent.getMenuType() != null && parent.getMenuType() != DIRECTORY) {
                throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
            }
            cursor = parent.getParentId();
        }
    }

    private long childCount(Long menuId) {
        Long count = menuMapper.selectCount(Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, menuId));
        return count == null ? 0 : count;
    }

    private void assignToSuperAdmin(Long menuId) {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getName, "超级管理员"));
        if (role == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        RoleMenu roleMenu = new RoleMenu();
        roleMenu.setRoleId(role.getId());
        roleMenu.setMenuId(menuId);
        roleMenuMapper.insert(roleMenu);
    }
}
