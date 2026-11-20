package br.com.zwo.fps.listener;

import br.com.zwo.fps.ZwoFPS;
import br.com.zwo.fps.config.FpsConfig;
import br.com.zwo.fps.core.ModeService;
import br.com.zwo.fps.core.PlayerState;
import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class FpsListener implements Listener {
    private final ZwoFPS plugin;

    public FpsListener(ZwoFPS plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.modes().state(player);
        plugin.culling().track(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.modes().apply(player);
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) maybeHint(player);
        }, 80L);
    }

    private void maybeHint(Player player) {
        FpsConfig cfg = plugin.cfg();
        if (!cfg.hintEnabled() || !player.hasPermission("zwofps.use")) return;
        PlayerState state = plugin.modes().state(player);
        if (state.profile() != null) return; // already being helped
        if (cfg.isWorldDisabled(player.getWorld().getName())) return;
        int view = player.getClientViewDistance();
        ClientOption.ParticleVisibility particles = ModeService.particles(player);
        boolean lowEnd = (view > 0 && view <= cfg.hintMaxViewDistance())
                || particles == ClientOption.ParticleVisibility.MINIMAL;
        if (!lowEnd) return;
        long now = System.currentTimeMillis();
        if (now - plugin.prefs().lastHint(player) < cfg.hintCooldownHours() * 3_600_000L) return;
        plugin.prefs().setLastHint(player, now);
        plugin.text().send(player, "low-end-hint");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerState state = plugin.states().remove(event.getPlayer().getUniqueId());
        plugin.culling().untrack(event.getPlayer().getUniqueId(), state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.modes().apply(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerState state = plugin.states().get(event.getPlayer().getUniqueId());
        if (state != null) plugin.culling().scheduleSoon(state, 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerState state = plugin.states().get(event.getPlayer().getUniqueId());
        if (state != null) plugin.culling().scheduleSoon(state, 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientOptions(PlayerClientOptionsChangeEvent event) {
        if (!event.hasViewDistanceChanged() && !event.hasParticleVisibilityChanged()) return;
        Player player = event.getPlayer();
        // The option values are applied to the player right after the event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.prefs().mode(player).equals(FpsConfig.MODE_AUTO)) {
                plugin.modes().apply(player);
            }
        });
    }

    @EventHandler
    public void onEntityAdded(EntityAddToWorldEvent event) {
        plugin.culling().onEntityAdded(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveEvent event) {
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.UNLOAD || cause == EntityRemoveEvent.Cause.PLAYER_QUIT) return;
        plugin.culling().onEntityGone(event.getEntity().getUniqueId());
    }
}
