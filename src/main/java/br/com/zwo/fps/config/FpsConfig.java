package br.com.zwo.fps.config;

import com.destroystokyo.paper.ClientOption;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable snapshot of config.yml. A new instance is built on every reload. */
public final class FpsConfig {

    public static final String MODE_OFF = "off";
    public static final String MODE_AUTO = "auto";

    public record Category(int index, String id, String displayName, Material icon, Set<EntityType> types) {}

    public record Rule(int hideBeyond, int maxVisible) {
        public boolean active() {
            return hideBeyond > 0 || maxVisible > 0;
        }
    }

    public record Profile(String id, String displayName, Material icon, List<String> description,
                          int sendViewDistance, boolean clearWeather, Rule[] rules) {
        public boolean hasCulling() {
            for (Rule rule : rules) {
                if (rule != null) return true;
            }
            return false;
        }
    }

    public record AutoRule(int maxClientViewDistance, Set<ClientOption.ParticleVisibility> particles) {}

    private String defaultMode;
    private Set<String> disabledWorlds;
    private boolean manageSendDistance;
    private int scanIntervalTicks;
    private double scanRadius;
    private double scanHeight;
    private int maxPlayersPerTick;
    private double maxMillisPerTick;
    private double slowDownMspt;
    private double alwaysVisibleRadius;
    private double hysteresis;
    private Map<String, AutoRule> autoRules;
    private boolean hintEnabled;
    private int hintCooldownHours;
    private int hintMaxViewDistance;
    private Set<String> protectedTags;
    private List<String> protectedNames;
    private boolean skipWithPassengers;
    private List<Category> categories;
    private Map<EntityType, Category> typeIndex;
    private Map<String, Profile> profiles;
    private ConfigurationSection messages;
    private String menuTitle;

    private FpsConfig() {}

    public static FpsConfig load(FileConfiguration c, Logger log) {
        FpsConfig cfg = new FpsConfig();
        cfg.disabledWorlds = lowerSet(c.getStringList("disabled-worlds"));
        cfg.manageSendDistance = c.getBoolean("view.manage-send-distance", true);

        cfg.scanIntervalTicks = Math.max(10, c.getInt("culling.scan-interval-ticks", 40));
        cfg.scanRadius = clamp(c.getDouble("culling.scan-radius", 128), 16, 256);
        cfg.scanHeight = clamp(c.getDouble("culling.scan-height", 64), 8, 384);
        cfg.maxPlayersPerTick = Math.max(1, c.getInt("culling.max-players-per-tick", 4));
        cfg.maxMillisPerTick = clamp(c.getDouble("culling.max-millis-per-tick", 2.0), 0.2, 20);
        cfg.slowDownMspt = c.getDouble("culling.slow-down-if-mspt-above", 45.0);
        cfg.alwaysVisibleRadius = clamp(c.getDouble("culling.always-visible-radius", 8), 0, 64);
        cfg.hysteresis = clamp(c.getDouble("culling.hysteresis-blocks", 4), 0, 32);

        cfg.hintEnabled = c.getBoolean("low-end-hint.enabled", true);
        cfg.hintCooldownHours = Math.max(1, c.getInt("low-end-hint.cooldown-hours", 24));
        cfg.hintMaxViewDistance = c.getInt("low-end-hint.max-client-view-distance", 8);

        cfg.protectedTags = lowerSet(c.getStringList("protection.scoreboard-tags"));
        List<String> names = new ArrayList<>();
        for (String s : c.getStringList("protection.name-contains")) {
            if (s != null && !s.isBlank()) names.add(s.toLowerCase(Locale.ROOT));
        }
        cfg.protectedNames = List.copyOf(names);
        cfg.skipWithPassengers = c.getBoolean("protection.skip-with-passengers", true);

        loadCategories(cfg, c.getConfigurationSection("categories"), log);
        loadProfiles(cfg, c.getConfigurationSection("profiles"), log);

        Map<String, AutoRule> auto = new LinkedHashMap<>();
        ConfigurationSection autoRoot = c.getConfigurationSection("auto");
        if (autoRoot != null) {
            for (String id : autoRoot.getKeys(false)) {
                String pid = id.toLowerCase(Locale.ROOT);
                if (!cfg.profiles.containsKey(pid)) {
                    log.warning("auto." + id + " aponta para um perfil que nao existe.");
                    continue;
                }
                auto.put(pid, autoRule(autoRoot.getConfigurationSection(id), 0, log));
            }
        }
        cfg.autoRules = Collections.unmodifiableMap(auto);

        String def = c.getString("default-mode", MODE_AUTO).toLowerCase(Locale.ROOT);
        if (!cfg.isValidMode(def)) {
            log.warning("default-mode '" + def + "' nao existe; usando 'off'.");
            def = MODE_OFF;
        }
        cfg.defaultMode = def;

        cfg.messages = c.getConfigurationSection("messages");
        cfg.menuTitle = c.getString("menu.title", "<dark_purple><bold>Zwo FPS");
        return cfg;
    }

