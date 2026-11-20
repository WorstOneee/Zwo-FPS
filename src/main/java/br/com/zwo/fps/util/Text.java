package br.com.zwo.fps.util;

import br.com.zwo.fps.config.FpsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.function.Supplier;

/** MiniMessage helpers. Messages are read from the current config on every call. */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Supplier<FpsConfig> config;

    public Text(Supplier<FpsConfig> config) {
        this.config = config;
    }

    public static Component mm(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw == null ? "" : raw, resolvers);
    }

    /** For item names/lore: no default italics. */
    public static Component item(String raw, TagResolver... resolvers) {
        return mm(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static TagResolver ph(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** Placeholder whose value is itself MiniMessage (e.g. a profile name with colors). */
    public static TagResolver phMm(String key, String miniMessage) {
        return Placeholder.component(key, mm(miniMessage));
    }

    public Component msg(String key, TagResolver... resolvers) {
        var section = config.get().messages();
        String prefix = section == null ? "" : section.getString("prefix", "");
        String raw = section == null ? null : section.getString(key);
        if (raw == null) raw = "<red>Mensagem ausente: " + key;
        return mm(prefix + raw, resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(msg(key, resolvers));
    }

    /** Sends a raw MiniMessage line with the prefix. */
    public void sendRaw(CommandSender to, String miniMessage, TagResolver... resolvers) {
        var section = config.get().messages();
        String prefix = section == null ? "" : section.getString("prefix", "");
        to.sendMessage(mm(prefix + miniMessage, resolvers));
    }
}
