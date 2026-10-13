package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.entity.Status;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录会话：令牌对应账号，会话里保存当前项目；同一账号的令牌集中记录，禁用或改密码时一起失效。
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final String TOKEN_KEY_PREFIX = "login:token:";
    private static final String PROJECT_KEY_PREFIX = "login:project:";
    private static final String ACCOUNT_TOKENS_KEY_PREFIX = "login:account:";

    private final AccountMapper accountMapper;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;

    public LoginResult login(String account, String password) {
        String loginAccount = account == null ? "" : account.trim();
        if (StringUtils.isBlank(loginAccount) || loginAccount.length() > 64 || StringUtils.isBlank(password) || password.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Account found = accountMapper.selectOne(Wrappers.<Account>lambdaQuery().eq(Account::getAccount, loginAccount));
        if (found == null || !passwordEncoder.matches(password, found.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (found.getStatus() == null || found.getStatus() != Status.ENABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisService.set(TOKEN_KEY_PREFIX + token, String.valueOf(found.getId()), TOKEN_TTL);
        redisService.addToSet(ACCOUNT_TOKENS_KEY_PREFIX + found.getId(), token, TOKEN_TTL);
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getId, found.getId())
                .set(Account::getLastLoginAt, System.currentTimeMillis()));
        return new LoginResult(token, found.getId(), found.getAccount(), found.getUsername());
    }

    public void logout(String authorization) {
        String token = token(authorization);
        if (token == null) {
            return;
        }
        Long accountId = findAccountId(authorization);
        redisService.delete(TOKEN_KEY_PREFIX + token);
        redisService.delete(PROJECT_KEY_PREFIX + token);
        if (accountId != null) {
            redisService.removeFromSet(ACCOUNT_TOKENS_KEY_PREFIX + accountId, token);
        }
    }

    /** 让账号的全部会话失效，用于禁用账号和修改密码 */
    public void invalidateAccount(long accountId) {
        String key = ACCOUNT_TOKENS_KEY_PREFIX + accountId;
        for (String token : redisService.members(key)) {
            redisService.delete(TOKEN_KEY_PREFIX + token);
            redisService.delete(PROJECT_KEY_PREFIX + token);
        }
        redisService.delete(key);
    }

    /** 让账号除当前会话以外的会话失效，用于本人修改密码 */
    public void invalidateOtherSessions(long accountId, String authorization) {
        String current = token(authorization);
        String key = ACCOUNT_TOKENS_KEY_PREFIX + accountId;
        for (String token : redisService.members(key)) {
            if (token.equals(current)) {
                continue;
            }
            redisService.delete(TOKEN_KEY_PREFIX + token);
            redisService.delete(PROJECT_KEY_PREFIX + token);
            redisService.removeFromSet(key, token);
        }
    }

    public Long findAccountId(String authorization) {
        String token = token(authorization);
        if (token == null) {
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

    public Long findSessionProjectId(String authorization) {
        String token = token(authorization);
        String projectId = token == null ? null : redisService.get(PROJECT_KEY_PREFIX + token);
        if (StringUtils.isBlank(projectId)) {
            return null;
        }
        try {
            return Long.valueOf(projectId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void setSessionProjectId(String authorization, Long projectId) {
        String token = token(authorization);
        if (token == null) {
            return;
        }
        if (projectId == null) {
            redisService.delete(PROJECT_KEY_PREFIX + token);
            return;
        }
        long seconds = redisService.getExpireSeconds(TOKEN_KEY_PREFIX + token);
        Duration ttl = seconds > 0 ? Duration.ofSeconds(seconds) : TOKEN_TTL;
        redisService.set(PROJECT_KEY_PREFIX + token, String.valueOf(projectId), ttl);
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    public record LoginResult(String token, long id, String account, String username) {
    }
}
