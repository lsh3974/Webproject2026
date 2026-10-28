package com.siheung.hanbaguni.security;

import com.siheung.hanbaguni.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class LoginUser
        extends org.springframework.security.core.userdetails.User {

    private final Long userId;
    private final String displayName;

    public LoginUser(User user) {
        super(
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus() == User.Status.ACTIVE,
                true,
                true,
                user.getStatus() != User.Status.SUSPENDED,
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name()
                ))
        );

        this.userId = user.getId();
        this.displayName = user.getDisplayName();
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }
}