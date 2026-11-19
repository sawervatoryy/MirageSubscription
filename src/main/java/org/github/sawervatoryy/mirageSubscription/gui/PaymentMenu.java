package org.github.sawervatoryy.mirageSubscription.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.model.Currency;
import org.github.sawervatoryy.mirageSubscription.model.Plan;
import org.github.sawervatoryy.mirageSubscription.payment.PaymentProvider;
import org.github.sawervatoryy.mirageSubscription.service.PurchaseResult;
import org.github.sawervatoryy.mirageSubscription.util.Items;
import org.github.sawervatoryy.mirageSubscription.util.Text;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Выбор способа оплаты тарифа: донат-валюта или рубли. Баланс подгружается асинхронно. */
public final class PaymentMenu extends Menu {

    private static final int POINTS_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int RUBLES_SLOT = 15;
    private static final int BACK_SLOT = 22;

    private final Plan plan;
    private final Map<Currency, String> balances = new EnumMap<>(Currency.class);

    public PaymentMenu(MirageSubscription plugin, Player player, Plan plan) {
        super(plugin, player);
        this.plan = plan;
    }

    @Override
    protected Component title() {
        return plugin.lang().component("gui.payment.title");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        Settings settings = plugin.settings();
        LangManager lang = plugin.lang();

        set(INFO_SLOT, Items.create(plan.icon(), Text.color(plan.name()),
                lang.components("gui.payment.info-lore", "days", String.valueOf(plan.days()))));

        buildCurrency(Currency.POINTS, POINTS_SLOT);
        buildCurrency(Currency.RUBLES, RUBLES_SLOT);

        set(BACK_SLOT,
                Items.create(settings.gui().icons().back(), lang.component("gui.payment.back"), List.of()),
                () -> new MainMenu(plugin, player).open());

        // Запрашиваем балансы только один раз; повторные redraw используют кэш.
        for (Currency currency : Currency.values()) {
            if (!balances.containsKey(currency)) {
                loadBalance(currency);
            }
        }
    }

    private void buildCurrency(Currency currency, int slot) {
        ItemStack item = currencyItem(currency);
        if (isPayable(currency)) {
            set(slot, item, () -> buy(currency));
        } else {
            set(slot, item);
        }
    }

    private boolean isPayable(Currency currency) {
        return plugin.payments().isEnabled(currency) && plan.price(currency) > 0;
    }

    private ItemStack currencyItem(Currency currency) {
        Settings settings = plugin.settings();
        LangManager lang = plugin.lang();
        String key = currency == Currency.POINTS ? "points" : "rubles";
        if (!isPayable(currency)) {
            return Items.create(settings.gui().icons().unavailable(),
                    lang.component("gui.payment.unavailable.name"),
                    lang.components("gui.payment.unavailable.lore"));
        }
        String balance = balances.getOrDefault(currency, lang.raw("gui.payment.balance-loading"));
        var icon = currency == Currency.POINTS ? settings.gui().icons().points() : settings.gui().icons().rubles();
        return Items.create(icon,
                lang.component("gui.payment." + key + ".name"),
                lang.components("gui.payment." + key + ".lore",
                        "price", settings.formatPrice(plan.price(currency), currency, "-"),
                        "balance", balance));
    }

    private void loadBalance(Currency currency) {
        if (!isPayable(currency)) {
            return;
        }
        Optional<PaymentProvider> provider = plugin.payments().provider(currency);
        if (provider.isEmpty()) {
            return;
        }
        provider.get().balance(player.getUniqueId())
                .thenAcceptAsync(value -> {
                    balances.put(currency, plugin.settings().formatPrice(Math.max(0, value), currency, "0"));
                    update(currency == Currency.POINTS ? POINTS_SLOT : RUBLES_SLOT, currencyItem(currency));
                }, plugin.mainThread())
                .exceptionally(ex -> null);
    }

    private void buy(Currency currency) {
        plugin.purchases().purchaseAndNotify(player, plan, currency).thenAccept(result -> {
            if (result == PurchaseResult.SUCCESS && player.isOnline()) {
                new MainMenu(plugin, player).open();
            }
        });
    }
}
