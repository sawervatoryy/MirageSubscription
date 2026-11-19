package org.github.sawervatoryy.mirageSubscription.payment;

import org.github.sawervatoryy.mirageSubscription.model.Currency;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Источник средств для оплаты подписки. Все операции асинхронные. */
public interface PaymentProvider {

    Currency currency();

    CompletableFuture<Integer> balance(UUID player);

    /** @return future с false, если средств недостаточно */
    CompletableFuture<Boolean> withdraw(UUID player, int amount);

    /** Возврат средств (например, если выдача подписки не удалась). */
    CompletableFuture<Boolean> deposit(UUID player, int amount);
}
