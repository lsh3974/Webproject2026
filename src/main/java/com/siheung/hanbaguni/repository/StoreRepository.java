package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findBySellerId(Long sellerId);

    Optional<Store> findByIdAndSellerId(Long id, Long sellerId);

    boolean existsByIdAndSellerId(Long id, Long sellerId);
}
