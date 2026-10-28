package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.GroupPurchaseService;
import com.siheung.hanbaguni.service.ProductService;
import com.siheung.hanbaguni.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/seller/stores/{storeId}/group-purchases")
public class SellerGroupPurchaseController {

    private final GroupPurchaseService groupPurchaseService;
    private final StoreService storeService;
    private final ProductService productService;

    public SellerGroupPurchaseController(
            GroupPurchaseService groupPurchaseService,
            StoreService storeService,
            ProductService productService) {
        this.groupPurchaseService = groupPurchaseService;
        this.storeService = storeService;
        this.productService = productService;
    }

    @GetMapping
    public String listGroupPurchases(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        List<GroupPurchase> groupPurchases = groupPurchaseService.findByStoreId(storeId);

        model.addAttribute("store", store);
        model.addAttribute("groupPurchases", groupPurchases);
        return "seller/grouppurchase/list";
    }

    @GetMapping("/new")
    public String showCreateForm(
            @PathVariable Long storeId,
            @RequestParam(name = "productId", required = false) Long productId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        List<Product> products = productService.findProductsByStoreId(storeId);

        GroupPurchaseForm form = new GroupPurchaseForm();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        form.setStartAt(now);
        form.setEndAt(now.plusDays(3));
        form.setPickupStartAt(now.plusDays(4));
        form.setPickupEndAt(now.plusDays(5));

        if (productId != null) {
            form.setProductId(productId);
            products.stream()
                    .filter(p -> p.getId().equals(productId))
                    .findFirst()
                    .ifPresent(selected -> {
                        form.setTitle("[공동구매] " + selected.getName());
                        form.setGroupPrice((int) (selected.getRegularPrice() * 0.85));
                    });
        }

        model.addAttribute("store", store);
        model.addAttribute("products", products);
        model.addAttribute("groupPurchaseForm", form);
        return "seller/grouppurchase/new";
    }

    @PostMapping("/new")
    public String createGroupPurchase(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("groupPurchaseForm") GroupPurchaseForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());

        if (bindingResult.hasErrors()) {
            List<Product> products = productService.findProductsByStoreId(storeId);
            model.addAttribute("store", store);
            model.addAttribute("products", products);
            return "seller/grouppurchase/new";
        }

        groupPurchaseService.createGroupPurchase(storeId, loginUser.getUserId(), form);

        String msg = form.getStatus() == GroupPurchase.Status.OPEN
                ? "공동구매 모집이 정식 게시되었습니다."
                : "공동구매 모집이 초안(임시저장)으로 저장되었습니다.";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/seller/stores/" + storeId + "/group-purchases";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable Long storeId,
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        GroupPurchase gp = groupPurchaseService.getWithOwnershipCheck(id, loginUser.getUserId());
        List<Product> products = productService.findProductsByStoreId(storeId);

        model.addAttribute("store", store);
        model.addAttribute("groupPurchase", gp);
        model.addAttribute("products", products);
        model.addAttribute("groupPurchaseForm", GroupPurchaseForm.from(gp));
        return "seller/grouppurchase/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateGroupPurchase(
            @PathVariable Long storeId,
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("groupPurchaseForm") GroupPurchaseForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());

        if (bindingResult.hasErrors()) {
            GroupPurchase gp = groupPurchaseService.getWithOwnershipCheck(id, loginUser.getUserId());
            List<Product> products = productService.findProductsByStoreId(storeId);
            model.addAttribute("store", store);
            model.addAttribute("groupPurchase", gp);
            model.addAttribute("products", products);
            return "seller/grouppurchase/edit";
        }

        groupPurchaseService.updateGroupPurchase(id, loginUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "공동구매 모집 정보가 수정되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/group-purchases";
    }

    @PostMapping("/{id}/publish")
    public String publishGroupPurchase(
            @PathVariable Long storeId,
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        groupPurchaseService.publishGroupPurchase(id, loginUser.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "공동구매 모집이 공개 게시(OPEN)되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/group-purchases";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable Long storeId,
            @PathVariable Long id,
            @RequestParam("status") GroupPurchase.Status status,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        groupPurchaseService.updateStatus(id, loginUser.getUserId(), status);
        redirectAttributes.addFlashAttribute("successMessage", "공동구매 모집 상태가 변경되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/group-purchases";
    }

    @PostMapping("/{id}/delete")
    public String deleteGroupPurchase(
            @PathVariable Long storeId,
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        groupPurchaseService.deleteGroupPurchase(id, loginUser.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "공동구매 모집이 삭제되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/group-purchases";
    }
}
