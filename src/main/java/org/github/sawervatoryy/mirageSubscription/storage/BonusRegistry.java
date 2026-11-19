package org.github.sawervatoryy.mirageSubscription.storage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.github.sawervatoryy.mirageSubscription.model.Bonus;
import org.github.sawervatoryy.mirageSubscription.model.BonusType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Реестр бонусов (bonuses.yml). Редактируется и в файле, и командами в игре.
 * Карта бонусов неизменяема и подменяется целиком, поэтому читать её можно из любого потока.
 */
public final class BonusRegistry {

    private final Plugin plugin;
    private final Logger log;
    private final Path file;
    private final ExecutorService io;
    private volatile Map<String, Bonus> bonuses = Map.of();

    public BonusRegistry(Plugin plugin, Path file, ExecutorService io) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.file = file;
        this.io = io;
    }

    public CompletableFuture<Void> loadAsync() {
        return CompletableFuture.runAsync(this::load, io);
    }

    private void load() {
        if (!Files.exists(file)) {
            plugin.saveResource("bonuses.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("bonuses");
        Map<String, Bonus> loaded = new LinkedHashMap<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                BonusType type = BonusType.parse(s.getString("type", "DAILY")).orElse(BonusType.DAILY);
                Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
                if (icon == null || icon.isAir() || !icon.isItem()) {
                    icon = Material.PAPER;
                }
                String key = id.toLowerCase(Locale.ROOT);
                loaded.put(key, new Bonus(key, s.getString("name", id), icon, type,
                        Duration.ofMinutes((long) (s.getDouble("cooldown-hours", 24) * 60)).toMillis(),
                        s.getStringList("lore"), s.getStringList("commands"), s.getStringList("revert-commands")));
            }
        }
        this.bonuses = Collections.unmodifiableMap(loaded);
    }

    public Collection<Bonus> all() {
        return bonuses.values();
    }

    public Optional<Bonus> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(bonuses.get(id.toLowerCase(Locale.ROOT)));
    }

    public synchronized void put(Bonus bonus) {
        Map<String, Bonus> copy = new LinkedHashMap<>(bonuses);
        copy.put(bonus.id(), bonus);
        bonuses = Collections.unmodifiableMap(copy);
        saveAsync();
    }

    public synchronized boolean remove(String id) {
        Map<String, Bonus> copy = new LinkedHashMap<>(bonuses);
        if (copy.remove(id.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        bonuses = Collections.unmodifiableMap(copy);
        saveAsync();
        return true;
    }

    private void saveAsync() {
        Map<String, Bonus> snapshot = bonuses;
        CompletableFuture.runAsync(() -> write(snapshot), io)
                .exceptionally(ex -> {
                    log.log(Level.SEVERE, "Не удалось сохранить bonuses.yml", ex);
                    return null;
                });
    }

    private void write(Map<String, Bonus> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "MirageSubscription — бонусы подписки.",
                "Файл можно править руками (затем /msub reload) или командами /msub bonus ...",
                "type: ACTIVATE (при каждой покупке) | DAILY (забирается вручную по кулдауну) | PASSIVE (команды при старте и revert-commands при окончании)",
                "В командах доступен плейсхолдер %player%."));
        for (Bonus b : snapshot.values()) {
            String base = "bonuses." + b.id();
            yaml.set(base + ".name", b.name());
            yaml.set(base + ".icon", b.icon().name());
            yaml.set(base + ".type", b.type().name());
            yaml.set(base + ".cooldown-hours", b.cooldownMillis() / 3_600_000.0);
            yaml.set(base + ".lore", b.lore());
            yaml.set(base + ".commands", b.commands());
            yaml.set(base + ".revert-commands", b.revertCommands());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, yaml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
