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
                                pushSubscriptions.deactivateAllForUser(user.getUserId());
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
