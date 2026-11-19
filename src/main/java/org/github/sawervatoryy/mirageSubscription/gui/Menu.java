package org.github.sawervatoryy.mirageSubscription.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.github.sawervatoryy.mirageSubscription.MirageSubscription;
import org.github.sawervatoryy.mirageSubscription.config.Settings;
import org.github.sawervatoryy.mirageSubscription.util.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Базовое анимированное меню.
 * <ul>
 *   <li>Открытие: предметы «расходятся» кольцами от центра к краям.</li>
 *   <li>Простой: по рамке бежит волна из цветных стёкол (палитра из config.yml).</li>
 * </ul>
 * Все методы вызываются только с главного потока.
 */
public abstract class Menu implements InventoryHolder {

    protected final MirageSubscription plugin;
    protected final Player player;

    private Inventory inventory;
    private final Map<Integer, ItemStack> layout = new LinkedHashMap<>();
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private final List<Integer> ring = new ArrayList<>();
    private List<ItemStack> panes = List.of();
    private BukkitTask task;
    private int phase;

    protected Menu(MirageSubscription plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract Component title();

    protected abstract int rows();

    /** Заполняет меню через {@link #set(int, ItemStack, Runnable)}. Рамка добавляется автоматически. */
    protected abstract void build();

    /** Вызывается на каждом кадре idle-анимации (для пульсации отдельных предметов). */
    protected void onIdleTick(int phase) {
    }

    // ------------------------------------------------------------------ жизненный цикл

    public final void open() {
        Settings.Gui gui = plugin.settings().gui();
        panes = gui.palette().stream().map(Items::pane).toList();
        inventory = Bukkit.createInventory(this, rows() * 9, title());
        compose();
        player.openInventory(inventory);
        plugin.playSound(player, gui.sounds().open());
        if (gui.animation()) {
            playOpening(gui);
        } else {
            layout.forEach(inventory::setItem);
        }
    }

    /** Перерисовывает содержимое без повторной анимации открытия. */
    protected final void redraw() {
        if (!isOpen()) {
            return;
        }
        inventory.clear();
        compose();
        layout.forEach(inventory::setItem);
    }

    public final void handleClick(int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize() || inventory.getItem(slot) == null) {
            return;
        }
        Runnable action = actions.get(slot);
        if (action != null) {
            plugin.playSound(player, plugin.settings().gui().sounds().click());
            action.run();
        }
    }

    public final void handleClose() {
        cancelTask();
    }

    public final Player viewer() {
        return player;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ помощники для наследников

    protected final void set(int slot, ItemStack item) {
        layout.put(slot, item);
    }

    protected final void set(int slot, ItemStack item, Runnable action) {
        layout.put(slot, item);
        actions.put(slot, action);
    }

    /** Живое обновление одного предмета (например, когда пришёл асинхронный баланс). */
    protected final void update(int slot, ItemStack item) {
        layout.put(slot, item);
        if (isOpen() && inventory.getItem(slot) != null) {
            inventory.setItem(slot, item);
        }
    }

    protected final Inventory inv() {
        return inventory;
    }

    protected final boolean isOpen() {
        return inventory != null && player.isOnline()
                && player.getOpenInventory().getTopInventory().getHolder(false) == this;
    }

    // ------------------------------------------------------------------ внутренности

    private void compose() {
        layout.clear();
        actions.clear();
        ring.clear();
        build();
        fillBorder();
    }

    private void fillBorder() {
        int rows = rows();
        List<Integer> slots = new ArrayList<>();
        for (int c = 0; c < 9; c++) {
            slots.add(c);                       // верх: слева направо
        }
        for (int r = 1; r < rows; r++) {
            slots.add(r * 9 + 8);               // правый край: сверху вниз
        }
        for (int c = 7; c >= 0; c--) {
            slots.add((rows - 1) * 9 + c);      // низ: справа налево
        }
        for (int r = rows - 2; r >= 1; r--) {
            slots.add(r * 9);                   // левый край: снизу вверх
        }
        for (int slot : slots) {
            if (!layout.containsKey(slot)) {
                layout.put(slot, panes.get(0));
                ring.add(slot);
            }
        }
    }

    private void playOpening(Settings.Gui gui) {
        double centerRow = (rows() - 1) / 2.0;
        TreeMap<Long, List<Integer>> waves = new TreeMap<>();
        for (int slot : layout.keySet()) {
            double dr = (slot / 9 - centerRow) * 1.5;
            double dc = slot % 9 - 4;
            waves.computeIfAbsent(Math.round(Math.hypot(dr, dc)), k -> new ArrayList<>()).add(slot);
        }
        List<List<Integer>> steps = new ArrayList<>(waves.values());
        int[] index = {0};
        cancelTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isOpen()) {
                cancelTask();
                return;
            }
            if (index[0] < steps.size()) {
                for (int slot : steps.get(index[0])) {
                    inventory.setItem(slot, layout.get(slot));
                }
                float pitch = 0.6f + 1.4f * index[0] / Math.max(1, steps.size() - 1);
                plugin.playSound(player, gui.sounds().step(), pitch);
                index[0]++;
            } else {
                cancelTask();
                startIdle(gui);
            }
        }, 0L, gui.openStepTicks());
    }

    private void startIdle(Settings.Gui gui) {
        if (gui.idleStepTicks() <= 0 || panes.size() < 2 || ring.isEmpty()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isOpen()) {
                cancelTask();
                return;
            }
            phase++;
            for (int i = 0; i < ring.size(); i++) {
                inventory.setItem(ring.get(i), panes.get((i + phase) % panes.size()));
            }
            onIdleTick(phase);
        }, gui.idleStepTicks(), gui.idleStepTicks());
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
