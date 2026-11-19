package org.github.sawervatoryy.mirageSubscription.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.model.Bonus;
import org.github.sawervatoryy.mirageSubscription.model.BonusType;
import org.github.sawervatoryy.mirageSubscription.model.Profile;
import org.github.sawervatoryy.mirageSubscription.service.ClaimResult;
import org.github.sawervatoryy.mirageSubscription.util.Items;
import org.github.sawervatoryy.mirageSubscription.util.Text;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Список бонусов подписки; DAILY-бонусы забираются кликом. */
public final class BonusesMenu extends Menu {

    private static final int BACK_SLOT = 49;
    /** Внутренняя область 7x4 (между рамкой). */
    private static final int[] SLOTS = innerSlots();

    public BonusesMenu(MirageSubscription plugin, Player player) {
        super(plugin, player);
    }

    private static int[] innerSlots() {
        int[] slots = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * 9 + col;
            }
        }
        return slots;
    }

    @Override
    protected Component title() {
        return plugin.lang().component("gui.bonuses.title");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        LangManager lang = plugin.lang();
        long now = System.currentTimeMillis();
        Optional<Profile> profile = plugin.profiles().find(player.getUniqueId()).filter(p -> p.isActive(now));

        int index = 0;
        for (Bonus bonus : plugin.bonusRegistry().all()) {
            if (index >= SLOTS.length) {
                break;
            }
            List<String> lines = new ArrayList<>(bonus.lore());
            lines.addAll(lang.formatList("gui.bonuses.type." + bonus.type().name().toLowerCase(Locale.ROOT)));
            if (bonus.type() == BonusType.DAILY) {
                lines.add(stateLine(bonus, profile, now));
            }
            var item = Items.create(bonus.icon(), Text.color(bonus.name()), Text.color(lines));
            if (bonus.type() == BonusType.DAILY) {
                set(SLOTS[index], item, () -> claim(bonus));
            } else {
                set(SLOTS[index], item);
            }
            index++;
        }

        set(BACK_SLOT,
                Items.create(plugin.settings().gui().icons().back(), lang.component("gui.bonuses.back"), List.of()),
                () -> new MainMenu(plugin, player).open());
    }

    private String stateLine(Bonus bonus, Optional<Profile> profile, long now) {
        LangManager lang = plugin.lang();
        if (profile.isEmpty()) {
            return lang.format("gui.bonuses.state.need-subscription");
        }
        long left = plugin.bonusService().cooldownLeft(profile.get(), bonus, now);
        return left > 0
                ? lang.format("gui.bonuses.state.cooldown", "left", TimeFormat.duration(left, lang))
                : lang.format("gui.bonuses.state.ready");
    }

    private void claim(Bonus bonus) {
        ClaimResult result = plugin.bonusService().claim(player, bonus);
        plugin.sendClaimResult(player, bonus, result);
        redraw();
    }
}
