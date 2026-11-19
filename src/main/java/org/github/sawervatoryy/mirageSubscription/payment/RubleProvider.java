package org.github.sawervatoryy.mirageSubscription.payment;

import org.github.sawervatoryy.mirageSubscription.model.Currency;
import org.github.sawervatoryy.mirageSubscription.model.Profile;
import org.github.sawervatoryy.mirageSubscription.storage.ProfileRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Рублёвый баланс игрока внутри плагина. Пополняется командой {@code /msub rub add <игрок> <сумма>}
 * (её вызывает донат-магазин после успешной оплаты). Операции выполняются в памяти — без блокировок.
 */
public final class RubleProvider implements PaymentProvider {

    private final ProfileRepository profiles;

    public RubleProvider(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Currency currency() {
        return Currency.RUBLES;
    }

    @Override
    public CompletableFuture<Integer> balance(UUID player) {
        return CompletableFuture.completedFuture(profiles.find(player).map(Profile::rubles).orElse(0));
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID player, int amount) {
        boolean ok = profiles.find(player).map(p -> p.takeRubles(amount)).orElse(false);
        if (ok) {
            profiles.markDirty();
        }
        return CompletableFuture.completedFuture(ok);
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID player, int amount) {
        profiles.getOrCreate(player, null).addRubles(amount);
        return CompletableFuture.completedFuture(true);
    }
}
