package org.github.sawervatoryy.mirageSubscription.service;

/** Итог попытки забрать бонус. */
public record ClaimResult(Status status, long remainingMillis) {

    public enum Status {
        SUCCESS, NO_SUBSCRIPTION, COOLDOWN, NOT_CLAIMABLE
    }

    public static ClaimResult of(Status status) {
        return new ClaimResult(status, 0);
    }
}
