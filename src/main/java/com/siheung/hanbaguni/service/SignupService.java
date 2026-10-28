package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.siheung.hanbaguni.exception.DuplicateSignupException;

import java.util.Locale;

/**
 * 이메일은 앞뒤 공백 제거·소문자 통일 후 저장하며, 로그인에서도 같은 규칙을 적용해야 합니다.
 */
@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void signup(String email, String password, String displayName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = displayName.trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateSignupException(
                    "email",
                    "이미 가입된 이메일입니다."
            );
        }

        if (userRepository.existsByDisplayName(normalizedDisplayName)) {
            throw new DuplicateSignupException(
                    "displayName",
                    "이미 사용 중인 닉네임입니다."
            );
        }

        String passwordHash = passwordEncoder.encode(password);

        User user = new User(
                normalizedEmail,
                passwordHash,
                normalizedDisplayName
        );

        userRepository.save(user);
    }
}