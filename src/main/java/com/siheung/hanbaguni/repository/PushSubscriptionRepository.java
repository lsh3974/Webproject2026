
package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.notification.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);

    /**
     * 구독의 현재 소유자가 지정 사용자와 일치할 때만 원자적으로 비활성화한다.
     * 계정 전환과 오래된 푸시 응답 처리가 경합하더라도 다른 계정의 구독은 보호한다.
     */
    @Modifying
    @Query("update PushSubscription p set p.active = false where p.id = :subscriptionId and p.user.id = :userId and p.active = true")
    int deactivateIfOwnedByUser(
            @Param("subscriptionId") Long subscriptionId,
            @Param("userId") Long userId
    );

    List<PushSubscription> findByUserIdAndActiveTrue(Long userId);

    List<PushSubscription> findByUserIdAndActiveTrueOrderByIdAsc(Long userId);
}
