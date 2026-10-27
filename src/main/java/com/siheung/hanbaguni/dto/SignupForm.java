package com.siheung.hanbaguni.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class SignupForm {

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식으로 입력해주세요.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 72,
            message = "비밀번호는 8자 이상, 72자 이하로 입력해주세요.")
    private String password;

    @NotBlank(message = "표시 이름을 입력해주세요.")
    @Size(max = 60, message = "표시 이름은 60자 이하여야 합니다.")
    private String displayName;

    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithinByteLimit() {
        if (password == null) {
            return true;
        }

        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}