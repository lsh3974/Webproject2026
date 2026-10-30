package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.service.GroupPurchaseQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GroupPurchaseBrowseController {
    private final GroupPurchaseQueryService queryService;

    public GroupPurchaseBrowseController(GroupPurchaseQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/group-purchases")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String district,
                       @RequestParam(defaultValue = "") String category,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        q = GroupPurchaseQueryService.normalize(q);
        district = GroupPurchaseQueryService.normalize(district);
        category = GroupPurchaseQueryService.normalize(category);
        model.addAttribute("purchases", queryService.search(q, district, category, page));
        model.addAttribute("q", q);
        model.addAttribute("district", district);
        model.addAttribute("category", category);
        return "group-purchases/list";
    }

    @GetMapping("/group-purchases/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("purchase", queryService.detail(id));
        return "group-purchases/detail";
    }
}
