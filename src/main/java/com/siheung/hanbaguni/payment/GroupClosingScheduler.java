package com.siheung.hanbaguni.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "hanbaguni.closing.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class GroupClosingScheduler {

    private final GroupClosingJob closingJob;

    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void closeDueGroups() {
        closingJob.runOnce(LocalDateTime.now());
    }
}