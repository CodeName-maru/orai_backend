package com.ovengers.common.auth;

import lombok.*;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUserInfo {
    private String id;
    private String departmentId;
    private String role;

    public TokenUserInfo(String id, String departmentId) {
        this.id = id;
        this.departmentId = departmentId;
    }
}
