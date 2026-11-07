package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.notification.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);
    List<PushSubscription> findByUserIdAndActiveTrue(Long userId);
}
