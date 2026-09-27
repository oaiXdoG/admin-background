package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.AccountRole;
import com.oaixdog.adminbackground.entity.Menu;
import com.oaixdog.adminbackground.entity.Role;
import com.oaixdog.adminbackground.entity.RoleMenu;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.AccountRoleMapper;
import com.oaixdog.adminbackground.mapper.MenuMapper;
import com.oaixdog.adminbackground.mapper.RoleMapper;
import com.oaixdog.adminbackground.mapper.RoleMenuMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleInitializationService {

    private static final String SUPER_ADMIN_ROLE = "超级管理员";

    private final AccountMapper accountMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @PostConstruct
    @Transactional
    public void migrateLegacyAdministrators() {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getName, SUPER_ADMIN_ROLE));
        if (role == null) {
            role = new Role();
            role.setName(SUPER_ADMIN_ROLE);
            role.setGlobalRole(true);
            role.setBuiltIn(true);
            roleMapper.insert(role);
        }
        for (Menu menu : menuMapper.selectList(null)) {
            Long count = roleMenuMapper.selectCount(Wrappers.<RoleMenu>lambdaQuery()
                    .eq(RoleMenu::getRoleId, role.getId())
                    .eq(RoleMenu::getMenuId, menu.getId()));
            if (count == null || count == 0) {
                RoleMenu roleMenu = new RoleMenu();
                roleMenu.setRoleId(role.getId());
                roleMenu.setMenuId(menu.getId());
                roleMenuMapper.insert(roleMenu);
            }
        }
        for (Account account : accountMapper.selectList(Wrappers.<Account>lambdaQuery().eq(Account::getAdmin, true))) {
            Long count = accountRoleMapper.selectCount(Wrappers.<AccountRole>lambdaQuery()
                    .eq(AccountRole::getAccountId, account.getId())
                    .eq(AccountRole::getRoleId, role.getId()));
            if (count == null || count == 0) {
                AccountRole accountRole = new AccountRole();
                accountRole.setAccountId(account.getId());
                accountRole.setRoleId(role.getId());
                accountRoleMapper.insert(accountRole);
            }
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                    .eq(Account::getId, account.getId())
                    .set(Account::getAdmin, false));
        }
    }
}
