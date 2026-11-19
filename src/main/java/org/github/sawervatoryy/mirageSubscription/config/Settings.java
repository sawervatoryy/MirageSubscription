package org.github.sawervatoryy.mirageSubscription.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.github.sawervatoryy.mirageSubscription.model.Currency;
import org.github.sawervatoryy.mirageSubscription.model.Plan;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Типизированный неизменяемый срез config.yml. */
public record Settings(
        Gui gui,
        Timing timing,
        CurrencyInfo points,
        CurrencyInfo rubles,
        Placeholders placeholders,
        DateTimeFormatter dateFormat,
        Map<String, Plan> plans) {

    public record Gui(boolean enabled, boolean animation, int openStepTicks, int idleStepTicks,
                      List<Material> palette, Sounds sounds, Icons icons) {
    }

    public record Sounds(boolean enabled, String open, String step, String click, String success, String error) {
    }

    public record Icons(Material statusActive, Material statusInactive, Material bonuses, Material close,
                        Material back, Material points, Material rubles, Material unavailable) {
    }

    public record Timing(int expiryCheckSeconds, int notifyBeforeDays, int saveIntervalSeconds) {
    }

    /** @param storeUrl ссылка на донат-магазин (используется для рублей) */
    public record CurrencyInfo(boolean enabled, String name, String symbol, String storeUrl) {
    }

    public record Placeholders(String active, String inactive, String none) {
    }

    private static final int[] DEFAULT_PLAN_SLOTS = {20, 22, 24, 21, 23, 19, 25};

    public CurrencyInfo currency(Currency currency) {
        return currency == Currency.POINTS ? points : rubles;
    }

    /** Ищет тариф по id без учёта регистра. */
    public Optional<Plan> plan(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(plans.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Цена в виде «450 поинтов» / «299₽»; для недоступной оплаты возвращает {@code unavailable}. */
    public String formatPrice(int price, Currency currency, String unavailable) {
        if (price <= 0) {
            return unavailable;
        }
        CurrencyInfo info = currency(currency);
        return currency == Currency.RUBLES ? price + info.symbol() : price + " " + info.name();
    }

    public static Settings parse(FileConfiguration cfg, Logger log) {
        List<Material> palette = new ArrayList<>();
        for (String name : cfg.getStringList("gui.animation.palette")) {
            palette.add(material(name, Material.LIGHT_BLUE_STAINED_GLASS_PANE, log, "gui.animation.palette"));
        }
        if (palette.isEmpty()) {
            palette.add(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        }

        Sounds sounds = new Sounds(
                cfg.getBoolean("gui.sounds.enabled", true),
                cfg.getString("gui.sounds.open", "block.ender_chest.open"),
                cfg.getString("gui.sounds.step", "block.note_block.chime"),
                cfg.getString("gui.sounds.click", "ui.button.click"),
                cfg.getString("gui.sounds.success", "entity.player.levelup"),
                cfg.getString("gui.sounds.error", "entity.villager.no"));

        Icons icons = new Icons(
                material(cfg.getString("gui.icons.status-active"), Material.NETHER_STAR, log, "gui.icons.status-active"),
                material(cfg.getString("gui.icons.status-inactive"), Material.COAL, log, "gui.icons.status-inactive"),
                material(cfg.getString("gui.icons.bonuses"), Material.CHEST, log, "gui.icons.bonuses"),
                material(cfg.getString("gui.icons.close"), Material.BARRIER, log, "gui.icons.close"),
                material(cfg.getString("gui.icons.back"), Material.ARROW, log, "gui.icons.back"),
                material(cfg.getString("gui.icons.points"), Material.AMETHYST_SHARD, log, "gui.icons.points"),
                material(cfg.getString("gui.icons.rubles"), Material.EMERALD, log, "gui.icons.rubles"),
                material(cfg.getString("gui.icons.unavailable"), Material.RED_STAINED_GLASS_PANE, log, "gui.icons.unavailable"));

        Gui gui = new Gui(
                cfg.getBoolean("gui.enabled", true),
                cfg.getBoolean("gui.animation.enabled", true),
                Math.max(1, cfg.getInt("gui.animation.open-step-ticks", 2)),
                cfg.getInt("gui.animation.idle-step-ticks", 4),
                List.copyOf(palette), sounds, icons);

        Timing timing = new Timing(
                Math.max(5, cfg.getInt("settings.expiry-check-seconds", 30)),
                Math.max(0, cfg.getInt("settings.notify-before-days", 3)),
                Math.max(10, cfg.getInt("settings.save-interval-seconds", 60)));

        CurrencyInfo points = new CurrencyInfo(
                cfg.getBoolean("currencies.points.enabled", true),
                cfg.getString("currencies.points.name", "поинтов"),
                "", "");
        CurrencyInfo rubles = new CurrencyInfo(
                cfg.getBoolean("currencies.rubles.enabled", true),
                cfg.getString("currencies.rubles.name", "руб."),
                cfg.getString("currencies.rubles.symbol", "₽"),
                cfg.getString("currencies.rubles.store-url", ""));

        Placeholders placeholders = new Placeholders(
                cfg.getString("placeholders.active", "&#00ACFFPremium"),
                cfg.getString("placeholders.inactive", "&7Нет"),
                cfg.getString("placeholders.none", "-"));

        DateTimeFormatter dateFormat;
        try {
            dateFormat = DateTimeFormatter.ofPattern(cfg.getString("settings.date-format", "dd.MM.yyyy HH:mm"))
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            log.warning("Некорректный settings.date-format, использую dd.MM.yyyy HH:mm");
            dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        }

        return new Settings(gui, timing, points, rubles, placeholders, dateFormat, parsePlans(cfg, log));
    }

    private static Map<String, Plan> parsePlans(FileConfiguration cfg, Logger log) {
        Map<String, Plan> plans = new LinkedHashMap<>();
        ConfigurationSection root = cfg.getConfigurationSection("plans");
        if (root == null) {
            log.warning("В config.yml нет секции plans — тарифов не будет.");
            return Collections.emptyMap();
        }
        int index = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            int defaultSlot = DEFAULT_PLAN_SLOTS[Math.min(index, DEFAULT_PLAN_SLOTS.length - 1)];
            int slot = s.getInt("slot", defaultSlot);
            if (slot < 0 || slot > 53) {
                log.warning("Тариф " + id + ": слот " + slot + " вне диапазона 0-53, использую " + defaultSlot);
                slot = defaultSlot;
            }
            String key = id.toLowerCase(Locale.ROOT);
            plans.put(key, new Plan(
                    key,
                    s.getString("name", id),
                    Math.max(1, s.getInt("days", 30)),
                    s.getInt("price.points", -1),
                    s.getInt("price.rubles", -1),
                    material(s.getString("icon"), Material.GOLD_INGOT, log, "plans." + id + ".icon"),
                    slot,
                    s.getStringList("lore")));
            index++;
        }
        return Collections.unmodifiableMap(plans);
    }

    static Material material(String name, Material fallback, Logger log, String path) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isItem()) {
            log.warning("Неизвестный предмет '" + name + "' в " + path + ", использую " + fallback);
            return fallback;
        }
        return material;
    }
}
