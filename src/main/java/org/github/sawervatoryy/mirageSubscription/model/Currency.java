package org.github.sawervatoryy.mirageSubscription.model;

import java.util.Locale;
import java.util.Optional;

/** Валюта, которой можно оплатить подписку. */
public enum Currency {
    /** Донат-валюта (плагин PlayerPoints). */
    POINTS("points"),
    /** Рубли (внутренний баланс плагина, пополняется донат-магазином). */
    RUBLES("rub");

    private final String id;

    Currency(String id) {
        this.id = id;
    }

    /** Короткий идентификатор для команд и tab-completion. */
    public String id() {
        return id;
    }

    /** Разбирает ввод игрока: points / pp / rub / rubles и т.д. */
    public static Optional<Currency> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "points", "point", "pp", "playerpoints", "поинты" -> Optional.of(POINTS);
            case "rub", "rubles", "ruble", "rur", "руб", "рубли", "р" -> Optional.of(RUBLES);
            default -> Optional.empty();
        };
    }
}
