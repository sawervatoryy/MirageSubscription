package org.github.sawervatoryy.mirageSubscription.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.github.sawervatoryy.mirageSubscription.model.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Хранилище профилей: все данные в памяти (чтение с главного потока и из PlaceholderAPI без IO),
 * запись в data.yml выполняется асинхронно в одном IO-потоке (порядок записей сохраняется).
 */
public final class ProfileRepository {

    private final Logger log;
    private final Path file;
    private final ExecutorService io;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();

    public ProfileRepository(Logger log, Path file, ExecutorService io) {
        this.log = log;
        this.file = file;
        this.io = io;
    }

    public CompletableFuture<Void> loadAsync() {
        return CompletableFuture.runAsync(this::load, io);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("profiles");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                log.warning("data.yml: пропускаю некорректный UUID " + key);
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Profile profile = new Profile(uuid, s.getString("name"));
            profile.restore(s.getLong("expires-at"), s.getString("plan"), s.getInt("rubles"));
            ConfigurationSection claims = s.getConfigurationSection("claims");
            if (claims != null) {
                for (String bonusId : claims.getKeys(false)) {
                    profile.setClaim(bonusId, claims.getLong(bonusId));
                }
            }
            profiles.put(uuid, profile);
        }
        log.info("Загружено профилей: " + profiles.size());
    }

    public Optional<Profile> find(UUID uuid) {
        return Optional.ofNullable(profiles.get(uuid));
    }

    /** Возвращает профиль, создавая его при необходимости; имя обновляется, если оно изменилось. */
    public Profile getOrCreate(UUID uuid, String name) {
        Profile profile = profiles.computeIfAbsent(uuid, id -> new Profile(id, name));
        if (name != null && !name.equals(profile.name())) {
            profile.setName(name);
        }
        markDirty();
        return profile;
    }

    public Collection<Profile> all() {
        return profiles.values();
    }

    public void markDirty() {
        dirty.set(true);
    }

    /** Асинхронно сохраняет данные, если они менялись. */
    public CompletableFuture<Void> flushAsync() {
        if (!dirty.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }
        List<Profile.Snapshot> snapshots = profiles.values().stream().map(Profile::snapshot).toList();
        return CompletableFuture.runAsync(() -> write(snapshots), io).exceptionally(ex -> {
            dirty.set(true);
            log.log(Level.SEVERE, "Не удалось сохранить data.yml", ex);
            return null;
        });
    }

    private void write(List<Profile.Snapshot> snapshots) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Profile.Snapshot s : snapshots) {
            String base = "profiles." + s.uuid();
            yaml.set(base + ".name", s.name());
            yaml.set(base + ".expires-at", s.expiresAt());
            yaml.set(base + ".plan", s.planId());
            yaml.set(base + ".rubles", s.rubles());
            s.claims().forEach((id, time) -> yaml.set(base + ".claims." + id, time));
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
