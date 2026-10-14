package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.RoleMenu;
import com.oaixdog.adminbackground.entity.Status;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 菜单范围只在顶级目录选择，子项跟随上级。平台菜单只能分配给平台角色，项目菜单只能分配给模板和项目角色。
 * 按钮挂在菜单下面，route_name 保存权限标识（例如 manage_user:create），写接口按它校验。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private static final String MENU = "manage_menu";
    private static final int DIRECTORY = 1;
    private static final int MENU_ITEM = 2;
    private static final int BUTTON = 3;
    private static final int NAME_MAX_LENGTH = 64;
    private static final int ICON_MAX_LENGTH = 64;
    private static final int ROUTE_NAME_MAX_LENGTH = 64;
    private static final int ROUTE_PATH_MAX_LENGTH = 128;
    private static final long MENU_ID_BLOCK = 10000;

    private final PermissionService permissionService;
    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    public List<MenuResponse> list(String authorization) {
        permissionService.requirePlatformMenu(authorization, MENU);
        List<Menu> menus = menuMapper.selectList(Wrappers.<Menu>lambdaQuery());
        menus.sort(Comparator.comparing((Menu menu) -> menu.getParentId() == null ? 0L : menu.getParentId())
                .thenComparing(menu -> menu.getSort() == null ? 0 : menu.getSort())
                .thenComparing(Menu::getId));
        return MenuResponse.listOf(menus);
    }

    @Transactional
    public MenuResponse create(String authorization, MenuCreateRequest request) {
        permissionService.requirePlatformMenu(authorization, MENU + ":create");
        Menu menu = new Menu();
        fill(menu, request.getParentId(), request.getMenuType(), request.getScope(), request.getName(), request.getIcon(),
                request.getRouteName(), request.getRoutePath(), request.getStatus(), request.getHidden(), request.getSort());
        long menuId = nextMenuId(menu.getParentId());
        menu.setId(menuId);
        requireValidParent(menuId, menu.getParentId(), menu.getMenuType());
        requireUniqueRouteName(menuId, menu.getRouteName());
        long now = System.currentTimeMillis();
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        try {
            menuMapper.insert(menu);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.MENU_ID_EXISTS);
        }
        if (menu.getScope() == Menu.SCOPE_PLATFORM) {
            assignToSuperAdmin(menu.getId());
        }
        return MenuResponse.valueOf(menu);
    }

    @Transactional
    public MenuResponse update(String authorization, MenuUpdateRequest request) {
        permissionService.requirePlatformMenu(authorization, MENU + ":update");
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
        int previousScope = menu.getScope();
        fill(menu, request.getParentId(), request.getMenuType(), request.getScope(), request.getName(), request.getIcon(),
                request.getRouteName(), request.getRoutePath(), request.getStatus(), request.getHidden(), request.getSort());
        requireValidParent(menu.getId(), menu.getParentId(), menu.getMenuType());
        requireUniqueRouteName(menu.getId(), menu.getRouteName());
        long children = childCount(menu.getId());
        // 目录下只能是目录和菜单，菜单下只能是按钮，按钮下不能有子项
        long buttons = buttonCount(menu.getId());
        boolean childrenFit = switch (menu.getMenuType()) {
            case DIRECTORY -> buttons == 0;
            case MENU_ITEM -> buttons == children;
            default -> children == 0;
        };
        if (!childrenFit) {
            throw new BusinessException(ErrorCode.MENU_TYPE_CHANGE_FORBIDDEN);
        }
        boolean scopeChanged = menu.getScope() != previousScope;
        if (scopeChanged && children > 0) {
            throw new BusinessException(ErrorCode.MENU_SCOPE_CHANGE_FORBIDDEN);
        }
        menuMapper.update(null, Wrappers.<Menu>lambdaUpdate()
                .eq(Menu::getId, menu.getId())
                .set(Menu::getParentId, menu.getParentId())
                .set(Menu::getMenuType, menu.getMenuType())
                .set(Menu::getScope, menu.getScope())
                .set(Menu::getName, menu.getName())
                .set(Menu::getIcon, menu.getIcon())
                .set(Menu::getRouteName, menu.getRouteName())
                .set(Menu::getRoutePath, menu.getRoutePath())
                .set(Menu::getStatus, menu.getStatus())
                .set(Menu::getHidden, menu.getHidden())
                .set(Menu::getSort, menu.getSort())
                .set(Menu::getUpdatedAt, System.currentTimeMillis()));
        if (scopeChanged) {
            // 范围变了，原来分配给另一类角色的关联不再合法
            roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getMenuId, menu.getId()));
            if (menu.getScope() == Menu.SCOPE_PLATFORM) {
                assignToSuperAdmin(menu.getId());
            }
        }
        return MenuResponse.valueOf(menu);
    }

    @Transactional
    public void remove(String authorization, Long menuId) {
        permissionService.requirePlatformMenu(authorization, MENU + ":remove");
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

    private void fill(Menu menu, Long parentId, Integer menuType, Integer scope, String name, String icon, String routeName,
                      String routePath, Integer status, Boolean hidden, Integer sort) {
        if (menuType == null || (menuType != DIRECTORY && menuType != MENU_ITEM && menuType != BUTTON) || !Status.valid(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String menuName = name == null ? "" : name.trim();
        String menuIcon = icon == null ? "" : icon.trim();
        String menuRouteName = routeName == null ? "" : routeName.trim();
        String menuRoutePath = routePath == null ? "" : routePath.trim();
        if (StringUtils.isBlank(menuName) || menuName.length() > NAME_MAX_LENGTH
                || (menuType != DIRECTORY && StringUtils.isBlank(menuRouteName))
                || menuIcon.length() > ICON_MAX_LENGTH
                || menuRouteName.length() > ROUTE_NAME_MAX_LENGTH
                || menuRoutePath.length() > ROUTE_PATH_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long menuParentId = parentId == null || parentId == 0 ? null : parentId;
        if (menuType == BUTTON && menuParentId == null) {
            throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
        }
        // 子项的范围跟随上级，只有顶级目录使用请求里的范围
        Menu parent = menuParentId == null ? null : menuMapper.selectById(menuParentId);
        if (menuParentId != null && parent == null) {
            throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
        }
        Integer menuScope = parent != null ? parent.getScope() : scope;
        if (menuScope == null || (menuScope != Menu.SCOPE_PLATFORM && menuScope != Menu.SCOPE_PROJECT)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        menu.setParentId(menuParentId);
        menu.setMenuType(menuType);
        menu.setScope(menuScope);
        menu.setName(menuName);
        menu.setIcon(menuType == BUTTON ? "" : menuIcon);
        menu.setRouteName(menuRouteName);
        menu.setRoutePath(menuType == BUTTON ? "" : menuRoutePath);
        menu.setStatus(status);
        menu.setHidden(menuType != BUTTON && Boolean.TRUE.equals(hidden));
        menu.setBuiltIn(false);
        menu.setSort(sort == null ? 0 : sort);
    }

    /**
     * 菜单 ID 按顶级目录分段：顶级依次为 10000、20000……；子孙菜单在所属顶级目录的段内自增，例如 10001、10002。
     * 每段最多 9999 个子孙菜单。移动到其他顶级目录的菜单保留原 ID。
     */
    private long nextMenuId(Long parentId) {
        if (parentId == null) {
            long maxTop = menuMapper.selectList(Wrappers.<Menu>lambdaQuery().isNull(Menu::getParentId)).stream()
                    .mapToLong(Menu::getId).max().orElse(0);
            return (maxTop / MENU_ID_BLOCK + 1) * MENU_ID_BLOCK;
        }
        long block = topLevelId(parentId);
        long maxInBlock = menuMapper.selectList(Wrappers.<Menu>lambdaQuery().ge(Menu::getId, block).lt(Menu::getId, block + MENU_ID_BLOCK))
                .stream().mapToLong(Menu::getId).max().orElse(block);
        if (maxInBlock + 1 >= block + MENU_ID_BLOCK) {
            throw new BusinessException(ErrorCode.MENU_BLOCK_FULL);
        }
        return maxInBlock + 1;
    }

    /** 所属顶级目录 ID 向下取整到段起点，旧数据或移动过的菜单也能落在合法的段里 */
    private long topLevelId(Long menuId) {
        Long cursor = menuId;
        Menu menu = menuMapper.selectById(cursor);
        while (menu != null && menu.getParentId() != null) {
            cursor = menu.getParentId();
            menu = menuMapper.selectById(cursor);
        }
        return cursor / MENU_ID_BLOCK * MENU_ID_BLOCK;
    }

    /** 按钮的上级必须是菜单，目录和菜单的上级必须是目录 */
    private void requireValidParent(Long menuId, Long parentId, int menuType) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(menuId, parentId)) {
            throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
        }
        Long cursor = parentId;
        while (cursor != null) {
            if (Objects.equals(menuId, cursor)) {
                throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
            }
            Menu parent = menuMapper.selectById(cursor);
            if (parent == null) {
                throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
            }
            int expectedParentType = menuType == BUTTON ? MENU_ITEM : DIRECTORY;
            if (cursor.equals(parentId) && !Objects.equals(parent.getMenuType(), expectedParentType)) {
                throw new BusinessException(ErrorCode.MENU_PARENT_INVALID);
            }
            cursor = parent.getParentId();
        }
    }

    private void requireUniqueRouteName(Long menuId, String routeName) {
        if (StringUtils.isBlank(routeName)) {
            return;
        }
        Long count = menuMapper.selectCount(Wrappers.<Menu>lambdaQuery().eq(Menu::getRouteName, routeName).ne(Menu::getId, menuId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.MENU_ROUTE_NAME_EXISTS);
        }
    }

    private long childCount(Long menuId) {
        Long count = menuMapper.selectCount(Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, menuId));
        return count == null ? 0 : count;
    }

    private long buttonCount(Long menuId) {
        Long count = menuMapper.selectCount(Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, menuId).eq(Menu::getMenuType, BUTTON));
        return count == null ? 0 : count;
    }

    private void assignToSuperAdmin(Long menuId) {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_PLATFORM)
                .eq(Role::getCode, Role.SUPER_ADMIN_CODE));
        if (role == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        RoleMenu roleMenu = new RoleMenu();
        roleMenu.setRoleId(role.getId());
        roleMenu.setMenuId(menuId);
        roleMenuMapper.insert(roleMenu);
    }
}
