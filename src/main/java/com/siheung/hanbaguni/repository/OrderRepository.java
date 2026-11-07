package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.GroupPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"user", "groupPurchase", "groupPurchase.store", "groupPurchase.store.seller"})
    @Query("""
    SELECT o
    FROM Order o
    WHERE o.groupPurchase.id = :groupPurchaseId
      AND o.status = :status
    """)
    List<Order> findByGroupPurchaseIdAndStatus(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("status") Order.Status status
    );

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

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Order o WHERE o.groupPurchase.id = :groupPurchaseId")
    boolean existsByGroupPurchaseId(@Param("groupPurchaseId") Long groupPurchaseId);

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

    @Query("""
            SELECT SUM(o.quantity)
            FROM Order o
            WHERE o.groupPurchase.id = :groupPurchaseId
              AND (
                    o.status = com.siheung.hanbaguni.entity.Order.Status.PAID
                    OR o.status = com.siheung.hanbaguni.entity.Order.Status.PAYMENT_PENDING
                    OR (
                        o.status = com.siheung.hanbaguni.entity.Order.Status.PENDING
                        AND (o.reservedUntil IS NULL OR o.reservedUntil > :now)
                    )
              )
            GROUP BY o.user.id
            """)
    List<Long> findActiveUserQuantities(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select case when count(o) > 0 then true else false end from Order o
            where o.groupPurchase.id = :groupId and o.status = :status
            """)
    boolean hasOrdersInStatus(@Param("groupId") Long groupId, @Param("status") Order.Status status);

    @Query("""
            SELECT MAX(o.reservedUntil)
            FROM Order o
            WHERE o.groupPurchase.id = :groupPurchaseId
              AND o.status = com.siheung.hanbaguni.entity.Order.Status.PENDING
              AND o.reservedUntil > :now
            """)
    Optional<LocalDateTime> findMaxReservedUntil(
            @Param("groupPurchaseId") Long groupPurchaseId,
            @Param("now") LocalDateTime now
    );

    interface PendingPayment {
        Long getOrderId();
        Long getUserId();
    }

    @Query("""
            select o.id as orderId, o.user.id as userId from Order o
            where o.status = com.siheung.hanbaguni.entity.Order.Status.PAYMENT_PENDING
              and (o.paymentRetryAt is null or o.paymentRetryAt <= :now)
            order by o.paymentRetryAt, o.id
            """)
    List<PendingPayment> findDuePayments(@Param("now") LocalDateTime now,
                                         org.springframework.data.domain.Pageable page);
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

}
