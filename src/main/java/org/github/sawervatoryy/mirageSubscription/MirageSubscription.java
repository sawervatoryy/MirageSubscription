package org.github.sawervatoryy.mirageSubscription;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.github.sawervatoryy.mirageSubscription.command.AdminCommand;
import org.github.sawervatoryy.mirageSubscription.command.PremiumCommand;
import org.github.sawervatoryy.mirageSubscription.config.ConfigManager;
import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.gui.GuiListener;
import org.github.sawervatoryy.mirageSubscription.gui.Menu;
import org.github.sawervatoryy.mirageSubscription.listener.PlayerListener;
import org.github.sawervatoryy.mirageSubscription.model.Bonus;
import org.github.sawervatoryy.mirageSubscription.payment.PaymentService;
import org.github.sawervatoryy.mirageSubscription.placeholder.SubscriptionExpansion;
import org.github.sawervatoryy.mirageSubscription.service.BonusService;
import org.github.sawervatoryy.mirageSubscription.service.ClaimResult;
import org.github.sawervatoryy.mirageSubscription.service.PurchaseService;
import org.github.sawervatoryy.mirageSubscription.service.SubscriptionService;
import org.github.sawervatoryy.mirageSubscription.storage.BonusRegistry;
import org.github.sawervatoryy.mirageSubscription.storage.ProfileRepository;
import org.github.sawervatoryy.mirageSubscription.util.TimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * MirageSubscription — премиум подписка за PlayerPoints или рубли.
 * <p>
 * Главный класс только связывает компоненты. Файловый ввод-вывод выполняется в отдельном
 * IO-потоке; данные профилей живут в памяти, поэтому команды, GUI и плейсхолдеры не блокируют сервер.
 */
public final class MirageSubscription extends JavaPlugin {

    /** Выполняет задачу на главном потоке (сразу, если мы уже на нём). */
    private final Executor mainThread = task -> {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            try {
                Bukkit.getScheduler().runTask(this, task);
            } catch (org.bukkit.plugin.IllegalPluginAccessException e) {
                // плагин выключается — задачу выполнять уже не нужно
            }
        }
    };

    private final LangManager lang = new LangManager();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile boolean ready;

    private ExecutorService io;
    private ConfigManager configManager;
    private ProfileRepository profiles;
    private BonusRegistry bonusRegistry;
    private PaymentService payments;
    private BonusService bonusService;
    private SubscriptionService subscriptions;
    private PurchaseService purchases;
    private Runnable placeholderCleanup;

    @Override
    public void onEnable() {
        // Один поток гарантирует порядок записи файлов.
        io = Executors.newSingleThreadExecutor(r -> new Thread(r, "MirageSubscription-IO"));
        configManager = new ConfigManager(this, io, lang);
        profiles = new ProfileRepository(getLogger(), getDataFolder().toPath().resolve("data.yml"), io);
        bonusRegistry = new BonusRegistry(this, getDataFolder().toPath().resolve("bonuses.yml"), io);
        payments = new PaymentService(this);
        bonusService = new BonusService(this);
        subscriptions = new SubscriptionService(this);
        purchases = new PurchaseService(this);

        bindCommand("premium", new PremiumCommand(this));
        bindCommand("msub", new AdminCommand(this));
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        CompletableFuture.allOf(configManager.reload(), profiles.loadAsync(), bonusRegistry.loadAsync())
                .whenCompleteAsync((v, ex) -> {
                    if (ex != null) {
                        getLogger().log(Level.SEVERE, "Не удалось загрузить данные плагина", ex);
                        getServer().getPluginManager().disablePlugin(this);
                    } else {
                        onReady();
                    }
                }, mainThread);
    }

    private void bindCommand(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Команда " + name + " не объявлена в plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void onReady() {
        payments.init();
        restartTasks();
        hookPlaceholders();
        ready = true;
        getLogger().info("MirageSubscription запущен. GUI: " + (settings().gui().enabled() ? "включён" : "выключен"));
    }

    @Override
    public void onDisable() {
        ready = false;
        cancelTasks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
        }
        if (placeholderCleanup != null) {
            placeholderCleanup.run();
        }
        // Единственное место, где мы дожидаемся IO: сохранение при остановке.
        if (profiles != null) {
            try {
                profiles.flushAsync().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Не удалось дождаться сохранения data.yml", e);
            }
        }
        if (io != null) {
            io.shutdown();
            try {
                io.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Перечитывает config.yml, lang.yml и bonuses.yml, затем перезапускает фоновые задачи. */
    public CompletableFuture<Void> reloadAll() {
        return CompletableFuture.allOf(configManager.reload(), bonusRegistry.loadAsync())
                .thenRunAsync(this::restartTasks, mainThread);
    }

    private void restartTasks() {
        cancelTasks();
        Settings.Timing timing = settings().timing();
        long expiryPeriod = timing.expiryCheckSeconds() * 20L;
        long savePeriod = timing.saveIntervalSeconds() * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (subscriptions.hasDue()) {
                mainThread.execute(subscriptions::expireDue);
            }
        }, expiryPeriod, expiryPeriod));
        tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(this, profiles::flushAsync, savePeriod, savePeriod));
    }

    private void cancelTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private void hookPlaceholders() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI не найден — плейсхолдеры %miragesub_...% недоступны.");
            return;
        }
        SubscriptionExpansion expansion = new SubscriptionExpansion(this);
        expansion.register();
        placeholderCleanup = expansion::unregister;
    }

    // ------------------------------------------------------------------ общие помощники

    /** Читаемое название тарифа по id (для выданных вручную — «custom»). */
    public String planName(String planId) {
        return settings().plan(planId).map(p -> p.name()).orElse(lang.raw("plan.custom"));
    }

    public void playSound(Player player, String key) {
        playSound(player, key, 1f);
    }

    public void playSound(Player player, String key, float pitch) {
        if (settings().gui().sounds().enabled() && key != null && !key.isBlank()) {
            player.playSound(player.getLocation(), key, 1f, pitch);
        }
    }

    /** Сообщение и звук по результату получения бонуса (общее для команды и GUI). */
    public void sendClaimResult(Player player, Bonus bonus, ClaimResult result) {
        switch (result.status()) {
            case SUCCESS -> {
                lang.send(player, "bonuses.claimed", "bonus", bonus.name());
                playSound(player, settings().gui().sounds().success());
                return;
            }
            case NO_SUBSCRIPTION -> lang.send(player, "bonuses.no-subscription");
            case COOLDOWN -> lang.send(player, "bonuses.cooldown",
                    "left", TimeFormat.duration(result.remainingMillis(), lang));
            case NOT_CLAIMABLE -> lang.send(player, "bonuses.not-claimable");
        }
        playSound(player, settings().gui().sounds().error());
    }

    public boolean isReady() {
        return ready;
    }

    public Executor mainThread() {
        return mainThread;
    }

    public LangManager lang() {
        return lang;
    }

    public Settings settings() {
        return configManager.settings();
    }

    public ProfileRepository profiles() {
        return profiles;
    }

    public BonusRegistry bonusRegistry() {
        return bonusRegistry;
    }

    public PaymentService payments() {
        return payments;
    }

    public BonusService bonusService() {
        return bonusService;
    }

    public SubscriptionService subscriptions() {
        return subscriptions;
    }

    public PurchaseService purchases() {
        return purchases;
    }
}
