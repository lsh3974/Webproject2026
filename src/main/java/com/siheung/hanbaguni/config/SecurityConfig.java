package com.siheung.hanbaguni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import com.siheung.hanbaguni.security.RoleBasedLoginSuccessHandler;
import com.siheung.hanbaguni.notification.NotificationPushSubscriptionService;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RoleBasedLoginSuccessHandler loginSuccessHandler,
                                                   NotificationPushSubscriptionService pushSubscriptions)
            throws Exception {

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/signup",
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/service-worker.js",
                                "/images/**",
                                "/uploads/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/group-purchases",
                                "/group-purchases/{id}"
                        ).permitAll()
                        .requestMatchers("/seller/**").hasRole("SELLER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getPrincipal() instanceof com.siheung.hanbaguni.security.LoginUser user) {
                                // 브라우저가 로그아웃 요청에 현재 endpoint를 전달한 경우에만 해당 기기 구독을 해제한다.
                                pushSubscriptions.deactivateForUserAndEndpoint(
                                        user.getUserId(), request.getParameter("pushEndpoint"));
                            }
                        })
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
