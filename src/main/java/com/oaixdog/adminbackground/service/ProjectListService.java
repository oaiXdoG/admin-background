package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.mapper.AccountProjectMapper;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.protocol.ProjectListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectListService {

    private final LoginService loginService;
    private final AccountMapper accountMapper;
    private final AccountProjectMapper accountProjectMapper;
    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;

    public ProjectListResponse list(String authorization) {
        Long accountId = loginService.findAccountId(authorization);
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        List<ProjectListResponse.ProjectItem> projects = new ArrayList<>();
        Long currentProjectId = null;
        if (permissionService.hasGlobalRole(account)) {
            projectMapper.selectList(null).forEach(project -> {
                ProjectListResponse.ProjectItem item = new ProjectListResponse.ProjectItem();
                item.setProjectId(project.getId());
                item.setName(project.getName());
                item.setRoleName("超级管理员");
                projects.add(item);
            });
            currentProjectId = projects.stream().map(ProjectListResponse.ProjectItem::getProjectId)
                    .filter(id -> sameId(id, account.getLastProjectId())).findFirst().orElse(null);
        } else {
            List<AccountProjectMapper.AccountProjectRow> rows = accountProjectMapper.listByAccountId(accountId);
            for (AccountProjectMapper.AccountProjectRow row : rows) {
                ProjectListResponse.ProjectItem item = new ProjectListResponse.ProjectItem();
                item.setProjectId(row.getProjectId());
                item.setName(row.getName());
                item.setRoleId(row.getRoleId());
                item.setRoleName(row.getRoleName());
                projects.add(item);
                if (row.getProjectId().equals(account.getLastProjectId())) {
                    currentProjectId = row.getProjectId();
                }
            }
        }
        if (currentProjectId == null && !projects.isEmpty()) {
            currentProjectId = projects.get(0).getProjectId();
        }
        if (!sameId(account.getLastProjectId(), currentProjectId)) {
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                    .eq(Account::getId, accountId)
                    .set(Account::getLastProjectId, currentProjectId));
        }
        return ProjectListResponse.valueOf(currentProjectId, projects);
    }

    private static boolean sameId(Long left, Long right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
