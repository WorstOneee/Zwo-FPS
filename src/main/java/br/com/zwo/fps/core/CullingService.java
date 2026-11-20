package br.com.zwo.fps.core;

import br.com.zwo.fps.config.FpsConfig;
import br.com.zwo.fps.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-player entity culling.
 *
 * Each player is re-evaluated every {@code scan-interval-ticks}. Work is spread over
 * ticks with a per-tick player and time budget. For every category active in the
 * player's profile an entity is hidden when it is farther than {@code hide-beyond}
 * or when it falls outside the {@code max-visible} nearest of its category. Nothing
 * inside {@code always-visible-radius} is ever hidden.
 *
 * Hiding uses {@link Player#hideEntity}, which Paper keys by entity UUID, so state
 * survives chunk unloads. We therefore never forget a hidden UUID while the entity
 * can come back, and entities we could not show because they were unloaded are shown
 * as soon as they are added to a world again.
 */
public final class CullingService {
    private final Plugin plugin;
    private final Supplier<FpsConfig> config;
    private final Map<UUID, PlayerState> states;

    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    /** entity -> players currently hiding it (reverse index for cleanup). */
    private final Map<UUID, Set<UUID>> hiddenBy = new HashMap<>();
    /** entity -> players that must see it again once it is loaded. */
    private final Map<UUID, Set<UUID>> pendingShow = new HashMap<>();

    private BukkitTask task;
    private long tick;
    private double avgScanMicros;
    private long scansTotal;

    private record Candidate(Entity entity, double distance, boolean wasHidden) {}

    public CullingService(Plugin plugin, Supplier<FpsConfig> config, Map<UUID, PlayerState> states) {
        this.plugin = plugin;
        this.config = config;
        this.states = states;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long currentTick() { return tick; }
    public double averageScanMicros() { return avgScanMicros; }
    public long scansTotal() { return scansTotal; }
    public int pendingShowCount() { return pendingShow.size(); }

    public void track(Player player) {
        if (!queue.contains(player.getUniqueId())) queue.add(player.getUniqueId());
    }

    /** Player left: Paper discards the visibility state with the Player object, so just forget it. */
    public void untrack(UUID playerId, PlayerState state) {
        queue.remove(playerId);
        if (state != null) {
            for (UUID entityId : state.hidden) {
                removeIndex(hiddenBy, entityId, playerId);
            }
            state.hidden.clear();
        }
        pendingShow.values().forEach(set -> set.remove(playerId));
        pendingShow.values().removeIf(Set::isEmpty);
    }

    /** Ask for a scan of this player within the given number of ticks. */
    public void scheduleSoon(PlayerState state, int ticks) {
        state.nextScanTick = Math.min(state.nextScanTick, tick + Math.max(0, ticks));
    }

    private void tick() {
        tick++;
        FpsConfig cfg = config.get();
        long budgetNanos = (long) (cfg.maxMillisPerTick() * 1_000_000L);
        long start = System.nanoTime();
        int processed = 0;
        int size = queue.size();
        boolean slow = cfg.slowDownMspt() > 0 && Bukkit.getAverageTickTime() > cfg.slowDownMspt();
        int interval = cfg.scanIntervalTicks() * (slow ? 2 : 1);

        for (int i = 0; i < size; i++) {
            if (processed >= cfg.maxPlayersPerTick() || System.nanoTime() - start > budgetNanos) break;
            UUID id = queue.poll();
            if (id == null) break;
            queue.add(id);
            PlayerState state = states.get(id);
            if (state == null || state.nextScanTick > tick) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            state.nextScanTick = tick + interval;
            FpsConfig.Profile profile = state.profile;
            if (profile == null || !profile.hasCulling()) continue;
            try {
                scan(player, state, profile, cfg);
            } catch (Throwable t) {
                plugin.getLogger().warning("Falha no scan de " + player.getName() + ": " + t);
            }
            processed++;
        }
    }

    private void scan(Player player, PlayerState state, FpsConfig.Profile profile, FpsConfig cfg) {
        long t0 = System.nanoTime();
        World world = player.getWorld();
        Location origin = player.getLocation();
        double ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        double r = cfg.scanRadius();
        FpsConfig.Rule[] rules = profile.rules();
        int categories = cfg.categories().size();

        Collection<Entity> found = world.getNearbyEntities(origin, r, cfg.scanHeight(), r, e -> {
            FpsConfig.Category cat = cfg.categoryOf(e.getType());
            return cat != null && cat.index() < rules.length && rules[cat.index()] != null;
        });

        @SuppressWarnings("unchecked")
        List<Candidate>[] buckets = new List[categories];
        for (Entity e : found) {
            if (e == player || !e.isValid()) continue;
            FpsConfig.Category cat = cfg.categoryOf(e.getType());
            if (cat == null) continue;
            double dx = e.getX() - ox, dy = e.getY() - oy, dz = e.getZ() - oz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            List<Candidate> list = buckets[cat.index()];
            if (list == null) buckets[cat.index()] = list = new ArrayList<>();
            list.add(new Candidate(e, d, state.hidden.contains(e.getUniqueId())));
        }

        double always = cfg.alwaysVisibleRadius();
        double hyst = cfg.hysteresis();
        int[] hiddenPer = new int[categories];
        int[] seenPer = new int[categories];
        Set<UUID> seen = new HashSet<>(found.size() * 2);
        List<Entity> toHide = new ArrayList<>();
        Set<UUID> keepHidden = new HashSet<>();

        for (int c = 0; c < categories; c++) {
            List<Candidate> list = buckets[c];
            FpsConfig.Rule rule = c < rules.length ? rules[c] : null;
            if (list == null || rule == null) continue;
            seenPer[c] = list.size();
            if (rule.maxVisible() > 0 && list.size() > rule.maxVisible()) {
                // Already-visible entities get a small bonus so the budget does not flicker.
                list.sort(Comparator.comparingDouble(cand -> cand.distance() + (cand.wasHidden() ? hyst : 0)));
            }
            int visible = 0;
            for (Candidate cand : list) {
                Entity e = cand.entity();
                seen.add(e.getUniqueId());
                boolean hide = false;
                if (cand.distance() > always) {
                    double limit = rule.hideBeyond();
                    boolean beyond = limit > 0 && cand.distance() > (cand.wasHidden() ? limit - hyst : limit);
                    boolean overBudget = rule.maxVisible() > 0 && visible >= rule.maxVisible();
                    hide = (beyond || overBudget) && !isProtected(e, cfg);
                }
                if (hide) {
                    hiddenPer[c]++;
                    keepHidden.add(e.getUniqueId());
                    if (!cand.wasHidden()) toHide.add(e);
                } else {
                    visible++;
                }
            }
        }

        UUID pid = player.getUniqueId();
        for (Entity e : toHide) {
            player.hideEntity(plugin, e);
            state.hidden.add(e.getUniqueId());
            hiddenBy.computeIfAbsent(e.getUniqueId(), k -> new HashSet<>()).add(pid);
            removeIndex(pendingShow, e.getUniqueId(), pid);
        }
        // Show what we were hiding, was found in this scan and no longer qualifies.
        Iterator<UUID> it = state.hidden.iterator();
        while (it.hasNext()) {
            UUID entityId = it.next();
            if (!seen.contains(entityId) || keepHidden.contains(entityId)) continue;
            Entity e = Bukkit.getEntity(entityId);
            if (e != null) player.showEntity(plugin, e);
            it.remove();
            removeIndex(hiddenBy, entityId, pid);
        }

        state.hiddenPerCategory = hiddenPer;
        state.seenPerCategory = seenPer;
        long elapsed = System.nanoTime() - t0;
        state.lastScanNanos = elapsed;
        scansTotal++;
        double micros = elapsed / 1000.0;
        avgScanMicros = avgScanMicros == 0 ? micros : avgScanMicros * 0.95 + micros * 0.05;
    }

    /** Show everything this plugin hid from the player (mode off, profile change, world disabled). */
    public void showAll(Player player, PlayerState state) {
        UUID pid = player.getUniqueId();
        for (UUID entityId : state.hidden) {
            removeIndex(hiddenBy, entityId, pid);
            Entity e = Bukkit.getEntity(entityId);
            if (e != null) {
                player.showEntity(plugin, e);
            } else {
                // Unloaded: Paper still remembers it as hidden. Show it once it loads again.
                pendingShow.computeIfAbsent(entityId, k -> new HashSet<>()).add(pid);
            }
        }
        state.hidden.clear();
        state.hiddenPerCategory = new int[0];
        state.seenPerCategory = new int[0];
    }

    /** An entity was added to a world (chunk load, spawn, dimension change). */
    public void onEntityAdded(Entity entity) {
        if (pendingShow.isEmpty()) return;
        Set<UUID> viewers = pendingShow.remove(entity.getUniqueId());
        if (viewers == null) return;
        for (UUID viewer : viewers) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) p.showEntity(plugin, entity);
        }
    }

    /** An entity left the world for good (death, pickup, merge, plugin removal...). */
    public void onEntityGone(UUID entityId) {
        pendingShow.remove(entityId);
        Set<UUID> viewers = hiddenBy.remove(entityId);
        if (viewers == null) return;
        for (UUID viewer : viewers) {
            PlayerState s = states.get(viewer);
            if (s != null) s.hidden.remove(entityId);
        }
    }

    public boolean isProtected(Entity e, FpsConfig cfg) {
        if (cfg.skipWithPassengers() && !e.getPassengers().isEmpty()) return true;
        if (!cfg.protectedTags().isEmpty()) {
            for (String tag : e.getScoreboardTags()) {
                if (cfg.protectedTags().contains(tag.toLowerCase(Locale.ROOT))) return true;
            }
        }
        if (!cfg.protectedNames().isEmpty()) {
            String text = null;
            if (e.customName() != null) text = Text.plain(e.customName());
            if (e instanceof TextDisplay td) {
                String t = Text.plain(td.text());
                text = text == null ? t : text + " " + t;
            }
            if (text != null && !text.isEmpty()) {
                String lower = text.toLowerCase(Locale.ROOT);
                for (String part : cfg.protectedNames()) {
                    if (lower.contains(part)) return true;
                }
            }
        }
        return false;
    }

    private static void removeIndex(Map<UUID, Set<UUID>> index, UUID key, UUID value) {
        Set<UUID> set = index.get(key);
        if (set == null) return;
        set.remove(value);
        if (set.isEmpty()) index.remove(key);
    }
}
