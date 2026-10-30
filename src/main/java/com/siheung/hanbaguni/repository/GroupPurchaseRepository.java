package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.GroupPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GroupPurchaseRepository extends JpaRepository<GroupPurchase, Long>, JpaSpecificationExecutor<GroupPurchase> {

    @Override
    @EntityGraph(attributePaths = {"store", "product"})
    Page<GroupPurchase> findAll(Specification<GroupPurchase> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "product"})
    Optional<GroupPurchase> findOne(Specification<GroupPurchase> spec);

    List<GroupPurchase> findByStoreIdOrderByIdDesc(Long storeId);

    List<GroupPurchase> findByProductIdOrderByIdDesc(Long productId);

    List<GroupPurchase> findByStatusOrderByIdDesc(GroupPurchase.Status status);

    Optional<GroupPurchase> findByIdAndStoreId(Long id, Long storeId);

    @EntityGraph(attributePaths = {"store", "product"})
    @Query("SELECT gp FROM GroupPurchase gp WHERE gp.id = :id")
    Optional<GroupPurchase> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"store", "product"})
    @Query("SELECT gp FROM GroupPurchase gp WHERE gp.id = :id")
    Optional<GroupPurchase> findByIdWithLock(@Param("id") Long id);

    @EntityGraph(attributePaths = {"store", "product"})
    @Query("""
            SELECT gp FROM GroupPurchase gp
            WHERE gp.status = com.siheung.hanbaguni.entity.GroupPurchase.Status.OPEN
              AND gp.startAt <= :now
              AND gp.endAt > :now
            ORDER BY gp.endAt ASC
            """)
    List<GroupPurchase> findOpenGroupPurchases(@Param("now") LocalDateTime now);
}
