package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.AccountRole;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.ProjectUser;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.RoleMenu;
import com.oaixdog.adminbackground.entity.Status;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 平台角色、角色模板、项目角色共用一套增删改查。区别只在权限菜单、所属项目和可分配的菜单范围：
 * 平台角色只能分配平台菜单；模板和项目角色只能分配项目菜单。模板复制到项目后与模板互不影响。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final int NAME_MAX_LENGTH = 32;
    private static final int DESCRIPTION_MAX_LENGTH = 128;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final PermissionService permissionService;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final ProjectUserMapper projectUserMapper;
    private final AccountRoleMapper accountRoleMapper;

    /** 角色所在的位置：类型 + 所属项目（项目角色才有） */
    public record Scope(int roleType, Long projectId) {

        public static Scope platform() {
            return new Scope(Role.TYPE_PLATFORM, null);
        }

        public static Scope template() {
            return new Scope(Role.TYPE_TEMPLATE, null);
        }

        public static Scope project(Long projectId) {
            return new Scope(Role.TYPE_PROJECT, projectId);
        }

        int menuScope() {
            return roleType == Role.TYPE_PLATFORM ? Menu.SCOPE_PLATFORM : Menu.SCOPE_PROJECT;
        }
    }

    public List<RoleResponse> list(String authorization, Scope scope) {
        requireAccess(authorization, scope, "");
        List<Role> roles = roleMapper.selectList(inScope(scope).orderByAsc(Role::getId));
        Map<Long, String> templateNames = roleMapper.selectList(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_TEMPLATE))
                .stream().collect(Collectors.toMap(Role::getId, Role::getName));
        return roles.stream().map(role -> RoleResponse.valueOf(role, templateNames.get(role.getTemplateId()))).toList();
    }

    public RoleResponse create(String authorization, Scope scope, String name, String code, String description, Integer status) {
        requireAccess(authorization, scope, ":create");
        RoleValues values = validate(name, code, description, status);
        requireUnique(scope, null, values);
        long now = System.currentTimeMillis();
        Role role = new Role();
        role.setRoleType(scope.roleType());
        role.setProjectId(scope.projectId());
        role.setName(values.name());
        role.setCode(values.code());
        role.setDescription(values.description());
        role.setStatus(status);
        role.setBuiltIn(false);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        roleMapper.insert(role);
        return RoleResponse.valueOf(role);
    }

    public RoleResponse update(String authorization, Scope scope, Long roleId, String name, String code, String description, Integer status) {
        requireAccess(authorization, scope, ":update");
        Role role = requireEditableRole(scope, roleId);
        RoleValues values = validate(name, code, description, status);
        requireUnique(scope, roleId, values);
        role.setName(values.name());
        role.setCode(values.code());
        role.setDescription(values.description());
        role.setStatus(status);
        role.setUpdatedAt(System.currentTimeMillis());
        roleMapper.updateById(role);
        return RoleResponse.valueOf(role);
    }

    @Transactional
    public void remove(String authorization, Scope scope, Long roleId) {
        requireAccess(authorization, scope, ":remove");
        requireEditableRole(scope, roleId);
        if (projectUserMapper.selectCount(Wrappers.<ProjectUser>lambdaQuery().eq(ProjectUser::getRoleId, roleId)) > 0
                || accountRoleMapper.selectCount(Wrappers.<AccountRole>lambdaQuery().eq(AccountRole::getRoleId, roleId)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        if (scope.roleType() == Role.TYPE_TEMPLATE) {
            roleMapper.update(null, Wrappers.<Role>lambdaUpdate().eq(Role::getTemplateId, roleId).set(Role::getTemplateId, null));
        }
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    /** 返回该角色可分配范围内的全部菜单和已分配的菜单 */
    public RoleMenuListResponse listMenus(String authorization, Scope scope, Long roleId) {
        requireAccess(authorization, scope, "");
        requireRole(scope, roleId);
        List<Menu> menus = menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getScope, scope.menuScope())).stream()
                .sorted(Comparator.comparing((Menu menu) -> menu.getParentId() == null ? 0L : menu.getParentId())
                        .thenComparing(menu -> menu.getSort() == null ? 0 : menu.getSort())
                        .thenComparing(Menu::getId)).toList();
        Set<Long> inScope = menus.stream().map(Menu::getId).collect(Collectors.toSet());
        List<Long> menuIds = roleMenuMapper.listMenuIds(List.of(roleId)).stream().filter(inScope::contains).toList();
        return RoleMenuListResponse.valueOf(menus.stream().map(MenuResponse::valueOf).toList(), menuIds);
    }

    @Transactional
    public void updateMenus(String authorization, Scope scope, Long roleId, List<Long> menuIds) {
        requireAccess(authorization, scope, ":menu");
        requireEditableRole(scope, roleId);
        if (menuIds == null || menuIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.ROLE_MENU_INVALID);
        }
        Set<Long> assigned = new HashSet<>(menuIds);
        List<Menu> menus = assigned.isEmpty() ? List.of() : menuMapper.selectBatchIds(assigned);
        // 菜单必须存在、在角色可分配的范围内，并且上级目录要一起分配，否则侧栏无法从目录生成这些菜单
        if (menus.size() != assigned.size()
                || menus.stream().anyMatch(menu -> !Objects.equals(menu.getScope(), scope.menuScope())
                || (menu.getParentId() != null && !assigned.contains(menu.getParentId())))) {
            throw new BusinessException(ErrorCode.ROLE_MENU_INVALID);
        }
        replaceMenus(roleId, assigned);
    }

    /** 下拉选项的来源，各自只校验所在页面的权限 */
    public enum OptionSource { PROJECT_ROLE_IMPORT, PROJECT_CREATE, ACCOUNT_PLATFORM_ROLE, PROJECT_MEMBER }

    /** 下拉选项：只返回启用的角色。模板用于导入和新建项目，平台角色用于分配给账号，项目角色用于成员管理 */
    public List<RoleResponse> options(String authorization, OptionSource source, Long projectId) {
        Scope scope = switch (source) {
            case PROJECT_ROLE_IMPORT -> {
                permissionService.requireProjectMenu(authorization, projectId, "project_role");
                yield Scope.template();
            }
            case PROJECT_CREATE -> {
                permissionService.requirePlatformMenu(authorization, "manage_project");
                yield Scope.template();
            }
            case ACCOUNT_PLATFORM_ROLE -> {
                permissionService.requirePlatformMenu(authorization, "manage_user");
                yield Scope.platform();
            }
            case PROJECT_MEMBER -> {
                permissionService.requireProjectMenu(authorization, projectId, "project_member");
                yield Scope.project(projectId);
            }
        };
        return roleMapper.selectList(inScope(scope).eq(Role::getStatus, Status.ENABLED).orderByAsc(Role::getId))
                .stream().map(RoleResponse::valueOf).toList();
    }

    /** 从模板导入到项目，已有同名或同编码的角色跳过；返回导入的数量 */
    @Transactional
    public int importTemplates(String authorization, Long projectId, List<Long> templateIds) {
        requireAccess(authorization, Scope.project(projectId), ":import");
        return copyTemplates(projectId, templateIds);
    }

    /**
     * 新建项目时调用：生成拥有全部启用项目菜单的「项目管理员」，再复制勾选的模板。调用方负责权限校验。
     */
    @Transactional
    public void initializeProject(long projectId, List<Long> templateIds) {
        long now = System.currentTimeMillis();
        Role admin = new Role();
        admin.setRoleType(Role.TYPE_PROJECT);
        admin.setProjectId(projectId);
        admin.setName("项目管理员");
        admin.setCode(Role.PROJECT_ADMIN_CODE);
        admin.setDescription("项目级管理角色");
        admin.setStatus(Status.ENABLED);
        admin.setBuiltIn(false);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        roleMapper.insert(admin);
        replaceMenus(admin.getId(), menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getScope, Menu.SCOPE_PROJECT)
                .eq(Menu::getStatus, Status.ENABLED)).stream().map(Menu::getId).collect(Collectors.toSet()));
        copyTemplates(projectId, templateIds == null ? List.of() : templateIds);
    }

    private int copyTemplates(Long projectId, List<Long> templateIds) {
        if (templateIds == null || templateIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (templateIds.isEmpty()) {
            return 0;
        }
        Map<Long, Role> templates = roleMapper.selectList(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_TEMPLATE)
                .in(Role::getId, templateIds)).stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        if (templates.size() != new HashSet<>(templateIds).size()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        List<Role> existing = roleMapper.selectList(inScope(Scope.project(projectId)));
        Set<Long> projectMenus = menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getScope, Menu.SCOPE_PROJECT))
                .stream().map(Menu::getId).collect(Collectors.toSet());
        int imported = 0;
        long now = System.currentTimeMillis();
        for (Role template : templates.values()) {
            boolean duplicate = existing.stream().anyMatch(role -> role.getName().equals(template.getName()) || role.getCode().equals(template.getCode()));
            if (duplicate) {
                continue;
            }
            Role role = new Role();
            role.setRoleType(Role.TYPE_PROJECT);
            role.setProjectId(projectId);
            role.setTemplateId(template.getId());
            role.setName(template.getName());
            role.setCode(template.getCode());
            role.setDescription(template.getDescription());
            role.setStatus(template.getStatus());
            role.setBuiltIn(false);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            roleMapper.insert(role);
            replaceMenus(role.getId(), roleMenuMapper.listMenuIds(List.of(template.getId())).stream()
                    .filter(projectMenus::contains).collect(Collectors.toSet()));
            existing.add(role);
            imported++;
        }
        return imported;
    }

    private void replaceMenus(Long roleId, Set<Long> menuIds) {
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId));
        if (!menuIds.isEmpty()) {
            roleMenuMapper.insertBatch(roleId, menuIds);
        }
    }

    /** action 为空时是查看，只校验页面；写操作校验页面下的按钮，例如 manage_role:create */
    private void requireAccess(String authorization, Scope scope, String action) {
        switch (scope.roleType()) {
            case Role.TYPE_PLATFORM -> permissionService.requirePlatformMenu(authorization, "manage_role" + action);
            case Role.TYPE_TEMPLATE -> permissionService.requirePlatformMenu(authorization, "manage_role_template" + action);
            default -> permissionService.requireProjectMenu(authorization, scope.projectId(), "project_role" + action);
        }
    }

    private Role requireRole(Scope scope, Long roleId) {
        Role role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null || !Objects.equals(role.getRoleType(), scope.roleType()) || !Objects.equals(role.getProjectId(), scope.projectId())) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private Role requireEditableRole(Scope scope, Long roleId) {
        Role role = requireRole(scope, roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_FORBIDDEN);
        }
        return role;
    }

    private LambdaQueryWrapper<Role> inScope(Scope scope) {
        return Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, scope.roleType())
                .eq(scope.projectId() != null, Role::getProjectId, scope.projectId())
                .isNull(scope.projectId() == null, Role::getProjectId);
    }

    private void requireUnique(Scope scope, Long excludeId, RoleValues values) {
        if (roleMapper.selectCount(inScope(scope).eq(Role::getName, values.name()).ne(excludeId != null, Role::getId, excludeId)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_NAME_EXISTS);
        }
        if (roleMapper.selectCount(inScope(scope).eq(Role::getCode, values.code()).ne(excludeId != null, Role::getId, excludeId)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }
    }

    private RoleValues validate(String name, String code, String description, Integer status) {
        String roleName = name == null ? "" : name.trim();
        String roleCode = code == null ? "" : code.trim();
        String roleDescription = description == null ? "" : description.trim();
        if (roleName.isBlank() || roleName.length() > NAME_MAX_LENGTH || !CODE_PATTERN.matcher(roleCode).matches()
                || roleDescription.length() > DESCRIPTION_MAX_LENGTH || !Status.valid(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new RoleValues(roleName, roleCode, roleDescription);
    }

    private record RoleValues(String name, String code, String description) {
    }
}
