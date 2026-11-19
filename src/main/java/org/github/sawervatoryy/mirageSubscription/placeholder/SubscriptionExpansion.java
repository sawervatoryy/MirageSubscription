package org.github.sawervatoryy.mirageSubscription.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.model.Profile;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Плейсхолдеры %miragesub_...% для TAB, scoreboard и других плагинов.
 * Только чтение из памяти — безопасно вызывать из любого потока.
 * <pre>
 * %miragesub_active%     true / false
 * %miragesub_has%        то же самое
 * %miragesub_status%     текст из config.yml (placeholders.active / inactive)
 * %miragesub_timeleft%   «2д 5ч 10м»
 * %miragesub_days_left%  целых дней до конца
 * %miragesub_hours_left% целых часов до конца
 * %miragesub_expires%    дата окончания
 * %miragesub_plan%       название тарифа
 * %miragesub_rubles%     рублёвый баланс
 * </pre>
 */
public final class SubscriptionExpansion extends PlaceholderExpansion {

    private final MirageSubscription plugin;

    public SubscriptionExpansion(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "miragesub";
    }

    @Override
    public @NotNull String getAuthor() {
        return "sawervatoryy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !plugin.isReady()) {
            return "";
        }
        Settings settings = plugin.settings();
        long now = System.currentTimeMillis();
        Optional<Profile> profile = plugin.profiles().find(player.getUniqueId());
        boolean active = profile.map(p -> p.isActive(now)).orElse(false);
        long left = active ? profile.orElseThrow().remaining(now) : 0;
        String none = settings.placeholders().none();

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "active", "has" -> Boolean.toString(active);
            case "status" -> active ? settings.placeholders().active() : settings.placeholders().inactive();
            case "timeleft" -> active ? TimeFormat.duration(left, plugin.lang()) : none;
            case "days_left" -> String.valueOf(TimeUnit.MILLISECONDS.toDays(left));
            case "hours_left" -> String.valueOf(TimeUnit.MILLISECONDS.toHours(left));
            case "expires" -> active ? TimeFormat.date(profile.orElseThrow().expiresAt(), settings) : none;
            case "plan" -> active ? plugin.planName(profile.orElseThrow().planId()) : none;
            case "rubles" -> String.valueOf(profile.map(Profile::rubles).orElse(0));
            default -> null;
        };
    }
}
