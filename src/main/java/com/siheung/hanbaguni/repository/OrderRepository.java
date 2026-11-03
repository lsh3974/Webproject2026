package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import com.siheung.hanbaguni.entity.GroupPurchase;
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"groupPurchase", "groupPurchase.store", "groupPurchase.product"})
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId ORDER BY o.id DESC")
    List<Order> findByUserIdOrderByIdDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "groupPurchase", "groupPurchase.store", "groupPurchase.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "groupPurchase", "groupPurchase.store", "groupPurchase.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    @Query("select case when count(o) > 0 then true else false end from Order o where o.groupPurchase.id = :groupId")
    boolean existsByGroupPurchaseId(@Param("groupId") Long groupPurchaseId);

    @Query("""
        select case when count(o) > 0 then true else false end
        from Order o
        where o.groupPurchase.id = :groupPurchaseId
          and o.status = :status
        """)
    boolean existsByGroupPurchaseIdAndStatus(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("status") Order.Status status
    );

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
          AND (
                o.status = com.siheung.hanbaguni.entity.Order.Status.PAYMENT_PENDING
                OR (
                    o.status = com.siheung.hanbaguni.entity.Order.Status.PENDING
                    AND (o.reservedUntil IS NULL OR o.reservedUntil > :now)
                )
          )
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
                OR o.status = com.siheung.hanbaguni.entity.Order.Status.PAYMENT_PENDING
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

    @Query("""
        select o.groupPurchase.id
        from Order o
        where o.id = :orderId
          and o.user.id = :userId
        """)
    Optional<Long> findPaymentGroupId(
            @Param("orderId") Long orderId,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from Order o
        where o.id = :orderId
        """)
    Optional<Order> findPaymentOrderWithLock(
            @Param("orderId") Long orderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from Order o
        where o.groupPurchase.id = :groupPurchaseId
          and o.status = com.siheung.hanbaguni.entity.Order.Status.PENDING
        order by o.id
        """)
    List<Order> findPendingOrdersForGroupWithLock(
            @Param("groupPurchaseId") Long groupPurchaseId
    );

    @Query("""
        select o.groupPurchase.id
        from Order o
        where o.id = :orderId
        """)
    Optional<Long> findGroupIdForRefund(
            @Param("orderId") Long orderId
    );

    @Query("""
        select o.id
        from Order o
        where (o.groupPurchase.status = :groupStatus and o.status = :paid)
           or o.status = :pendingRefund
        order by o.id
        """)
    List<Long> findRefundCandidateIds(
            @Param("groupStatus") GroupPurchase.Status groupStatus,
            @Param("paid") Order.Status paid,
            @Param("pendingRefund") Order.Status pendingRefund
    );

    @Query("""
        select o.id from Order o
        where o.status = :status and o.groupPurchase.endAt <= :now
        order by o.id
        """)
    List<Long> findDuePaymentIds(@Param("status") Order.Status status,
                                 @Param("now") LocalDateTime now);
}
