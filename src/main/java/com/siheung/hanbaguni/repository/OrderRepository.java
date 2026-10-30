package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"groupPurchase", "groupPurchase.store", "groupPurchase.product"})
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId ORDER BY o.id DESC")
    List<Order> findByUserIdOrderByIdDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "groupPurchase", "groupPurchase.store", "groupPurchase.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithDetailsById(@Param("id") Long id);

    @Query("""
            SELECT o FROM Order o
            WHERE o.groupPurchase.id = :groupPurchaseId
              AND o.status = :status
              AND o.reservedUntil <= :now
            """)
    List<Order> findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("status") Order.Status status,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT o FROM Order o
            WHERE o.user.id = :userId
              AND o.status = :status
              AND o.reservedUntil <= :now
            """)
    List<Order> findByUserIdAndStatusAndReservedUntilLessThanEqual(
            @Param("userId") Long userId,
            @Param("status") Order.Status status,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT COALESCE(SUM(o.quantity), 0)
            FROM Order o
            WHERE o.groupPurchase.id = :groupPurchaseId
              AND o.status = :status
            """)
    int sumQuantityByGroupPurchaseIdAndStatus(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("status") Order.Status status
    );

    @Query("""
            SELECT COALESCE(SUM(o.quantity), 0)
            FROM Order o
            WHERE o.groupPurchase.id = :groupPurchaseId
              AND o.status = com.siheung.hanbaguni.entity.Order.Status.PENDING
              AND (o.reservedUntil IS NULL OR o.reservedUntil > :now)
            """)
    int sumActivePendingQuantity(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT COALESCE(SUM(o.quantity), 0)
            FROM Order o
            WHERE o.groupPurchase.id = :groupPurchaseId
              AND o.user.id = :userId
              AND (
                    o.status = com.siheung.hanbaguni.entity.Order.Status.PAID
                    OR (
                        o.status = com.siheung.hanbaguni.entity.Order.Status.PENDING
                        AND (o.reservedUntil IS NULL OR o.reservedUntil > :now)
                    )
              )
            """)
    int sumUserActiveQuantity(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );
}
