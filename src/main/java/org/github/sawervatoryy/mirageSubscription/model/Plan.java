package org.github.sawervatoryy.mirageSubscription.model;

import org.bukkit.Material;

import java.time.Duration;
import java.util.List;

/**
 * Тариф подписки из config.yml.
 *
 * @param pricePoints цена в PlayerPoints (&lt;= 0 — оплата недоступна)
 * @param priceRubles цена в рублях (&lt;= 0 — оплата недоступна)
 * @param slot        слот в главном меню
 * @param lore        дополнительные строки описания из конфига
 */
public record Plan(String id, String name, int days, int pricePoints, int priceRubles,
                   Material icon, int slot, List<String> lore) {

    public Plan {
        lore = List.copyOf(lore);
    }

    public int price(Currency currency) {
        return switch (currency) {
            case POINTS -> pricePoints;
            case RUBLES -> priceRubles;
        };
    }

    public long durationMillis() {
        return Duration.ofDays(days).toMillis();
    }
}
