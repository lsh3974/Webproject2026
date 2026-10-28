package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStoreId(Long storeId);

    List<Product> findByStoreIdAndStatus(Long storeId, Product.Status status);

    Optional<Product> findByIdAndStoreId(Long id, Long storeId);
}
