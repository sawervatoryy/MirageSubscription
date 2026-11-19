package org.github.sawervatoryy.mirageSubscription.payment;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.github.sawervatoryy.mirageSubscription.model.Currency;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Оплата донат-валютой через API PlayerPoints.
 * <p>
 * Методы {@code look/take/give} синхронные (могут обращаться к базе PlayerPoints), поэтому каждый вызов
 * выполняется в виртуальном потоке, а не на главном потоке сервера. Проверка баланса и списание
 * делаются в одной задаче, чтобы между ними ничего не вклинилось.
 */
public final class PlayerPointsProvider implements PaymentProvider {

    private final PlayerPointsAPI api;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PlayerPointsProvider() {
        PlayerPoints plugin = PlayerPoints.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("PlayerPoints не загружен");
        }
        this.api = plugin.getAPI();
    }

    @Override
    public Currency currency() {
        return Currency.POINTS;
    }

    @Override
    public CompletableFuture<Integer> balance(UUID player) {
        return CompletableFuture.supplyAsync(() -> api.look(player), executor);
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID player, int amount) {
        return CompletableFuture.supplyAsync(() -> api.look(player) >= amount && api.take(player, amount), executor);
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID player, int amount) {
        return CompletableFuture.supplyAsync(() -> api.give(player, amount), executor);
    }
}
