package com.civic_connect.backend.user.dto;

import com.civic_connect.backend.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private String username;
    private Long id;
    private String provider;
    private String email;
    private Role role;
}
