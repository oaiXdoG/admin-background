package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3023)
public class AccountMembership {

    private Long projectId;
    private Long roleId;

    public static AccountMembership valueOf(Long projectId, Long roleId) {
        AccountMembership membership = new AccountMembership();
        membership.projectId = projectId;
        membership.roleId = roleId;
        return membership;
    }
}
