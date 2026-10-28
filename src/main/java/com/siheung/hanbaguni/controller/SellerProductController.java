package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.ProductForm;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.FileStorageService;
import com.siheung.hanbaguni.service.ProductService;
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
@RequestMapping("/seller/stores/{storeId}/products")
public class SellerProductController {

    private final ProductService productService;
    private final StoreService storeService;
    private final FileStorageService fileStorageService;

    public SellerProductController(
            ProductService productService,
            StoreService storeService,
            FileStorageService fileStorageService) {
        this.productService = productService;
        this.storeService = storeService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String listProducts(
            @PathVariable Long storeId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());
        List<Product> products = productService.findProductsByStoreId(storeId);

        model.addAttribute("store", store);
        model.addAttribute("products", products);
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

        // 파일 업로드가 있으면 파일 경로 우선 적용
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
        Product product = productService.getProductWithOwnershipCheck(productId, loginUser.getUserId());

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

        Store store = storeService.getStoreWithOwnershipCheck(storeId, loginUser.getUserId());

        if (bindingResult.hasErrors()) {
            Product product = productService.getProductWithOwnershipCheck(productId, loginUser.getUserId());
            model.addAttribute("store", store);
            model.addAttribute("product", product);
            return "seller/product/edit";
        }

        // 새 파일이 업로드된 경우 경로 갱신
        if (form.getImageFile() != null && !form.getImageFile().isEmpty()) {
            String uploadedPath = fileStorageService.store(form.getImageFile());
            form.setImageUrl(uploadedPath);
        }

        productService.updateProduct(productId, loginUser.getUserId(), form);
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
        productService.updateProductStatus(productId, loginUser.getUserId(), status);
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
        productService.deleteProduct(productId, loginUser.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "상품이 삭제되었습니다.");
        return "redirect:/seller/stores/" + storeId + "/products";
    }
}
