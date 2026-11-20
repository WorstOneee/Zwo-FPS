package br.com.zwo.fps.core;

import br.com.zwo.fps.config.FpsConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Player preferences stored in the player's own PersistentDataContainer.
 * They are saved by the server together with the player data file, so there is
 * no extra file and no disk IO on the main thread.
 */
public final class PlayerPrefs {
    private final NamespacedKey modeKey;
    private final NamespacedKey hintKey;
    private final NamespacedKey rainKey;
    private final Supplier<FpsConfig> config;

    public PlayerPrefs(Plugin plugin, Supplier<FpsConfig> config) {
        this.modeKey = new NamespacedKey(plugin, "mode");
        this.hintKey = new NamespacedKey(plugin, "last_hint");
        this.rainKey = new NamespacedKey(plugin, "show_rain");
        this.config = config;
    }

    /** The mode the player chose, or the configured default. Always a valid mode id. */
    public String mode(Player player) {
        String raw = player.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        FpsConfig cfg = config.get();
        if (raw != null) {
            raw = raw.toLowerCase(Locale.ROOT);
            if (cfg.isValidMode(raw)) return raw;
        }
        return cfg.defaultMode();
    }

    public boolean hasChosenMode(Player player) {
        return player.getPersistentDataContainer().has(modeKey, PersistentDataType.STRING);
    }

    public void setMode(Player player, String mode) {
        player.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
    }

    public long lastHint(Player player) {
        Long v = player.getPersistentDataContainer().get(hintKey, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    public void setLastHint(Player player, long millis) {
        player.getPersistentDataContainer().set(hintKey, PersistentDataType.LONG, millis);
    }

    /** True when the player asked to keep seeing rain even if the profile clears it. */
    public boolean showRain(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Byte v = pdc.get(rainKey, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    public void setShowRain(Player player, boolean show) {
        player.getPersistentDataContainer().set(rainKey, PersistentDataType.BYTE, (byte) (show ? 1 : 0));
    }
}
