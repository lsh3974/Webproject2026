package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.OrderReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final OrderReservationService orderReservationService;

    public HomeController(OrderReservationService orderReservationService) {
        this.orderReservationService = orderReservationService;
    }

    @GetMapping("/")
    public String home(
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Long userId = (loginUser != null) ? loginUser.getUserId() : null;
        model.addAttribute("openGroupPurchases", orderReservationService.getOpenGroupPurchaseSummaries(userId));
        return "index";
    }
}
