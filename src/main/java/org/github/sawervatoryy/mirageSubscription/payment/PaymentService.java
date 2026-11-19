package org.github.sawervatoryy.mirageSubscription.payment;

import org.bukkit.Bukkit;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.model.Currency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Реестр платёжных провайдеров. */
public final class PaymentService {

    private final MirageSubscription plugin;
    private final Map<Currency, PaymentProvider> providers = new ConcurrentHashMap<>();

    public PaymentService(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    /** Регистрирует провайдеров (вызывается на главном потоке после загрузки данных). */
    public void init() {
        providers.put(Currency.RUBLES, new RubleProvider(plugin.profiles()));
        if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            try {
                providers.put(Currency.POINTS, new PlayerPointsProvider());
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Не удалось подключиться к PlayerPoints: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("PlayerPoints не найден — оплата поинтами недоступна.");
        }
    }

    public Optional<PaymentProvider> provider(Currency currency) {
        return Optional.ofNullable(providers.get(currency));
    }

    /** Валюта включена в config.yml и провайдер доступен. */
    public boolean isEnabled(Currency currency) {
        return plugin.settings().currency(currency).enabled() && providers.containsKey(currency);
    }
}
