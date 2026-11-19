package org.github.sawervatoryy.mirageSubscription.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.model.Profile;

import java.util.Optional;
import java.util.UUID;

/** Бизнес-логика подписок: выдача, снятие, окончание. Изменяющие методы — только с главного потока. */
public final class SubscriptionService {

    private final MirageSubscription plugin;

    public SubscriptionService(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(UUID uuid) {
        long now = System.currentTimeMillis();
        return plugin.profiles().find(uuid).map(p -> p.isActive(now)).orElse(false);
    }

    public Optional<Profile> activeProfile(UUID uuid) {
        long now = System.currentTimeMillis();
        return plugin.profiles().find(uuid).filter(p -> p.isActive(now));
    }

    /** Выдаёт или продлевает подписку и запускает бонусы. */
    public void grant(UUID uuid, String name, long durationMillis, String planId) {
        long now = System.currentTimeMillis();
        Profile profile = plugin.profiles().getOrCreate(uuid, name);
        boolean wasActive = profile.isActive(now);
        profile.extend(durationMillis, planId, now);
        plugin.profiles().markDirty();
        plugin.bonusService().onGrant(profile.name(), !wasActive);
    }

    /** Уменьшает срок подписки. @return true, если подписка закончилась. */
    public boolean reduce(UUID uuid, long millis) {
        Optional<Profile> profile = plugin.profiles().find(uuid);
        if (profile.isEmpty()) {
            return false;
        }
        boolean ended = profile.get().reduce(millis, System.currentTimeMillis());
        plugin.profiles().markDirty();
        if (ended) {
            finish(profile.get());
        }
        return ended;
    }

    /** Принудительно отменяет подписку. @return false, если её не было. */
    public boolean revoke(UUID uuid) {
        Optional<Profile> profile = activeProfile(uuid);
        if (profile.isEmpty()) {
            return false;
        }
        profile.get().clear();
        plugin.profiles().markDirty();
        finish(profile.get());
        return true;
    }

    /** Есть ли профили с истёкшим сроком (дешёвая проверка для асинхронной задачи). */
    public boolean hasDue() {
        long now = System.currentTimeMillis();
        return plugin.profiles().all().stream().anyMatch(p -> p.isDue(now));
    }

    /** Завершает все просроченные подписки. */
    public void expireDue() {
        long now = System.currentTimeMillis();
        for (Profile profile : plugin.profiles().all()) {
            expireIfDue(profile, now);
        }
    }

    /** @return true, если подписка игрока только что закончилась. */
    public boolean expireIfDue(Profile profile, long now) {
        if (!profile.expireIfDue(now)) {
            return false;
        }
        plugin.profiles().markDirty();
        finish(profile);
        return true;
    }

    private void finish(Profile profile) {
        plugin.bonusService().onExpire(profile.name());
        Player online = Bukkit.getPlayer(profile.uuid());
        if (online != null) {
            plugin.lang().send(online, "expire.expired");
            plugin.playSound(online, plugin.settings().gui().sounds().error());
        }
    }
}
