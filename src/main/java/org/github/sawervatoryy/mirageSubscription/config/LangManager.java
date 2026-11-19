package org.github.sawervatoryy.mirageSubscription.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.github.sawervatoryy.mirageSubscription.util.Text;

import java.util.List;

/**
 * Доступ к сообщениям из lang.yml. Экземпляр конфигурации подменяется целиком (volatile),
 * поэтому чтение безопасно из любого потока, в том числе из PlaceholderAPI.
 */
public final class LangManager {

    private volatile YamlConfiguration lang = new YamlConfiguration();

    public void load(YamlConfiguration configuration) {
        this.lang = configuration;
    }

    /** Сырая строка без подстановок; при отсутствии ключа возвращает маркер ошибки. */
    public String raw(String key) {
        String value = lang.getString(key);
        return value == null ? "&c[lang:" + key + "]" : value;
    }

    public List<String> rawList(String key) {
        List<String> list = lang.getStringList(key);
        return list.isEmpty() ? List.of("&c[lang:" + key + "]") : list;
    }

    /** Строка с подстановкой {prefix} и пар ключ/значение. */
    public String format(String key, String... pairs) {
        return Text.apply(raw(key).replace("{prefix}", rawPrefix()), pairs);
    }

    public List<String> formatList(String key, String... pairs) {
        String prefix = rawPrefix();
        return rawList(key).stream()
                .map(line -> Text.apply(line.replace("{prefix}", prefix), pairs))
                .toList();
    }

    public Component component(String key, String... pairs) {
        return Text.color(format(key, pairs));
    }

    public List<Component> components(String key, String... pairs) {
        return Text.color(formatList(key, pairs));
    }

    public void send(CommandSender target, String key, String... pairs) {
        target.sendMessage(component(key, pairs));
    }

    public void sendLines(CommandSender target, String key, String... pairs) {
        components(key, pairs).forEach(target::sendMessage);
    }

    private String rawPrefix() {
        String prefix = lang.getString("prefix");
        return prefix == null ? "" : prefix;
    }
}
