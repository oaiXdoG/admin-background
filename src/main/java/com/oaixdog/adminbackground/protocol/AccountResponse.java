package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.Account;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountResponse {

    private long id;
    private String account;
    private String username;

    public static AccountResponse valueOf(Account account) {
        AccountResponse response = new AccountResponse();
        response.id = account.getId();
        response.account = account.getAccount();
        response.username = account.getUsername();
        return response;
    }
}
