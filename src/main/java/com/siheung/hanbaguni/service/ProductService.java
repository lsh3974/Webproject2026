package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.ProductForm;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreService storeService;

    public ProductService(ProductRepository productRepository, StoreService storeService) {
        this.productRepository = productRepository;
        this.storeService = storeService;
    }

    public List<Product> findProductsByStoreId(Long storeId) {
        return productRepository.findByStoreId(storeId);
    }

    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    public Product getProductWithOwnershipCheck(Long productId, Long sellerId) {
        Product product = getProductById(productId);
        if (!product.getStore().isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 상품의 관리 권한이 없습니다.");
        }
        return product;
    }

    public Product getProductWithStoreAndOwnershipCheck(Long storeId, Long productId, Long sellerId) {
        Product product = getProductWithOwnershipCheck(productId, sellerId);
        if (!product.getStore().getId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 매장에 속한 상품이 아닙니다.");
        }
        return product;
    }

    @Transactional
    public Long createProduct(Long storeId, Long sellerId, ProductForm form) {
        Store store = storeService.getStoreWithOwnershipCheck(storeId, sellerId);

        Product product = new Product(
                store,
                form.getName().trim(),
                form.getCategory().trim(),
                form.getRegularPrice(),
                form.getUnit().trim(),
                form.getDescription(),
                form.getImageUrl() != null ? form.getImageUrl().trim() : null
        );

        Product saved = productRepository.save(product);
        return saved.getId();
    }

    @Transactional
    public void updateProduct(Long storeId, Long productId, Long sellerId, ProductForm form) {
        Product product = getProductWithStoreAndOwnershipCheck(storeId, productId, sellerId);
        product.update(
                form.getName().trim(),
                form.getCategory().trim(),
                form.getRegularPrice(),
                form.getUnit().trim(),
                form.getDescription(),
                form.getImageUrl() != null ? form.getImageUrl().trim() : null,
                form.getStatus()
        );
    }

    @Transactional
    public void updateProductStatus(Long storeId, Long productId, Long sellerId, Product.Status status) {
        Product product = getProductWithStoreAndOwnershipCheck(storeId, productId, sellerId);
        product.updateStatus(status);
    }

    @Transactional
    public void deleteProduct(Long storeId, Long productId, Long sellerId) {
        Product product = getProductWithStoreAndOwnershipCheck(storeId, productId, sellerId);
        productRepository.delete(product);
    }
}
