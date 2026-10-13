package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.Account;
import com.oaixdog.adminbackground.mapper.AccountMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 一次性迁移：permission-migration.sql 只把明文密码列改名为 password_hash，启动时把不是 bcrypt 的值转成哈希。
 * 全部账号转换完成后可以删除本类。
 */
@Service
@RequiredArgsConstructor
public class PasswordUpgradeService {

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void upgradePlainPasswords() {
        for (Account account : accountMapper.selectList(Wrappers.<Account>lambdaQuery().notLikeRight(Account::getPasswordHash, "$2"))) {
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate().eq(Account::getId, account.getId())
                    .set(Account::getPasswordHash, passwordEncoder.encode(account.getPasswordHash())));
        }
    }
}
