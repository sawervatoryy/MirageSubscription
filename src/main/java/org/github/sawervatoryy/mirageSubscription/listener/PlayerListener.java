package org.github.sawervatoryy.mirageSubscription.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;

import java.util.concurrent.TimeUnit;

/** Обновляет имя игрока, завершает просроченную подписку и предупреждает о скором окончании. */
public final class PlayerListener implements Listener {

    private static final long NOTIFY_DELAY_TICKS = 60L;

    private final MirageSubscription plugin;

    public PlayerListener(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isReady()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.profiles().find(player.getUniqueId()).ifPresent(profile -> {
            if (!player.getName().equals(profile.name())) {
                profile.setName(player.getName());
                plugin.profiles().markDirty();
            }
            long now = System.currentTimeMillis();
            if (plugin.subscriptions().expireIfDue(profile, now)) {
                return; // сообщение об окончании отправлено сервисом
            }
            long threshold = TimeUnit.DAYS.toMillis(plugin.settings().timing().notifyBeforeDays());
            if (profile.isActive(now) && profile.remaining(now) <= threshold) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.lang().send(player, "expire.soon", "left",
                                TimeFormat.duration(profile.remaining(System.currentTimeMillis()), plugin.lang()));
                    }
                }, NOTIFY_DELAY_TICKS);
            }
        });
    }
}
