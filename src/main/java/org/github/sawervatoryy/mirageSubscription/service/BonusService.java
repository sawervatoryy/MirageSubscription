package org.github.sawervatoryy.mirageSubscription.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.model.Bonus;
import org.github.sawervatoryy.mirageSubscription.model.BonusType;
import org.github.sawervatoryy.mirageSubscription.model.Profile;

import java.util.List;
import java.util.logging.Level;

/** Выполнение бонусов подписки. Все методы вызываются с главного потока (диспатч консольных команд). */
public final class BonusService {

    private final MirageSubscription plugin;

    public BonusService(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    /**
     * Подписка выдана/продлена.
     *
     * @param freshStart true, если до этого подписки не было (запускаем PASSIVE-бонусы)
     */
    public void onGrant(String playerName, boolean freshStart) {
        for (Bonus bonus : plugin.bonusRegistry().all()) {
            if (bonus.type() == BonusType.ACTIVATE || (bonus.type() == BonusType.PASSIVE && freshStart)) {
                run(bonus.commands(), playerName);
            }
        }
    }

    /** Подписка закончилась — откатываем PASSIVE-бонусы. */
    public void onExpire(String playerName) {
        for (Bonus bonus : plugin.bonusRegistry().all()) {
            if (bonus.type() == BonusType.PASSIVE) {
                run(bonus.revertCommands(), playerName);
            }
        }
    }

    /** Игрок забирает DAILY-бонус. */
    public ClaimResult claim(Player player, Bonus bonus) {
        if (bonus.type() != BonusType.DAILY) {
            return ClaimResult.of(ClaimResult.Status.NOT_CLAIMABLE);
        }
        long now = System.currentTimeMillis();
        Profile profile = plugin.profiles().find(player.getUniqueId()).orElse(null);
        if (profile == null || !profile.isActive(now)) {
            return ClaimResult.of(ClaimResult.Status.NO_SUBSCRIPTION);
        }
        long left = cooldownLeft(profile, bonus, now);
        if (left > 0) {
            return new ClaimResult(ClaimResult.Status.COOLDOWN, left);
        }
        profile.setClaim(bonus.id(), now);
        plugin.profiles().markDirty();
        run(bonus.commands(), player.getName());
        return ClaimResult.of(ClaimResult.Status.SUCCESS);
    }

    /** Сколько мс осталось до следующего получения (0 — можно забирать). */
    public long cooldownLeft(Profile profile, Bonus bonus, long now) {
        Long last = profile.lastClaim(bonus.id());
        return last == null ? 0 : Math.max(0, last + bonus.cooldownMillis() - now);
    }

    private void run(List<String> commands, String playerName) {
        for (String raw : commands) {
            String command = raw.replace("%player%", playerName).replace("{player}", playerName).strip();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isEmpty()) {
                continue;
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка выполнения команды бонуса: " + command, e);
            }
        }
    }
}
