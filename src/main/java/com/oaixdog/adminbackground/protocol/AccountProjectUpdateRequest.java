package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Protocol(id = 3022)
public class AccountProjectUpdateRequest {

    private Long accountId;
    private List<AccountMembership> memberships;
}
