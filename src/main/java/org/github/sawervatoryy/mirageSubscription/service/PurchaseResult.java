package org.github.sawervatoryy.mirageSubscription.service;

/** Итог покупки подписки. */
public enum PurchaseResult {
    SUCCESS,
    NOT_READY,
    BUSY,
    CURRENCY_DISABLED,
    PRICE_UNAVAILABLE,
    INSUFFICIENT_FUNDS,
    ERROR
}
