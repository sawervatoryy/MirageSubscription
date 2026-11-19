package org.github.sawervatoryy.mirageSubscription.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Данные игрока: подписка, рублёвый баланс, время последних получений бонусов.
 * Потокобезопасен: изменяемое состояние защищено монитором объекта, карта клеймов — concurrent.
 */
public final class Profile {

    /** Неизменяемый срез состояния для асинхронной записи на диск. */
    public record Snapshot(UUID uuid, String name, long expiresAt, String planId, int rubles,
                           Map<String, Long> claims) {
    }

    private final UUID uuid;
    private final Map<String, Long> claims = new ConcurrentHashMap<>();
    private volatile String name;
    private long expiresAt;   // guarded by this; 0 — подписки нет
    private String planId;    // guarded by this
    private int rubles;       // guarded by this

    public Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? uuid.toString() : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Восстановление состояния при загрузке с диска. */
    public synchronized void restore(long expiresAt, String planId, int rubles) {
        this.expiresAt = expiresAt;
        this.planId = planId;
        this.rubles = Math.max(0, rubles);
    }

    public synchronized boolean isActive(long now) {
        return expiresAt > now;
    }

    /** Срок истёк, но обработка окончания ещё не выполнена. */
    public synchronized boolean isDue(long now) {
        return expiresAt > 0 && expiresAt <= now;
    }

    public synchronized long expiresAt() {
        return expiresAt;
    }

    public synchronized long remaining(long now) {
        return Math.max(0, expiresAt - now);
    }

    public synchronized String planId() {
        return planId;
    }

    /** Продлевает подписку (если активна — от текущего окончания, иначе от now). */
    public synchronized void extend(long millis, String planId, long now) {
        this.expiresAt = Math.max(expiresAt, now) + millis;
        this.planId = planId;
    }

    /** Уменьшает срок. @return true, если подписка в итоге закончилась. */
    public synchronized boolean reduce(long millis, long now) {
        if (expiresAt <= now) {
            return false;
        }
        expiresAt -= millis;
        if (expiresAt <= now) {
            clear();
            return true;
        }
        return false;
    }

    /** Атомарно завершает просроченную подписку. @return true, если завершение выполнено именно этим вызовом. */
    public synchronized boolean expireIfDue(long now) {
        if (isDue(now)) {
            clear();
            return true;
        }
        return false;
    }

    public synchronized void clear() {
        expiresAt = 0;
        planId = null;
    }

    public synchronized int rubles() {
        return rubles;
    }

    public synchronized void setRubles(int value) {
        rubles = Math.max(0, value);
    }

    public synchronized void addRubles(int amount) {
        rubles = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) rubles + amount));
    }

    /** @return false, если на балансе не хватает средств. */
    public synchronized boolean takeRubles(int amount) {
        if (amount < 0 || rubles < amount) {
            return false;
        }
        rubles -= amount;
        return true;
    }

    public Long lastClaim(String bonusId) {
        return claims.get(bonusId);
    }

    public void setClaim(String bonusId, long time) {
        claims.put(bonusId, time);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(uuid, name, expiresAt, planId, rubles, new HashMap<>(claims));
    }
}