    private static AutoRule autoRule(ConfigurationSection s, int defDistance, Logger log) {
        if (s == null) return new AutoRule(defDistance, Set.of());
        Set<ClientOption.ParticleVisibility> particles = EnumSet.noneOf(ClientOption.ParticleVisibility.class);
        for (String raw : s.getStringList("particles")) {
            try {
                particles.add(ClientOption.ParticleVisibility.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log.warning("Particula invalida em auto: " + raw + " (use ALL, DECREASED ou MINIMAL)");
            }
        }
        return new AutoRule(s.getInt("max-client-view-distance", defDistance), Collections.unmodifiableSet(particles));
    }

    private static void loadCategories(FpsConfig cfg, ConfigurationSection root, Logger log) {
        List<Category> list = new ArrayList<>();
        Map<EntityType, Category> index = new EnumMap<>(EntityType.class);
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Set<EntityType> types = EnumSet.noneOf(EntityType.class);
                for (String raw : s.getStringList("types")) {
                    EntityType type = parseType(raw);
                    if (type == null) {
                        log.warning("EntityType invalido na categoria '" + id + "': " + raw);
                    } else if (type == EntityType.PLAYER) {
                        log.warning("PLAYER nao pode ser escondido (categoria '" + id + "').");
                    } else if (index.containsKey(type)) {
                        log.warning(type + " ja pertence a categoria '" + index.get(type).id() + "'; ignorado em '" + id + "'.");
                    } else {
                        types.add(type);
                    }
                }
                Category cat = new Category(list.size(), id.toLowerCase(Locale.ROOT),
                        s.getString("name", id), material(s.getString("icon"), Material.CHEST),
                        Collections.unmodifiableSet(types));
                list.add(cat);
                for (EntityType t : types) index.put(t, cat);
            }
        }
        cfg.categories = List.copyOf(list);
        cfg.typeIndex = Collections.unmodifiableMap(index);
    }

    private static void loadProfiles(FpsConfig cfg, ConfigurationSection root, Logger log) {
        Map<String, Profile> map = new LinkedHashMap<>();
        if (root != null) {
            for (String rawId : root.getKeys(false)) {
                String id = rawId.toLowerCase(Locale.ROOT);
                if (id.equals(MODE_OFF) || id.equals(MODE_AUTO)) {
                    log.warning("'" + id + "' e um nome reservado e nao pode ser perfil.");
                    continue;
                }
                ConfigurationSection s = root.getConfigurationSection(rawId);
                if (s == null) continue;
                Rule[] rules = new Rule[cfg.categories.size()];
                ConfigurationSection culling = s.getConfigurationSection("culling");
                if (culling != null) {
                    for (String catId : culling.getKeys(false)) {
                        Category cat = cfg.category(catId);
                        if (cat == null) {
                            log.warning("Perfil '" + id + "' usa categoria inexistente: " + catId);
                            continue;
                        }
                        Rule rule = new Rule(Math.max(0, culling.getInt(catId + ".hide-beyond", 0)),
                                Math.max(0, culling.getInt(catId + ".max-visible", 0)));
                        rules[cat.index()] = rule.active() ? rule : null;
                    }
                }
                int send = s.getInt("send-view-distance", 0);
                send = send <= 0 ? -1 : (int) clamp(send, 2, 32);
                map.put(id, new Profile(id, s.getString("name", id), material(s.getString("icon"), Material.PAPER),
                        List.copyOf(s.getStringList("description")), send, s.getBoolean("clear-weather", false), rules));
            }
        }
        cfg.profiles = Collections.unmodifiableMap(map);
    }

    private static EntityType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Material material(String raw, Material def) {
        if (raw == null) return def;
        Material m = Material.matchMaterial(raw);
        return m == null || !m.isItem() ? def : m;
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> set = new HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) set.add(v.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(set);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public boolean isValidMode(String mode) {
        return mode != null && (mode.equals(MODE_OFF) || mode.equals(MODE_AUTO) || profiles.containsKey(mode));
    }

    /** off, auto and every profile id, in menu order. */
    public List<String> modeIds() {
        List<String> ids = new ArrayList<>(profiles.keySet());
        ids.add(MODE_AUTO);
        ids.add(MODE_OFF);
        return ids;
    }

    public Category category(String id) {
        for (Category c : categories) {
            if (c.id().equalsIgnoreCase(id)) return c;
        }
        return null;
    }

    public Category categoryOf(EntityType type) { return typeIndex.get(type); }
    public Profile profile(String id) { return id == null ? null : profiles.get(id.toLowerCase(Locale.ROOT)); }
    public Map<String, Profile> profiles() { return profiles; }
    public List<Category> categories() { return categories; }
    public String defaultMode() { return defaultMode; }
    public boolean isWorldDisabled(String world) { return disabledWorlds.contains(world.toLowerCase(Locale.ROOT)); }
    public boolean manageSendDistance() { return manageSendDistance; }
    public int scanIntervalTicks() { return scanIntervalTicks; }
    public double scanRadius() { return scanRadius; }
    public double scanHeight() { return scanHeight; }
    public int maxPlayersPerTick() { return maxPlayersPerTick; }
    public double maxMillisPerTick() { return maxMillisPerTick; }
    public double slowDownMspt() { return slowDownMspt; }
    public double alwaysVisibleRadius() { return alwaysVisibleRadius; }
    public double hysteresis() { return hysteresis; }
    /** Profile id -> rule, checked in config order; first match wins. */
    public Map<String, AutoRule> autoRules() { return autoRules; }
    public boolean hintEnabled() { return hintEnabled; }
    public int hintCooldownHours() { return hintCooldownHours; }
    public int hintMaxViewDistance() { return hintMaxViewDistance; }
    public Set<String> protectedTags() { return protectedTags; }
    public List<String> protectedNames() { return protectedNames; }
    public boolean skipWithPassengers() { return skipWithPassengers; }
    public ConfigurationSection messages() { return messages; }
    public String menuTitle() { return menuTitle; }
}
