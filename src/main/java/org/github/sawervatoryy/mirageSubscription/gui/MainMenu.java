package org.github.sawervatoryy.mirageSubscription.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.model.Currency;
import org.github.sawervatoryy.mirageSubscription.model.Plan;
import org.github.sawervatoryy.mirageSubscription.model.Profile;
import org.github.sawervatoryy.mirageSubscription.util.Items;
import org.github.sawervatoryy.mirageSubscription.util.Text;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Главное меню /premium: статус подписки, тарифы, бонусы. */
public final class MainMenu extends Menu {

    private static final int STATUS_SLOT = 13;
    private static final int BONUSES_SLOT = 40;
    private static final int CLOSE_SLOT = 49;

    private boolean active;

    public MainMenu(MirageSubscription plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected Component title() {
        return plugin.lang().component("gui.main.title");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        Settings settings = plugin.settings();
        LangManager lang = plugin.lang();
        long now = System.currentTimeMillis();

        Optional<Profile> profile = plugin.profiles().find(player.getUniqueId());
        active = profile.map(p -> p.isActive(now)).orElse(false);

        ItemStack status;
        if (active) {
            Profile p = profile.orElseThrow();
            status = Items.create(settings.gui().icons().statusActive(),
                    lang.component("gui.main.status.active.name"),
                    lang.components("gui.main.status.active.lore",
                            "plan", plugin.planName(p.planId()),
                            "left", TimeFormat.duration(p.remaining(now), lang),
                            "expires", TimeFormat.date(p.expiresAt(), settings)));
            Items.glint(status, true);
        } else {
            status = Items.create(settings.gui().icons().statusInactive(),
                    lang.component("gui.main.status.inactive.name"),
                    lang.components("gui.main.status.inactive.lore"));
        }
        set(STATUS_SLOT, status);

        String unavailable = lang.raw("gui.unavailable");
        for (Plan plan : settings.plans().values()) {
            List<String> lines = new ArrayList<>(plan.lore());
            lines.addAll(lang.formatList("gui.main.plan-lore",
                    "days", String.valueOf(plan.days()),
                    "points", settings.formatPrice(plan.pricePoints(), Currency.POINTS, unavailable),
                    "rubles", settings.formatPrice(plan.priceRubles(), Currency.RUBLES, unavailable)));
            set(plan.slot(),
                    Items.create(plan.icon(), Text.color(plan.name()), Text.color(lines)),
                    () -> new PaymentMenu(plugin, player, plan).open());
        }

        set(BONUSES_SLOT,
                Items.create(settings.gui().icons().bonuses(),
                        lang.component("gui.main.bonuses.name"),
                        lang.components("gui.main.bonuses.lore")),
                () -> new BonusesMenu(plugin, player).open());

        set(CLOSE_SLOT,
                Items.create(settings.gui().icons().close(), lang.component("gui.main.close"), List.of()),
                player::closeInventory);
    }

    /** Пульсация свечения иконки статуса для активной подписки. */
    @Override
    protected void onIdleTick(int phase) {
        if (!active || phase % 3 != 0) {
            return;
        }
        ItemStack item = inv().getItem(STATUS_SLOT);
        if (item != null) {
            Items.glint(item, phase % 6 != 0);
            inv().setItem(STATUS_SLOT, item);
        }
    }
}
