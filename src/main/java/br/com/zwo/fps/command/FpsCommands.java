package br.com.zwo.fps.command;

import br.com.zwo.fps.ZwoFPS;
import br.com.zwo.fps.config.FpsConfig;
import br.com.zwo.fps.core.Diagnostics;
import br.com.zwo.fps.core.ModeService;
import br.com.zwo.fps.core.PlayerState;
import br.com.zwo.fps.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/** Handles /fps (players) and /zwofps (admin). */
public final class FpsCommands implements TabExecutor {
    private static final double AREA_RADIUS = 64;

    private final ZwoFPS plugin;

    public FpsCommands(ZwoFPS plugin) {
        this.plugin = plugin;
    }

    private Text text() { return plugin.text(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("zwofps")) return admin(sender, args);
        return fps(sender, args);
    }

    // ------------------------------------------------------------------ /fps

    private boolean fps(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            text().send(sender, "only-player");
            return true;
        }
        if (!player.hasPermission("zwofps.use")) {
            text().send(player, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> plugin.menu().open(player);
            case "status" -> sendStatus(player, player, false);
            case "dicas", "tips" -> sendTips(player);
            // "area" used to list nearby entities; it now opens the check-up (no world info for players).
            case "checkup", "check", "jogo", "area", "área" -> sendCheckup(player);
            case "chuva", "rain" -> toggleRain(player);
            default -> setMode(player, player, normalizeMode(sub));
        }
        return true;
    }

    private static String normalizeMode(String raw) {
        return switch (raw) {
            case "on", "ligar" -> "medio";
            case "médio", "medium" -> "medio";
            case "low", "light" -> "leve";
            case "desligar", "desligado", "normal" -> FpsConfig.MODE_OFF;
            case "automatico", "automático" -> FpsConfig.MODE_AUTO;
            default -> raw;
        };
    }

    public void setMode(CommandSender feedback, Player target, String mode) {
        FpsConfig cfg = plugin.cfg();
        if (!cfg.isValidMode(mode)) {
            text().send(feedback, "unknown-mode", Text.ph("modes", String.join(", ", cfg.modeIds())));
            return;
        }
        plugin.prefs().setMode(target, mode);
        plugin.modes().apply(target);
        PlayerState state = plugin.modes().state(target);
        if (feedback != target) {
            text().send(feedback, "admin-set", Text.ph("player", target.getName()), Text.ph("mode", mode));
        }
        if (cfg.isWorldDisabled(target.getWorld().getName()) && !mode.equals(FpsConfig.MODE_OFF)) {
            text().send(target, "world-disabled");
            return;
        }
        switch (mode) {
            case FpsConfig.MODE_OFF -> text().send(target, "mode-off");
            case FpsConfig.MODE_AUTO -> {
                if (state.profile() == null) text().send(target, "mode-auto-idle");
                else text().send(target, "mode-auto-active", Text.phMm("profile", state.profile().displayName()));
            }
            default -> text().send(target, "mode-set", Text.phMm("mode", cfg.profile(mode).displayName()));
        }
    }

    public void toggleRain(Player player) {
        boolean show = !plugin.prefs().showRain(player);
        plugin.prefs().setShowRain(player, show);
        plugin.modes().apply(player);
        text().send(player, show ? "rain-on" : "rain-off");
    }

    public void sendTips(Player player) {
        PlayerState state = plugin.modes().state(player);
        text().sendRaw(player, "<gold><bold>Dicas para o seu PC");
        for (String line : Diagnostics.tips(player, state)) player.sendMessage(Text.mm(line));
    }

    public void sendCheckup(Player player) {
        PlayerState state = plugin.modes().state(player);
        text().sendRaw(player, "<light_purple><bold>Check-up do seu jogo");
        for (String line : Diagnostics.checkup(player, state, plugin.cfg(), plugin.modes(), plugin.prefs().mode(player))) {
            player.sendMessage(Text.mm(line));
        }
    }

