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

    // 동시성 제어를 위한 비관적 락(Pessimistic Lock) 조회 쿼리
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT gp FROM GroupPurchase gp WHERE gp.id = :id")
    Optional<GroupPurchase> findByIdWithLock(@Param("id") Long id);
}