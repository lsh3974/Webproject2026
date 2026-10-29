package com.siheung.hanbaguni.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GroupPurchaseBrowseController {

    @GetMapping("/group-purchases")
    public String list() {
        return "group-purchases/list";
    }
}