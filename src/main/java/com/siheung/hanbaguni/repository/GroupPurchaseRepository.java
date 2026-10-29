package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.GroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupPurchaseRepository extends JpaRepository<GroupPurchase, Long> {

    List<GroupPurchase> findByStoreIdOrderByIdDesc(Long storeId);

    List<GroupPurchase> findByProductIdOrderByIdDesc(Long productId);

    List<GroupPurchase> findByStatusOrderByIdDesc(GroupPurchase.Status status);

    Optional<GroupPurchase> findByIdAndStoreId(Long id, Long storeId);
}
