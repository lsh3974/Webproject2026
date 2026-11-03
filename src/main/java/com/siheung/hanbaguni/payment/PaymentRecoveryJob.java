package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "hanbaguni.payment.recovery-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentRecoveryJob {

    private final OrderRepository orders;
    private final PaymentService payments;

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void recoverPendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        for (var pending : orders.findDuePayments(now, PageRequest.of(0, 50))) {
            try {
                payments.recover(pending.getOrderId(), pending.getUserId(), LocalDateTime.now());
            } catch (RuntimeException exception) {
                log.warn("결제 복구 실패 orderId={}", pending.getOrderId(), exception);
            }
        }
    }
}
