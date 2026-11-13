package com.siheung.hanbaguni.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "hanbaguni.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationSchedulingConfiguration {}
