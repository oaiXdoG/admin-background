package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.oaixdog.adminbackground.protocol.AccountCreateResponse;
import com.oaixdog.adminbackground.protocol.AccountResponse;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountCommandService {

    private static final int ACCOUNT_MAX_LENGTH = 64;
    private static final int PASSWORD_MAX_LENGTH = 128;
    private static final int USERNAME_MAX_LENGTH = 64;

    private final AccountMapper accountMapper;

    public AccountCreateResponse create(String authorization, String account, String password, String username) {
        permissionService.requireMenu(authorization, "manage_user");
        String loginAccount = account == null ? "" : account.trim();
        String displayName = username == null ? "" : username.trim();
        if (StringUtils.isBlank(loginAccount) || loginAccount.length() > ACCOUNT_MAX_LENGTH
                || StringUtils.isBlank(password) || password.length() > PASSWORD_MAX_LENGTH
                || StringUtils.isBlank(displayName) || displayName.length() > USERNAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long existing = accountMapper.selectCount(Wrappers.<Account>lambdaQuery().eq(Account::getAccount, loginAccount));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXISTS);
        }
        Account created = new Account();
        created.setAccount(loginAccount);
        created.setPassword(password);
        created.setUsername(displayName);
        created.setAdmin(false);
        accountMapper.insert(created);
        return AccountCreateResponse.valueOf(created.getId(), created.getAccount(), created.getUsername());
    }

    public List<AccountResponse> list(String authorization) {
        permissionService.requireMenu(authorization, "manage_user");
        return accountMapper.selectList(null).stream().map(AccountResponse::valueOf).toList();
    }

    private final PermissionService permissionService;

}
