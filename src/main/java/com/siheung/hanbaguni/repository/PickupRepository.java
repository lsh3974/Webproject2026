package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Pickup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PickupRepository extends JpaRepository<Pickup, Long> {

    List<Pickup> findByStoreIdOrderByIdDesc(Long storeId);

    List<Pickup> findByGroupPurchaseIdOrderByIdDesc(Long groupPurchaseId);

    Optional<Pickup> findByPickupCode(String pickupCode);

    Optional<Pickup> findByPickupCodeAndStoreId(String pickupCode, Long storeId);

    boolean existsByPickupCode(String pickupCode);
}
