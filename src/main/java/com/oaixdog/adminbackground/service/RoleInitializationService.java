package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.RoleMenu;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.mapper.MenuMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.mapper.RoleMenuMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时确保内置超级管理员角色存在，并拥有全部平台菜单。项目菜单由权限判断直接放行，不写入关联。
 */
@Service
@RequiredArgsConstructor
public class RoleInitializationService {

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @PostConstruct
    @Transactional
    public void ensureSuperAdmin() {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getRoleType, Role.TYPE_PLATFORM)
                .eq(Role::getCode, Role.SUPER_ADMIN_CODE));
        if (role == null) {
            long now = System.currentTimeMillis();
            role = new Role();
            role.setRoleType(Role.TYPE_PLATFORM);
            role.setName("超级管理员");
            role.setCode(Role.SUPER_ADMIN_CODE);
            role.setDescription("系统最高权限角色，可进入全部项目");
            role.setStatus(Status.ENABLED);
            role.setBuiltIn(true);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            roleMapper.insert(role);
        }
        Set<Long> assigned = new HashSet<>(roleMenuMapper.listMenuIds(List.of(role.getId())));
        for (Menu menu : menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getScope, Menu.SCOPE_PLATFORM))) {
            if (!assigned.contains(menu.getId())) {
                RoleMenu roleMenu = new RoleMenu();
                roleMenu.setRoleId(role.getId());
                roleMenu.setMenuId(menu.getId());
                roleMenuMapper.insert(roleMenu);
            }
        }
    }
}
