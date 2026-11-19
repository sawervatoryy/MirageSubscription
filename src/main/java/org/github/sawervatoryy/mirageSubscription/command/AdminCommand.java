package org.github.sawervatoryy.mirageSubscription.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.model.Bonus;
import org.github.sawervatoryy.mirageSubscription.model.BonusType;
import org.github.sawervatoryy.mirageSubscription.model.Plan;
import org.github.sawervatoryy.mirageSubscription.model.Profile;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** /msub — администрирование: подписки, рублёвый баланс, бонусы (редактор в игре), reload. */
public final class AdminCommand implements TabExecutor {

    private static final String PERMISSION = "miragesubscription.admin";
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> ROOT = List.of("give", "take", "revoke", "check", "rub", "bonus", "reload", "help");
    private static final List<String> RUB = List.of("add", "take", "set", "balance");
    private static final List<String> BONUS = List.of("list", "info", "create", "delete", "setname", "seticon",
            "settype", "setcooldown", "addlore", "clearlore", "addcmd", "delcmd", "addrevert", "delrevert");

    private final MirageSubscription plugin;

    public AdminCommand(MirageSubscription plugin) {
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            lang.sendLines(sender, "help.admin");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reload(sender);
            return true;
        }
        if (!plugin.isReady()) {
            lang.send(sender, "loading");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
            case "revoke" -> revoke(sender, args);
            case "check" -> check(sender, args);
            case "rub" -> rub(sender, args);
            case "bonus" -> bonus(sender, args);
            default -> lang.sendLines(sender, "help.admin");
        }
        return true;
    }

    // ------------------------------------------------------------------ подписки

    private void reload(CommandSender sender) {
        plugin.reloadAll().whenComplete((v, ex) -> plugin.mainThread().execute(() -> {
            if (ex != null) {
                plugin.getLogger().warning("Ошибка перезагрузки: " + ex.getMessage());
            }
            plugin.lang().send(sender, ex == null ? "admin.reloaded" : "admin.reload-failed");
        }));
    }

    private Optional<OfflinePlayer> target(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer result = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (result == null) {
            plugin.lang().send(sender, "admin.player-not-found", "player", name);
        }
        return Optional.ofNullable(result);
    }

    private static String nameOf(OfflinePlayer player, String fallback) {
        return player.getName() != null ? player.getName() : fallback;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.lang().send(sender, "admin.usage.give");
            return;
        }
        Optional<OfflinePlayer> target = target(sender, args[1]);
        if (target.isEmpty()) {
            return;
        }
        long millis;
        String planId;
        Optional<Plan> plan = plugin.settings().plan(args[2]);
        if (plan.isPresent()) {
            millis = plan.get().durationMillis();
            planId = plan.get().id();
        } else {
            Integer days = parsePositive(args[2]);
            if (days == null) {
                plugin.lang().send(sender, "admin.usage.give");
                return;
            }
            millis = Duration.ofDays(days).toMillis();
            planId = "custom";
        }
        String name = nameOf(target.get(), args[1]);
        plugin.subscriptions().grant(target.get().getUniqueId(), name, millis, planId);
        plugin.lang().send(sender, "admin.given", "player", name,
                "days", String.valueOf(Duration.ofMillis(millis).toDays()));
        Player online = target.get().getPlayer();
        if (online != null) {
            plugin.lang().send(online, "admin.given-notify",
                    "days", String.valueOf(Duration.ofMillis(millis).toDays()));
        }
    }

    private void take(CommandSender sender, String[] args) {
        Integer days = args.length >= 3 ? parsePositive(args[2]) : null;
        if (days == null) {
            plugin.lang().send(sender, "admin.usage.take");
            return;
        }
        target(sender, args[1]).ifPresent(t -> {
            plugin.subscriptions().reduce(t.getUniqueId(), Duration.ofDays(days).toMillis());
            plugin.lang().send(sender, "admin.taken", "player", nameOf(t, args[1]), "days", String.valueOf(days));
        });
    }

    private void revoke(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.lang().send(sender, "admin.usage.revoke");
            return;
        }
        target(sender, args[1]).ifPresent(t -> plugin.lang().send(sender,
                plugin.subscriptions().revoke(t.getUniqueId()) ? "admin.revoked" : "admin.no-subscription",
                "player", nameOf(t, args[1])));
    }

    private void check(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.lang().send(sender, "admin.usage.check");
            return;
        }
        target(sender, args[1]).ifPresent(t -> {
            LangManager lang = plugin.lang();
            long now = System.currentTimeMillis();
            Optional<Profile> profile = plugin.profiles().find(t.getUniqueId());
            String name = nameOf(t, args[1]);
            int rubles = profile.map(Profile::rubles).orElse(0);
            if (profile.isPresent() && profile.get().isActive(now)) {
                lang.send(sender, "admin.check-active", "player", name,
                        "plan", plugin.planName(profile.get().planId()),
                        "left", TimeFormat.duration(profile.get().remaining(now), lang),
                        "expires", TimeFormat.date(profile.get().expiresAt(), plugin.settings()),
                        "rubles", String.valueOf(rubles));
            } else {
                lang.send(sender, "admin.check-inactive", "player", name, "rubles", String.valueOf(rubles));
            }
        });
    }

    private void rub(CommandSender sender, String[] args) {
        LangManager lang = plugin.lang();
        if (args.length < 3) {
            lang.send(sender, "admin.usage.rub");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Optional<OfflinePlayer> target = target(sender, args[2]);
        if (target.isEmpty()) {
            return;
        }
        String name = nameOf(target.get(), args[2]);
        Profile profile = plugin.profiles().getOrCreate(target.get().getUniqueId(), name);
        if (action.equals("balance")) {
            lang.send(sender, "admin.rub-balance", "player", name, "amount", String.valueOf(profile.rubles()));
            return;
        }
        Integer amount = args.length >= 4 ? parsePositive(args[3]) : null;
        if (!RUB.contains(action) || amount == null) {
            lang.send(sender, "admin.usage.rub");
            return;
        }
        switch (action) {
            case "add" -> profile.addRubles(amount);
            case "set" -> profile.setRubles(amount);
            case "take" -> profile.addRubles(-amount);
            default -> { }
        }
        plugin.profiles().markDirty();
        lang.send(sender, "admin.rub-changed", "player", name, "amount", String.valueOf(profile.rubles()));
        Player online = target.get().getPlayer();
        if (online != null && action.equals("add")) {
            lang.send(online, "admin.rub-received", "amount", String.valueOf(amount));
        }
    }

    // ------------------------------------------------------------------ редактор бонусов

    private void bonus(CommandSender sender, String[] args) {
        LangManager lang = plugin.lang();
        if (args.length < 2) {
            lang.sendLines(sender, "help.admin-bonus");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            lang.send(sender, "admin.bonus.list-header");
            plugin.bonusRegistry().all().forEach(b -> lang.send(sender, "admin.bonus.list-line",
                    "id", b.id(), "name", b.name(), "type", b.type().name()));
            return;
        }
        if (args.length < 3) {
            lang.sendLines(sender, "help.admin-bonus");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        String rest = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        switch (action) {
            case "create" -> createBonus(sender, id, args);
            case "delete" -> lang.send(sender,
                    plugin.bonusRegistry().remove(id) ? "admin.bonus.deleted" : "admin.bonus.not-found", "id", id);
            case "info" -> plugin.bonusRegistry().get(id).ifPresentOrElse(b -> info(sender, b),
                    () -> lang.send(sender, "admin.bonus.not-found", "id", id));
            case "setname" -> edit(sender, id, rest.isBlank() ? null : b -> b.withName(rest));
            case "seticon" -> setIcon(sender, id, args);
            case "settype" -> {
                Optional<BonusType> type = args.length > 3 ? BonusType.parse(args[3]) : Optional.empty();
                edit(sender, id, type.<UnaryOperator<Bonus>>map(t -> b -> b.withType(t)).orElse(null));
            }
            case "setcooldown" -> {
                double hours = args.length > 3 ? parseDouble(args[3]) : -1;
                edit(sender, id, hours <= 0 ? null : b -> b.withCooldown((long) (hours * 3_600_000)));
            }
            case "addlore" -> edit(sender, id, rest.isBlank() ? null : b -> b.withLore(append(b.lore(), rest)));
            case "clearlore" -> edit(sender, id, b -> b.withLore(List.of()));
            case "addcmd" -> edit(sender, id, rest.isBlank() ? null : b -> b.withCommands(append(b.commands(), rest)));
            case "addrevert" -> edit(sender, id, rest.isBlank() ? null : b -> b.withRevertCommands(append(b.revertCommands(), rest)));
            case "delcmd" -> {
                Integer n = args.length > 3 ? parsePositive(args[3]) : null;
                edit(sender, id, n == null ? null : b -> b.withCommands(remove(b.commands(), n)));
            }
            case "delrevert" -> {
                Integer n = args.length > 3 ? parsePositive(args[3]) : null;
                edit(sender, id, n == null ? null : b -> b.withRevertCommands(remove(b.revertCommands(), n)));
            }
            default -> lang.sendLines(sender, "help.admin-bonus");
        }
    }

    private void createBonus(CommandSender sender, String id, String[] args) {
        LangManager lang = plugin.lang();
        if (!ID.matcher(id).matches()) {
            lang.send(sender, "admin.bonus.bad-id");
            return;
        }
        if (plugin.bonusRegistry().get(id).isPresent()) {
            lang.send(sender, "admin.bonus.exists", "id", id);
            return;
        }
        BonusType type = (args.length > 3 ? BonusType.parse(args[3]) : Optional.<BonusType>empty()).orElse(BonusType.DAILY);
        plugin.bonusRegistry().put(new Bonus(id, "&#00ACFF" + id, Material.PAPER, type,
                Duration.ofHours(24).toMillis(), List.of(), List.of(), List.of()));
        lang.send(sender, "admin.bonus.created", "id", id, "type", type.name());
    }

    private void setIcon(CommandSender sender, String id, String[] args) {
        Material material = null;
        if (args.length > 3) {
            material = Material.matchMaterial(args[3]);
        } else if (sender instanceof Player player) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            material = hand.getType().isAir() ? null : hand.getType();
        }
        if (material == null || material.isAir() || !material.isItem()) {
            plugin.lang().send(sender, "admin.bonus.bad-icon");
            return;
        }
        Material icon = material;
        edit(sender, id, b -> b.withIcon(icon));
    }

    /** Применяет изменение к бонусу; null вместо функции означает неверные аргументы. */
    private void edit(CommandSender sender, String id, UnaryOperator<Bonus> change) {
        LangManager lang = plugin.lang();
        if (change == null) {
            lang.sendLines(sender, "help.admin-bonus");
            return;
        }
        Optional<Bonus> bonus = plugin.bonusRegistry().get(id);
        if (bonus.isEmpty()) {
            lang.send(sender, "admin.bonus.not-found", "id", id);
            return;
        }
        plugin.bonusRegistry().put(change.apply(bonus.get()));
        lang.send(sender, "admin.bonus.updated", "id", id);
    }

    private void info(CommandSender sender, Bonus bonus) {
        LangManager lang = plugin.lang();
        lang.send(sender, "admin.bonus.info-head", "id", bonus.id(), "name", bonus.name(),
                "type", bonus.type().name(), "icon", bonus.icon().name(),
                "cooldown", String.valueOf(bonus.cooldownMillis() / 3_600_000.0));
        for (int i = 0; i < bonus.commands().size(); i++) {
            lang.send(sender, "admin.bonus.info-cmd", "n", String.valueOf(i + 1), "cmd", bonus.commands().get(i));
        }
        for (int i = 0; i < bonus.revertCommands().size(); i++) {
            lang.send(sender, "admin.bonus.info-revert", "n", String.valueOf(i + 1), "cmd", bonus.revertCommands().get(i));
        }
    }

    // ------------------------------------------------------------------ утилиты

    private static List<String> append(List<String> list, String line) {
        List<String> copy = new ArrayList<>(list);
        copy.add(line);
        return copy;
    }

    private static List<String> remove(List<String> list, int oneBasedIndex) {
        List<String> copy = new ArrayList<>(list);
        if (oneBasedIndex >= 1 && oneBasedIndex <= copy.size()) {
            copy.remove(oneBasedIndex - 1);
        }
        return copy;
    }

    private static Integer parsePositive(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseDouble(String input) {
        try {
            return Double.parseDouble(input.replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION) || !plugin.isReady()) {
            return List.of();
        }
        if (args.length == 1) {
            return PremiumCommand.filter(ROOT, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        switch (sub) {
            case "give" -> {
                if (args.length == 2) return PremiumCommand.filter(players, args[1]);
                if (args.length == 3) return PremiumCommand.filter(List.copyOf(plugin.settings().plans().keySet()), args[2]);
            }
            case "take", "revoke", "check" -> {
                if (args.length == 2) return PremiumCommand.filter(players, args[1]);
            }
            case "rub" -> {
                if (args.length == 2) return PremiumCommand.filter(RUB, args[1]);
                if (args.length == 3) return PremiumCommand.filter(players, args[2]);
            }
            case "bonus" -> {
                if (args.length == 2) return PremiumCommand.filter(BONUS, args[1]);
                if (args.length == 3 && !args[1].equalsIgnoreCase("create")) {
                    return PremiumCommand.filter(plugin.bonusRegistry().all().stream().map(Bonus::id).toList(), args[2]);
                }
                if (args.length == 4 && (args[1].equalsIgnoreCase("settype") || args[1].equalsIgnoreCase("create"))) {
                    return PremiumCommand.filter(Arrays.stream(BonusType.values()).map(Enum::name).toList(), args[3]);
                }
            }
            default -> { }
        }
        return List.of();
    }
}
