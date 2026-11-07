package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.notification.NotificationDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    boolean existsByNotificationIdAndChannelAndTargetKey(Long notificationId, NotificationDelivery.Channel channel, String targetKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.status in (com.siheung.hanbaguni.notification.NotificationDelivery.DeliveryStatus.PENDING, com.siheung.hanbaguni.notification.NotificationDelivery.DeliveryStatus.RETRY) and d.nextAttemptAt <= :now order by d.id")
    List<NotificationDelivery> findDueForProcessing(LocalDateTime now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.id = :id")
    java.util.Optional<NotificationDelivery> findByIdForUpdate(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.status = com.siheung.hanbaguni.notification.NotificationDelivery.DeliveryStatus.UNKNOWN and (d.lockedUntil is null or d.lockedUntil <= :now) order by d.id")
    List<NotificationDelivery> findUnknownForReconciliation(LocalDateTime now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.status = com.siheung.hanbaguni.notification.NotificationDelivery.DeliveryStatus.PROCESSING and d.lockedUntil <= :now")
    List<NotificationDelivery> findExpiredClaims(LocalDateTime now, Pageable pageable);
}
