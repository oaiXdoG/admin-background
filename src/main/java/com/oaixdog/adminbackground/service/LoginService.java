package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final String TOKEN_KEY_PREFIX = "login:token:";

    private final AccountMapper accountMapper;
    private final RedisService redisService;

    public LoginResult login(String account, String password) {
        String loginAccount = account == null ? "" : account.trim();
        if (StringUtils.isBlank(loginAccount) || loginAccount.length() > 64 || StringUtils.isBlank(password) || password.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Account found = accountMapper.selectOne(Wrappers.<Account>lambdaQuery().eq(Account::getAccount, loginAccount));
        if (found == null || !password.equals(found.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisService.set(TOKEN_KEY_PREFIX + token, String.valueOf(found.getId()), TOKEN_TTL);
        return new LoginResult(token, found.getId(), found.getAccount(), found.getUsername());
    }

    public Long findAccountId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        String accountId = redisService.get(TOKEN_KEY_PREFIX + token);
        if (StringUtils.isBlank(accountId)) {
            return null;
        }
        try {
            return Long.valueOf(accountId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record LoginResult(String token, long id, String account, String username) {
    }
}