    /** Staff only: entity census around a player (or the sender). It reveals bases, so never for players. */
    private void sendArea(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            text().send(sender, "player-not-found");
            return;
        }
        PlayerState state = plugin.modes().state(target);
        text().sendRaw(sender, "<light_purple><bold>Entidades perto de <aqua>" + target.getName());
        for (String line : Diagnostics.area(target, state, plugin.cfg(), AREA_RADIUS)) sender.sendMessage(Text.mm(line));
    }

    /** detailed = staff view with hidden-entity counts, which describe the surroundings. */
    private void sendStatus(CommandSender to, Player target, boolean detailed) {
        PlayerState state = plugin.modes().state(target);
        FpsConfig cfg = plugin.cfg();
        FpsConfig.Profile p = state.profile();
        text().sendRaw(to, "<white>Status de <aqua>" + target.getName());
        to.sendMessage(Text.mm("<gray>Modo escolhido: <white>" + plugin.prefs().mode(target)
                + (plugin.prefs().hasChosenMode(target) ? "" : " <dark_gray>(padrão)")));
        to.sendMessage(Text.mm("<gray>Perfil aplicado: " + (p == null ? "<white>nenhum" : p.displayName())));
        to.sendMessage(Text.mm("<gray>Chunks enviados: <white>" + (state.appliedSendDistance() > 0 ? state.appliedSendDistance() : "padrão")
                + " <dark_gray>|</dark_gray> <gray>view do servidor: <white>" + target.getViewDistance()
                + " <dark_gray>|</dark_gray> <gray>render do cliente: <white>" + target.getClientViewDistance()));
        to.sendMessage(Text.mm("<gray>Clima limpo: <white>" + (state.weatherApplied() ? "sim" : "não")
                + " <dark_gray>|</dark_gray> <gray>Partículas no cliente: <white>" + Diagnostics.particlesName(ModeService.particles(target))
                + " <dark_gray>|</dark_gray> <gray>Client: <white>" + Diagnostics.brand(target)));
        if (!detailed) return;
        to.sendMessage(Text.mm("<gray>Entidades escondidas: <white>" + state.hiddenCount()
                + " <dark_gray>|</dark_gray> <gray>último scan: <white>" + String.format(Locale.ROOT, "%.2f", state.lastScanNanos() / 1_000_000.0) + " ms"));
        int[] hidden = state.hiddenPerCategory();
        int[] seen = state.seenPerCategory();
        for (FpsConfig.Category cat : cfg.categories()) {
            int i = cat.index();
            if (i >= seen.length || seen[i] == 0) continue;
            to.sendMessage(Text.mm("<dark_gray>- </dark_gray>" + cat.displayName() + "<gray>: <white>" + hidden[i] + "</white>/<white>" + seen[i] + " <gray>escondidas"));
        }
    }

    // --------------------------------------------------------------- /zwofps

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zwofps.admin")) {
            text().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                text().send(sender, "reloaded");
            }
            case "stats" -> sendStats(sender);
            case "check" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) text().send(sender, "player-not-found");
                else sendStatus(sender, target, true);
            }
            case "set" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    text().send(sender, "player-not-found");
                } else if (args.length < 3) {
                    text().send(sender, "unknown-mode", Text.ph("modes", String.join(", ", plugin.cfg().modeIds())));
                } else {
                    setMode(sender, target, normalizeMode(args[2].toLowerCase(Locale.ROOT)));
                }
            }
            case "hotspots" -> hotspots(sender, args);
            case "area" -> sendArea(sender, args);
            default -> {
                text().sendRaw(sender, "<white>Comandos de admin:");
                sender.sendMessage(Text.mm("<gray>/zwofps stats <dark_gray>- uso e custo do plugin"));
                sender.sendMessage(Text.mm("<gray>/zwofps check <jogador> <dark_gray>- status e client do jogador"));
                sender.sendMessage(Text.mm("<gray>/zwofps set <jogador> <modo> <dark_gray>- troca o modo"));
                sender.sendMessage(Text.mm("<gray>/zwofps area [jogador] <dark_gray>- entidades num raio de 64 blocos"));
                sender.sendMessage(Text.mm("<gray>/zwofps hotspots [mundo] [qtd] <dark_gray>- chunks com mais entidades/tile entities"));
                sender.sendMessage(Text.mm("<gray>/zwofps reload"));
            }
        }
        return true;
    }

    private void sendStats(CommandSender sender) {
        Map<String, Integer> byMode = new TreeMap<>();
        Map<String, Integer> byProfile = new TreeMap<>();
        int online = 0, hidden = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            online++;
            byMode.merge(plugin.prefs().mode(p), 1, Integer::sum);
            PlayerState s = plugin.states().get(p.getUniqueId());
            if (s == null) continue;
            hidden += s.hiddenCount();
            byProfile.merge(s.profile() == null ? "nenhum" : s.profile().id(), 1, Integer::sum);
        }
        text().sendRaw(sender, "<white>Estatísticas");
        sender.sendMessage(Text.mm("<gray>Online: <white>" + online + " <dark_gray>|</dark_gray> <gray>entidades escondidas (soma): <white>" + hidden));
        sender.sendMessage(Text.mm("<gray>Modos escolhidos: <white>" + byMode));
        sender.sendMessage(Text.mm("<gray>Perfis aplicados agora: <white>" + byProfile));
        sender.sendMessage(Text.mm("<gray>Custo médio do scan: <white>" + String.format(Locale.ROOT, "%.0f µs", plugin.culling().averageScanMicros())
                + " <dark_gray>|</dark_gray> <gray>scans: <white>" + plugin.culling().scansTotal()
                + " <dark_gray>|</dark_gray> <gray>MSPT: <white>" + String.format(Locale.ROOT, "%.1f", Bukkit.getAverageTickTime())));
    }

    private record Hotspot(World world, int x, int z, int entities, int tiles, String top) {
        int score() { return entities + tiles; }
    }

    private void hotspots(CommandSender sender, String[] args) {
        List<World> worlds = new ArrayList<>();
        int limit = 10;
        for (int i = 1; i < args.length; i++) {
            World w = Bukkit.getWorld(args[i]);
            if (w != null) worlds.add(w);
            else {
                try { limit = Math.max(1, Math.min(30, Integer.parseInt(args[i]))); } catch (NumberFormatException ignored) { }
            }
        }
        if (worlds.isEmpty()) worlds.addAll(Bukkit.getWorlds());
        PriorityQueue<Hotspot> top = new PriorityQueue<>((a, b) -> Integer.compare(a.score(), b.score()));
        long t0 = System.nanoTime();
        int chunks = 0;
        for (World w : worlds) {
            for (Chunk c : w.getLoadedChunks()) {
                chunks++;
                Entity[] ents = c.getEntities();
                int tiles = c.getTileEntities(false).length;
                if (ents.length + tiles == 0) continue;
                if (top.size() >= limit && ents.length + tiles <= top.peek().score()) continue;
                Map<EntityType, Integer> count = new EnumMap<>(EntityType.class);
                for (Entity e : ents) count.merge(e.getType(), 1, Integer::sum);
                String topType = count.entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(e -> e.getKey().name().toLowerCase(Locale.ROOT) + " x" + e.getValue()).orElse("-");
                top.add(new Hotspot(w, c.getX(), c.getZ(), ents.length, tiles, topType));
                if (top.size() > limit) top.poll();
            }
        }
        List<Hotspot> sorted = new ArrayList<>(top);
        sorted.sort((a, b) -> Integer.compare(b.score(), a.score()));
        text().sendRaw(sender, "<white>Hotspots <gray>(" + chunks + " chunks em "
                + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - t0) / 1_000_000.0) + " ms)");
        for (Hotspot h : sorted) {
            int bx = h.x() * 16 + 8, bz = h.z() * 16 + 8;
            String tp = "/execute in " + h.world().getKey().asString() + " run tp @s " + bx + " ~ " + bz;
            sender.sendMessage(Text.mm("<click:suggest_command:'" + tp + "'><hover:show_text:'Clique para preparar o teleporte'>"
                    + "<dark_gray>- <aqua>" + h.world().getName() + " <gray>" + bx + ", " + bz
                    + " <white>" + h.entities() + "</white> entidades, <white>" + h.tiles() + "</white> tile entities <dark_gray>(" + h.top() + ")</hover></click>"));
        }
        if (sorted.isEmpty()) sender.sendMessage(Text.mm("<gray>Nada encontrado."));
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("zwofps")) {
            if (!sender.hasPermission("zwofps.admin")) return List.of();
            if (args.length == 1) options.addAll(List.of("stats", "check", "set", "area", "hotspots", "reload"));
            else if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("area")))
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            else if (args.length == 2 && args[0].equalsIgnoreCase("hotspots"))
                Bukkit.getWorlds().forEach(w -> options.add(w.getName()));
            else if (args.length == 3 && args[0].equalsIgnoreCase("set")) options.addAll(plugin.cfg().modeIds());
        } else if (args.length == 1) {
            options.add("menu");
            options.addAll(plugin.cfg().modeIds());
            options.addAll(List.of("status", "dicas", "checkup", "chuva"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }
}
