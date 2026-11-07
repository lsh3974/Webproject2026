package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.notification.NotificationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {
    boolean existsByEventKey(String eventKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from NotificationEvent e where e.status = com.siheung.hanbaguni.notification.NotificationEvent.EventStatus.PENDING and e.nextAttemptAt <= :now order by e.id")
    List<NotificationEvent> findDueForProcessing(LocalDateTime now, Pageable pageable);
}
