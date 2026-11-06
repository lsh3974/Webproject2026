package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Pickup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PickupRepository extends JpaRepository<Pickup, Long> {

    List<Pickup> findByStoreIdOrderByIdDesc(Long storeId);

    List<Pickup> findByGroupPurchaseIdOrderByIdDesc(Long groupPurchaseId);

    Optional<Pickup> findByPickupCode(String pickupCode);

    Optional<Pickup> findByPickupCodeAndStoreId(String pickupCode, Long storeId);

    boolean existsByPickupCode(String pickupCode);

    Optional<Pickup> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pickup p WHERE p.id = :id")
    Optional<Pickup> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Pickup p WHERE p.order.id = :orderId AND p.customer.id = :userId")
    Optional<Pickup> findByOrderIdAndCustomerId(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
