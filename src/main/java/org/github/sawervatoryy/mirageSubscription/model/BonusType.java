package org.github.sawervatoryy.mirageSubscription.model;

import java.util.Locale;
import java.util.Optional;

/** Тип бонуса подписки. */
public enum BonusType {
    /** Команды выполняются при каждой покупке/выдаче подписки. */
    ACTIVATE,
    /** Игрок забирает бонус вручную раз в N часов (кулдаун). */
    DAILY,
    /** Команды при старте подписки и «откат»-команды при её окончании (права, префиксы и т.п.). */
    PASSIVE;

    public static Optional<BonusType> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(input.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
