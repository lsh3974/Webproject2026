package com.siheung.hanbaguni.notification;

public class NotificationSendException extends RuntimeException {
    private final Outcome outcome;
    public NotificationSendException(String message, Outcome outcome) { super(message); this.outcome = outcome; }
    public NotificationSendException(String message, Outcome outcome, Throwable cause) { super(message, cause); this.outcome = outcome; }
    public Outcome getOutcome() { return outcome; }
    public enum Outcome { RETRYABLE, PERMANENT, UNKNOWN, SUBSCRIPTION_GONE }
}
