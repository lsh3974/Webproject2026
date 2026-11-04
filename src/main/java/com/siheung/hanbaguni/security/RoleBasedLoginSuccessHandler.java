package com.siheung.hanbaguni.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class RoleBasedLoginSuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    public RoleBasedLoginSuccessHandler() {
        // 로그인 전 방문 주소보다 권한별 시작 화면을 우선합니다.
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    protected String determineTargetUrl(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        boolean isSeller = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_SELLER"));

        return isSeller ? "/seller/stores" : "/";
    }
}