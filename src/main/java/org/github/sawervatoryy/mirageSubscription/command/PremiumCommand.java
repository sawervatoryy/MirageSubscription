package org.github.sawervatoryy.mirageSubscription.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.gui.MainMenu;
import org.github.sawervatoryy.mirageSubscription.model.Bonus;
import org.github.sawervatoryy.mirageSubscription.model.BonusType;
import org.github.sawervatoryy.mirageSubscription.model.Currency;
import org.github.sawervatoryy.mirageSubscription.model.Plan;
import org.github.sawervatoryy.mirageSubscription.model.Profile;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /premium (/premuim, /prem) — игровая команда. Работает и при выключенном GUI. */
public final class PremiumCommand implements TabExecutor {

    private static final String PERMISSION = "miragesubscription.use";
    private static final List<String> SUBCOMMANDS = List.of("gui", "status", "plans", "buy", "bonuses", "claim", "help");

    private final MirageSubscription plugin;

    public PremiumCommand(MirageSubscription plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        LangManager lang = plugin.lang();
        if (!sender.hasPermission(PERMISSION)) {
            lang.send(sender, "no-permission");
            return true;
        }
        if (!plugin.isReady()) {
            lang.send(sender, "loading");
            return true;
        }
        if (args.length == 0) {
            if (plugin.settings().gui().enabled() && sender instanceof Player player) {
                new MainMenu(plugin, player).open();
            } else {
                showStatusOrHelp(sender);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> openGui(sender);
            case "status", "info" -> showStatusOrHelp(sender);
            case "plans", "list" -> showPlans(sender);
            case "buy" -> buy(sender, args);
            case "bonuses", "bonus" -> showBonuses(sender);
            case "claim" -> claim(sender, args);
            default -> lang.sendLines(sender, "help.player");
        }
        return true;
    }

    private void openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
        } else if (!plugin.settings().gui().enabled()) {
            plugin.lang().send(sender, "gui-disabled");
        } else {
            new MainMenu(plugin, player).open();
        }
    }

    private void showStatusOrHelp(CommandSender sender) {
        LangManager lang = plugin.lang();
        if (!(sender instanceof Player player)) {
            lang.sendLines(sender, "help.player");
            return;
        }
        long now = System.currentTimeMillis();
        Optional<Profile> profile = plugin.profiles().find(player.getUniqueId()).filter(p -> p.isActive(now));
        if (profile.isPresent()) {
            Profile p = profile.get();
            lang.sendLines(player, "status.active",
                    "plan", plugin.planName(p.planId()),
                    "left", TimeFormat.duration(p.remaining(now), lang),
                    "expires", TimeFormat.date(p.expiresAt(), plugin.settings()));
        } else {
            lang.sendLines(player, "status.inactive");
        }
        lang.sendLines(player, "help.player");
    }

    private void showPlans(CommandSender sender) {
        LangManager lang = plugin.lang();
        Settings settings = plugin.settings();
        String none = lang.raw("gui.unavailable");
        lang.send(sender, "plans.header");
        for (Plan plan : settings.plans().values()) {
            lang.send(sender, "plans.line",
                    "id", plan.id(), "name", plan.name(), "days", String.valueOf(plan.days()),
                    "points", settings.formatPrice(plan.pricePoints(), Currency.POINTS, none),
                    "rubles", settings.formatPrice(plan.priceRubles(), Currency.RUBLES, none));
        }
    }

    private void buy(CommandSender sender, String[] args) {
        LangManager lang = plugin.lang();
        if (!(sender instanceof Player player)) {
            lang.send(sender, "player-only");
            return;
        }
        if (args.length < 3) {
            lang.send(sender, "buy.usage");
            return;
        }
        Optional<Plan> plan = plugin.settings().plan(args[1]);
        if (plan.isEmpty()) {
            lang.send(sender, "buy.unknown-plan");
            return;
        }
        Optional<Currency> currency = Currency.parse(args[2]);
        if (currency.isEmpty()) {
            lang.send(sender, "buy.unknown-currency");
            return;
        }
        plugin.purchases().purchaseAndNotify(player, plan.get(), currency.get());
    }

    private void showBonuses(CommandSender sender) {
        LangManager lang = plugin.lang();
        lang.send(sender, "bonuses.header");
        long now = System.currentTimeMillis();
        Optional<Profile> profile = sender instanceof Player p
                ? plugin.profiles().find(p.getUniqueId()).filter(x -> x.isActive(now))
                : Optional.empty();
        for (Bonus bonus : plugin.bonusRegistry().all()) {
            String state;
            if (bonus.type() != BonusType.DAILY) {
                state = lang.raw("bonuses.state.auto");
            } else if (profile.isEmpty()) {
                state = lang.raw("bonuses.state.need-subscription");
            } else {
                long left = plugin.bonusService().cooldownLeft(profile.get(), bonus, now);
                state = left > 0 ? lang.format("bonuses.state.cooldown", "left", TimeFormat.duration(left, lang))
                        : lang.raw("bonuses.state.ready");
            }
            lang.send(sender, "bonuses.line", "id", bonus.id(), "name", bonus.name(), "state", state);
        }
    }

    private void claim(CommandSender sender, String[] args) {
        LangManager lang = plugin.lang();
        if (!(sender instanceof Player player)) {
            lang.send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            lang.send(sender, "bonuses.usage");
            return;
        }
        Optional<Bonus> bonus = plugin.bonusRegistry().get(args[1]);
        if (bonus.isEmpty()) {
            lang.send(sender, "bonuses.not-found");
            return;
        }
        plugin.sendClaimResult(player, bonus.get(), plugin.bonusService().claim(player, bonus.get()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!plugin.isReady()) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("buy") && args.length == 2) {
            return filter(List.copyOf(plugin.settings().plans().keySet()), args[1]);
        }
        if (sub.equals("buy") && args.length == 3) {
            return filter(Arrays.stream(Currency.values()).map(Currency::id).toList(), args[2]);
        }
        if (sub.equals("claim") && args.length == 2) {
            return filter(plugin.bonusRegistry().all().stream()
                    .filter(b -> b.type() == BonusType.DAILY).map(Bonus::id).toList(), args[1]);
        }
        return List.of();
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
