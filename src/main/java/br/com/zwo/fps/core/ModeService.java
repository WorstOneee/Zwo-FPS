package br.com.zwo.fps.core;

import br.com.zwo.fps.config.FpsConfig;
import com.destroystokyo.paper.ClientOption;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Decides which profile a player gets and applies send distance + weather. */
public final class ModeService {
    private final Plugin plugin;
    private final Supplier<FpsConfig> config;
    private final PlayerPrefs prefs;
    private final Map<UUID, PlayerState> states;
    private final CullingService culling;

    public ModeService(Plugin plugin, Supplier<FpsConfig> config, PlayerPrefs prefs,
                       Map<UUID, PlayerState> states, CullingService culling) {
        this.plugin = plugin;
        this.config = config;
        this.prefs = prefs;
        this.states = states;
        this.culling = culling;
    }

    public PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), PlayerState::new);
    }

    /** Profile the player should have right now, or null for "nothing". */
    public FpsConfig.Profile resolve(Player player) {
        FpsConfig cfg = config.get();
        if (cfg.isWorldDisabled(player.getWorld().getName())) return null;
        String mode = prefs.mode(player);
        if (mode.equals(FpsConfig.MODE_OFF)) return null;
        if (mode.equals(FpsConfig.MODE_AUTO)) return resolveAuto(player, cfg);
        return cfg.profile(mode);
    }

    public FpsConfig.Profile resolveAuto(Player player, FpsConfig cfg) {
        int clientView = player.getClientViewDistance();
        ClientOption.ParticleVisibility particles = particles(player);
        for (Map.Entry<String, FpsConfig.AutoRule> entry : cfg.autoRules().entrySet()) {
            FpsConfig.AutoRule rule = entry.getValue();
            boolean byDistance = rule.maxClientViewDistance() > 0 && clientView > 0 && clientView <= rule.maxClientViewDistance();
            boolean byParticles = particles != null && rule.particles().contains(particles);
            if (byDistance || byParticles) return cfg.profile(entry.getKey());
        }
        return null;
    }

    public static ClientOption.ParticleVisibility particles(Player player) {
        try {
            return player.getClientOption(ClientOption.PARTICLE_VISIBILITY);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Re-resolve and apply everything for this player. */
    public void apply(Player player) {
        if (!player.isOnline()) return;
        PlayerState state = state(player);
        FpsConfig.Profile next = resolve(player);
        FpsConfig.Profile previous = state.profile;
        // Profiles are immutable and rebuilt on reload, so identity tells us if anything changed.
        if (previous != next) {
            culling.showAll(player, state);
            state.profile = next;
            culling.scheduleSoon(state, 1);
        }
        applyView(player, state, next);
    }

    private void applyView(Player player, PlayerState state, FpsConfig.Profile profile) {
        FpsConfig cfg = config.get();
        int desired = profile == null || !cfg.manageSendDistance() ? -1 : profile.sendViewDistance();
        if (desired != state.appliedSendDistance) {
            try {
                player.setSendViewDistance(desired);
                state.appliedSendDistance = desired;
            } catch (Throwable t) {
                plugin.getLogger().warning("Nao foi possivel ajustar send-view-distance de " + player.getName() + ": " + t.getMessage());
            }
        }
        boolean wantClear = profile != null && profile.clearWeather() && !prefs.showRain(player);
        if (wantClear && !state.weatherApplied) {
            player.setPlayerWeather(WeatherType.CLEAR);
            state.weatherApplied = true;
        } else if (!wantClear && state.weatherApplied) {
            player.resetPlayerWeather();
            state.weatherApplied = false;
        }
    }

    /** Undo everything (plugin disable). */
    public void restore(Player player) {
        PlayerState state = states.get(player.getUniqueId());
        if (state == null) return;
        culling.showAll(player, state);
        state.profile = null;
        applyView(player, state, null);
    }
}
