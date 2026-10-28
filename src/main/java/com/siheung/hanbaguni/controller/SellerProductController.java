package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.ProductForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.FileStorageService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/seller/stores/{storeId}/products")
public class SellerProductController {

    private final ProductService productService;
    private final StoreService storeService;
    private final FileStorageService fileStorageService;
    private final GroupPurchaseService groupPurchaseService;

    public SellerProductController(
            ProductService productService,
            StoreService storeService,
            FileStorageService fileStorageService,
            GroupPurchaseService groupPurchaseService) {
        this.productService = productService;
        this.storeService = storeService;
        this.fileStorageService = fileStorageService;
        this.groupPurchaseService = groupPurchaseService;
    }

    @GetMapping
    public String listProducts(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        List<Product> products = productService.findProductsByStoreId(storeId);
        List<GroupPurchase> groupPurchases = groupPurchaseService.findByStoreId(storeId);

        // 상품별 공동구매 매핑 (진행중/초안 우선 매핑)
        Map<Long, GroupPurchase> groupPurchaseByProductId = new HashMap<>();
        for (GroupPurchase gp : groupPurchases) {
            Long productId = gp.getProduct().getId();
            GroupPurchase existing = groupPurchaseByProductId.get(productId);
            if (existing == null) {
                groupPurchaseByProductId.put(productId, gp);
            } else if (existing.getStatus() == GroupPurchase.Status.CLOSED
                    && (gp.getStatus() == GroupPurchase.Status.OPEN || gp.getStatus() == GroupPurchase.Status.DRAFT)) {
                groupPurchaseByProductId.put(productId, gp);
            }
        }

        model.addAttribute("store", store);
        model.addAttribute("products", products);
        model.addAttribute("groupPurchaseMap", groupPurchaseByProductId);
        return "seller/product/list";
    }

    @GetMapping("/new")
    public String showCreateForm(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        model.addAttribute("store", store);
        model.addAttribute("productForm", new ProductForm());
        return "seller/product/new";
    }

    @PostMapping("/new")
    public String createProduct(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("store", store);
            return "seller/product/new";
        }

        // 유효성 통과 및 권한 확인 후에만 파일 저장 실행
        if (form.getImageFile() != null && !form.getImageFile().isEmpty()) {
            String uploadedPath = fileStorageService.store(form.getImageFile());
            form.setImageUrl(uploadedPath);
        }

        productService.createProduct(storeId, loginUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "상품이 성공적으로 등록되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/products";
    }

    @GetMapping("/{productId}/edit")
    public String showEditForm(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        Product product = productService.getProductWithStoreAndOwnershipCheck(storeId, productId, loginUser.getUserId());

        model.addAttribute("store", store);
        model.addAttribute("product", product);
        model.addAttribute("productForm", ProductForm.from(product));
        return "seller/product/edit";
    }

    @PostMapping("/{productId}/edit")
    public String updateProduct(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        // 1. 매장 소유권 및 상품의 매장 일치/소유권 검사를 가장 먼저 실행 (인가 실패 시 파일 저장 방지)
        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        Product product = productService.getProductWithStoreAndOwnershipCheck(storeId, productId, loginUser.getUserId());

        // 2. 폼 유효성 검사 오류 시 리턴
        if (bindingResult.hasErrors()) {
            model.addAttribute("store", store);
            model.addAttribute("product", product);
            return "seller/product/edit";
        }

        // 3. 모든 권한과 유효성 검증을 통과한 후에만 새 파일 업로드 저장 수행
        if (form.getImageFile() != null && !form.getImageFile().isEmpty()) {
            String uploadedPath = fileStorageService.store(form.getImageFile());
            form.setImageUrl(uploadedPath);
        }

        productService.updateProduct(storeId, productId, loginUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "상품 정보가 수정되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/products";
    }

    @PostMapping("/{productId}/status")
    public String updateStatus(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @RequestParam("status") Product.Status status,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        productService.updateProductStatus(storeId, productId, loginUser.getUserId(), status);
        redirectAttributes.addFlashAttribute("successMessage", "상품 판매 상태가 변경되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/products";
    }

    @PostMapping("/{productId}/delete")
    public String deleteProduct(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        productService.deleteProduct(storeId, productId, loginUser.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "상품이 삭제되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/products";
    }
}
