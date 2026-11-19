package org.github.sawervatoryy.mirageSubscription.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/** Загружает config.yml и lang.yml в IO-потоке и атомарно публикует результат. */
public final class ConfigManager {

    private final MirageSubscription plugin;
    private final ExecutorService io;
    private final LangManager lang;
    private volatile Settings settings;

    public ConfigManager(MirageSubscription plugin, ExecutorService io, LangManager lang) {
        this.plugin = plugin;
        this.io = io;
        this.lang = lang;
    }

    public Settings settings() {
        return settings;
    }

    /** Асинхронно (пере)читывает конфиги. Завершается после публикации новых значений. */
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> {
            ensureDefault("config.yml");
            ensureDefault("lang.yml");
            YamlConfiguration cfg = read("config.yml");
            YamlConfiguration langCfg = read("lang.yml");
            this.settings = Settings.parse(cfg, plugin.getLogger());
            this.lang.load(langCfg);
        }, io);
    }

    private void ensureDefault(String name) {
        if (!new File(plugin.getDataFolder(), name).exists()) {
            plugin.saveResource(name, false);
        }
    }

    /** Читает файл, подмешивая значения по умолчанию из jar (новые ключи не требуют пересоздания файла). */
    private YamlConfiguration read(String name) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось прочитать значения по умолчанию для " + name + ": " + e.getMessage());
        }
        return yaml;
    }
}
