package org.github.sawervatoryy.mirageSubscription.model;

import org.bukkit.Material;

import java.util.List;

/**
 * Бонус подписки. Неизменяемый: редактирование создаёт новую копию через {@code with*}.
 *
 * @param commands       консольные команды (поддерживают %player%)
 * @param revertCommands команды отката для PASSIVE-бонуса при окончании подписки
 */
public record Bonus(String id, String name, Material icon, BonusType type, long cooldownMillis,
                    List<String> lore, List<String> commands, List<String> revertCommands) {

    public Bonus {
        lore = List.copyOf(lore);
        commands = List.copyOf(commands);
        revertCommands = List.copyOf(revertCommands);
    }

    public Bonus withName(String value) {
        return new Bonus(id, value, icon, type, cooldownMillis, lore, commands, revertCommands);
    }

    public Bonus withIcon(Material value) {
        return new Bonus(id, name, value, type, cooldownMillis, lore, commands, revertCommands);
    }

    public Bonus withType(BonusType value) {
        return new Bonus(id, name, icon, value, cooldownMillis, lore, commands, revertCommands);
    }

    public Bonus withCooldown(long millis) {
        return new Bonus(id, name, icon, type, millis, lore, commands, revertCommands);
    }

    public Bonus withLore(List<String> value) {
        return new Bonus(id, name, icon, type, cooldownMillis, value, commands, revertCommands);
    }

    public Bonus withCommands(List<String> value) {
        return new Bonus(id, name, icon, type, cooldownMillis, lore, value, revertCommands);
    }

    public Bonus withRevertCommands(List<String> value) {
        return new Bonus(id, name, icon, type, cooldownMillis, lore, commands, value);
    }
}
