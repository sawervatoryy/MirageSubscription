package org.github.sawervatoryy.mirageSubscription.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

/** Работа с цветными строками: {@code &f} и {@code &#RRGGBB}. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** Преобразует строку в Component без курсива по умолчанию (для названий и описаний предметов). */
    public static Component color(String input) {
        return LEGACY.deserialize(input == null ? "" : input)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> color(List<String> input) {
        return input.stream().map(Text::color).toList();
    }

    /** Подставляет {@code {ключ}} -> значение; pairs = ключ1, значение1, ключ2, значение2... */
    public static String apply(String template, String... pairs) {
        String result = template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return result;
    }
}
