package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Pickup;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.UserRepository;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.GroupPurchaseService;
import com.siheung.hanbaguni.service.PickupService;
import com.siheung.hanbaguni.service.StoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/seller/stores/{storeId}/pickups")
public class SellerPickupController {

    private final PickupService pickupService;
    private final StoreService storeService;
    private final GroupPurchaseService groupPurchaseService;
    private final UserRepository userRepository;

    public SellerPickupController(
            PickupService pickupService,
            StoreService storeService,
            GroupPurchaseService groupPurchaseService,
            UserRepository userRepository) {
        this.pickupService = pickupService;
        this.storeService = storeService;
        this.groupPurchaseService = groupPurchaseService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listPickups(
            @PathVariable Long storeId,
            @RequestParam(name = "searchCode", required = false) String searchCode,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        List<Pickup> pickups = pickupService.findByStoreId(storeId, loginUser.getUserId());
        List<GroupPurchase> groupPurchases = groupPurchaseService.findByStoreId(storeId);

        Pickup searchedPickup = null;
        if (searchCode != null && !searchCode.isBlank()) {
            try {
                searchedPickup = pickupService.getByCodeAndStore(searchCode, storeId, loginUser.getUserId());
            } catch (Exception e) {
                model.addAttribute("searchError", e.getMessage());
            }
        }

        model.addAttribute("store", store);
        model.addAttribute("pickups", pickups);
        model.addAttribute("groupPurchases", groupPurchases);
        model.addAttribute("searchCode", searchCode);
        model.addAttribute("searchedPickup", searchedPickup);
        return "seller/pickup/index";
    }

    @GetMapping("/api/lookup")
    @ResponseBody
    public ResponseEntity<?> lookupApi(
            @PathVariable Long storeId,
            @RequestParam("code") String code,
            @AuthenticationPrincipal LoginUser loginUser) {

        try {
            Pickup p = pickupService.getByCodeAndStore(code, storeId, loginUser.getUserId());
            Map<String, Object> data = new HashMap<>();
            data.put("id", p.getId());
            data.put("pickupCode", p.getPickupCode());
            data.put("productName", p.getGroupPurchase().getProduct().getName());
            data.put("groupTitle", p.getGroupPurchase().getTitle());
            data.put("quantity", p.getQuantity());
            data.put("totalPrice", p.getGroupPurchase().getGroupPrice() * p.getQuantity());
            data.put("customerName", p.getCustomerName() != null ? p.getCustomerName() : p.getCustomer().getDisplayName());
            data.put("customerPhone", p.getCustomerPhone());
            data.put("status", p.getStatus().name());
            data.put("pickedUpAt", p.getPickedUpAt() != null ? p.getPickedUpAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{pickupId}/ready")
    public String markReady(
            @PathVariable Long storeId,
            @PathVariable Long pickupId,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        try {
            pickupService.markReady(pickupId, loginUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "상품이 [준비완료] 상태로 변경되었습니다.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getReason());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/stores/" + storeId + "/pickups";
    }

    @PostMapping("/{pickupId}/complete")
    public String markPickedUp(
            @PathVariable Long storeId,
            @PathVariable Long pickupId,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        try {
            pickupService.markPickedUp(pickupId, loginUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "수령 완료 처리가 완료되었습니다.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getReason());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/stores/" + storeId + "/pickups";
    }

    @PostMapping("/scan-complete")
    public String markPickedUpByScan(
            @PathVariable Long storeId,
            @RequestParam("pickupCode") String pickupCode,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        try {
            Pickup p = pickupService.markPickedUpByCode(pickupCode, storeId, loginUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "[" + p.getPickupCode() + "] " + p.getGroupPurchase().getProduct().getName() + " " + p.getQuantity() + "개 수령 완료 처리되었습니다!");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "수령 처리 실패: " + e.getReason());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "수령 처리 실패: " + e.getMessage());
        }
        return "redirect:/seller/stores/" + storeId + "/pickups";
    }

    @PostMapping("/mock-create")
    public String createMockPickup(
            @PathVariable Long storeId,
            @RequestParam("groupPurchaseId") Long groupPurchaseId,
            @RequestParam(name = "quantity", defaultValue = "1") int quantity,
            @RequestParam(name = "customerName", defaultValue = "홍길동") String customerName,
            @RequestParam(name = "customerPhone", defaultValue = "010-9876-5432") String customerPhone,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        GroupPurchase gp = groupPurchaseService.getWithOwnershipCheck(groupPurchaseId, loginUser.getUserId());
        User currentUser = userRepository.findById(loginUser.getUserId())
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));

        Pickup created = pickupService.createTestPickup(store, gp, currentUser, quantity, customerName, customerPhone);
        redirectAttributes.addFlashAttribute("successMessage",
                "테스트 수령 건이 생성되었습니다! 수령 코드: [" + created.getPickupCode() + "]");
        return "redirect:/seller/stores/" + storeId + "/pickups?searchCode=" + created.getPickupCode();
    }
}
