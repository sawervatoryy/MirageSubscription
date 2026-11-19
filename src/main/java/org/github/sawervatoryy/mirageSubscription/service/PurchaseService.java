package org.github.sawervatoryy.mirageSubscription.service;

import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.model.Currency;
import org.github.sawervatoryy.mirageSubscription.model.Plan;
import org.github.sawervatoryy.mirageSubscription.payment.PaymentProvider;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Покупка подписки: асинхронное списание средств, затем выдача на главном потоке.
 * Защита от двойного клика/спама — множество игроков с покупкой «в полёте».
 */
public final class PurchaseService {

    private final MirageSubscription plugin;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PurchaseService(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    /** Покупка без уведомлений игрока. Future завершается на главном потоке. */
    public CompletableFuture<PurchaseResult> purchase(Player player, Plan plan, Currency currency) {
        if (!plugin.isReady()) {
            return CompletableFuture.completedFuture(PurchaseResult.NOT_READY);
        }
        Optional<PaymentProvider> provider = plugin.payments().provider(currency);
        if (provider.isEmpty() || !plugin.payments().isEnabled(currency)) {
            return CompletableFuture.completedFuture(PurchaseResult.CURRENCY_DISABLED);
        }
        int price = plan.price(currency);
        if (price <= 0) {
            return CompletableFuture.completedFuture(PurchaseResult.PRICE_UNAVAILABLE);
        }
        UUID id = player.getUniqueId();
        if (!inFlight.add(id)) {
            return CompletableFuture.completedFuture(PurchaseResult.BUSY);
        }
        String name = player.getName();
        PaymentProvider pay = provider.get();

        return pay.withdraw(id, price)
                .thenApplyAsync(paid -> paid
                        ? finalizePurchase(id, name, plan, currency, price, pay)
                        : PurchaseResult.INSUFFICIENT_FUNDS, plugin.mainThread())
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка оплаты подписки для " + name, ex);
                    return PurchaseResult.ERROR;
                })
                .whenComplete((result, ex) -> inFlight.remove(id));
    }

    /** Покупка с сообщениями и звуками. Future завершается на главном потоке. */
    public CompletableFuture<PurchaseResult> purchaseAndNotify(Player player, Plan plan, Currency currency) {
        return purchase(player, plan, currency).thenApply(result -> {
            notifyPlayer(player, plan, currency, result);
            return result;
        });
    }

    private PurchaseResult finalizePurchase(UUID id, String name, Plan plan, Currency currency,
                                            int price, PaymentProvider pay) {
        try {
            plugin.subscriptions().grant(id, name, plan.durationMillis(), plan.id());
            plugin.getLogger().info(name + " купил подписку «" + plan.id() + "» за " + price + " (" + currency + ")");
            return PurchaseResult.SUCCESS;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось выдать подписку игроку " + name + ", возвращаю средства", e);
            pay.deposit(id, price).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "НЕ УДАЛОСЬ вернуть " + price + " (" + currency + ") игроку " + name, ex);
                return false;
            });
            return PurchaseResult.ERROR;
        }
    }

    private void notifyPlayer(Player player, Plan plan, Currency currency, PurchaseResult result) {
        if (!player.isOnline()) {
            return;
        }
        Settings settings = plugin.settings();
        var lang = plugin.lang();
        String price = settings.formatPrice(plan.price(currency), currency, "-");
        switch (result) {
            case SUCCESS -> {
                lang.send(player, "buy.success", "plan", plan.name(), "days", String.valueOf(plan.days()), "price", price);
                plugin.playSound(player, settings.gui().sounds().success());
                return;
            }
            case INSUFFICIENT_FUNDS -> {
                lang.send(player, currency == Currency.POINTS ? "buy.insufficient-points" : "buy.insufficient-rubles",
                        "price", price);
                String url = settings.rubles().storeUrl();
                if (currency == Currency.RUBLES && url != null && url.startsWith("http")) {
                    player.sendMessage(lang.component("buy.store-link", "url", url).clickEvent(ClickEvent.openUrl(url)));
                }
            }
            case BUSY -> lang.send(player, "buy.busy");
            case CURRENCY_DISABLED -> lang.send(player, "buy.currency-disabled");
            case PRICE_UNAVAILABLE -> lang.send(player, "buy.price-unavailable");
            case NOT_READY -> lang.send(player, "loading");
            case ERROR -> lang.send(player, "buy.error");
        }
        plugin.playSound(player, settings.gui().sounds().error());
    }
}
