package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.SignupForm;
import com.siheung.hanbaguni.service.SignupService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import com.siheung.hanbaguni.exception.DuplicateSignupException;

@Controller
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(
            @Valid @ModelAttribute("signupForm") SignupForm form,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return "signup";
        }

        try {
            signupService.signup(
                    form.getEmail(),
                    form.getPassword(),
                    form.getDisplayName()
            );
        } catch (DuplicateSignupException e) {
            bindingResult.rejectValue(
                    e.getField(),
                    "signup.duplicate",
                    e.getMessage()
            );
            return "signup";
        }catch (DataIntegrityViolationException e) {
            bindingResult.reject(
                    "signup.conflict",
                    "회원 정보를 저장하지 못했습니다. 이메일·닉네임 중복 여부와 입력값을 확인해주세요."
            );
            return "signup";
        }

        return "redirect:/signup?success";
    }
}