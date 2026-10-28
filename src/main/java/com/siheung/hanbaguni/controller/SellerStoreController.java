package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.StoreForm;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/seller/stores")
public class SellerStoreController {

    private final StoreService storeService;

    public SellerStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public String listStores(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        List<Store> stores = storeService.findStoresBySellerId(loginUser.getUserId());
        model.addAttribute("stores", stores);
        return "seller/store/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("storeForm", new StoreForm());
        return "seller/store/new";
    }

    @PostMapping("/new")
    public String createStore(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("storeForm") StoreForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "seller/store/new";
        }

        Long storeId = storeService.createStore(loginUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "매장이 성공적으로 등록되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/products";
    }

    @GetMapping("/{storeId}/edit")
    public String showEditForm(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        model.addAttribute("store", store);
        model.addAttribute("storeForm", StoreForm.from(store));
        return "seller/store/edit";
    }

    @PostMapping("/{storeId}/edit")
    public String updateStore(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("storeForm") StoreForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
            model.addAttribute("store", store);
            return "seller/store/edit";
        }

        storeService.updateStore(storeId, loginUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "매장 정보가 수정되었습니다.");
        return "redirect:/seller/stores";
    }
}
