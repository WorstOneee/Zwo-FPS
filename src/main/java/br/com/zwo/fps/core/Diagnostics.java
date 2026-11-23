package br.com.zwo.fps.core;

import br.com.zwo.fps.config.FpsConfig;
import com.destroystokyo.paper.ClientOption;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-aware tips, the player's own game check-up and the staff-only entity census.
 * Everything returns MiniMessage lines. Player-facing output never describes the world around
 * them: entity counts near a player reveal bases and farms.
 */
public final class Diagnostics {
    private Diagnostics() {}

    public static String brand(Player player) {
        String b = player.getClientBrandName();
        return b == null || b.isBlank() ? "desconhecido" : b;
    }

    public static String particlesName(ClientOption.ParticleVisibility p) {
        if (p == null) return "desconhecido";
        return switch (p) {
            case ALL -> "Todas";
            case DECREASED -> "Reduzidas";
            case MINIMAL -> "Mínimas";
        };
    }

    public static List<String> tips(Player player, PlayerState state) {
        List<String> lines = new ArrayList<>();
        String brand = brand(player).toLowerCase(Locale.ROOT);
        int clientView = player.getClientViewDistance();
        ClientOption.ParticleVisibility particles = ModeService.particles(player);

        lines.add("<gray>Cliente: <white>" + brand(player) + "</white> <dark_gray>|</dark_gray> Render distance: <white>"
                + clientView + "</white> <dark_gray>|</dark_gray> Partículas: <white>" + particlesName(particles));

        if (brand.contains("vanilla")) {
            lines.add("<yellow>➜ <white>Maior ganho possível:</white> instale <green>Sodium</green> (Fabric) ou o pacote "
                    + "<click:open_url:'https://modrinth.com/modpack/fabulously-optimized'><hover:show_text:'Abrir no Modrinth'>"
                    + "<aqua><u>Fabulously Optimized</u></aqua></hover></click>. Costuma dobrar ou triplicar o FPS.");
        } else if (brand.contains("fabric") || brand.contains("quilt")) {
            lines.add("<yellow>➜ <white>Fabric detectado:</white> confira se tem <green>Sodium</green>, <green>ImmediatelyFast</green> "
                    + "e <green>EntityCulling</green> instalados.");
        } else if (brand.contains("forge")) {
            lines.add("<yellow>➜ <white>Forge/NeoForge detectado:</white> use <green>Embeddium</green> ou <green>Sodium</green> (NeoForge) "
                    + "e <green>EntityCulling</green>.");
        } else if (brand.contains("lunar") || brand.contains("badlion") || brand.contains("feather")) {
            lines.add("<yellow>➜ <white>Client com otimizações próprias:</white> ative as opções de desempenho/culling no menu dele.");
        }

        if (particles == ClientOption.ParticleVisibility.ALL) {
            lines.add("<yellow>➜ <white>Partículas</white> estão em <white>Todas</white>: mude para <white>Reduzidas</white> ou <white>Mínimas</white> "
                    + "(Opções > Vídeo). O servidor não consegue cortar partículas por você.");
        }
        int send = state.appliedSendDistance();
        if (send > 0 && clientView > send) {
            lines.add("<yellow>➜ <white>O servidor envia no máximo " + send + " chunks</white> para você neste modo; "
                    + "um render distance maior que isso não mostra nada a mais.");
        } else if (state.profile() == null && clientView >= 16) {
            lines.add("<yellow>➜ <white>Render distance " + clientView + "</white> é alto. 8 a 12 já é ótimo para jogar.");
        }
        lines.add("<yellow>➜ <white>Vídeo:</white> Gráficos <white>Rápidos</white>, Nuvens <white>Desligadas</white>, "
                + "Sombras de entidade <white>Desligadas</white>, Mistura de bioma <white>Desligada</white>, "
                + "Distância de entidades <white>75%</white>.");
        return lines;
    }

    /** Player-only check-up: their own client settings and what the server sends them. No world info. */
    public static List<String> checkup(Player player, PlayerState state, FpsConfig cfg, ModeService modes, String currentMode) {
        List<String> lines = new ArrayList<>();
        String brandRaw = brand(player);
        String brand = brandRaw.toLowerCase(Locale.ROOT);
        int clientView = player.getClientViewDistance();
        ClientOption.ParticleVisibility particles = ModeService.particles(player);
        int issues = 0;

        boolean vanilla = brand.contains("vanilla");
        if (vanilla) issues++;
        lines.add(row("Client", brandRaw, vanilla ? "<red>sem mods de otimização" : "<green>ok"));

        String viewNote;
        if (clientView <= 0) viewNote = "<gray>?";
        else if (clientView <= 8) viewNote = "<green>leve";
        else if (clientView <= 12) viewNote = "<yellow>ok";
        else { viewNote = "<red>alto"; issues++; }
        lines.add(row("Render distance", String.valueOf(clientView), viewNote));

        String partNote;
        if (particles == ClientOption.ParticleVisibility.ALL) { partNote = "<red>pesado"; issues++; }
        else if (particles == ClientOption.ParticleVisibility.DECREASED) partNote = "<yellow>ok";
        else if (particles == ClientOption.ParticleVisibility.MINIMAL) partNote = "<green>leve";
        else partNote = "<gray>?";
        lines.add(row("Partículas", particlesName(particles), partNote));

        int send = state.appliedSendDistance();
        lines.add(row("Chunks que o servidor envia", String.valueOf(send > 0 ? send : player.getViewDistance()),
                send > 0 ? "<green>reduzido pelo Zwo FPS" : "<gray>padrão do servidor"));
        lines.add(row("Chuva", state.weatherApplied() ? "escondida" : "visível",
                state.weatherApplied() ? "<green>menos partículas" : "<gray>normal"));

        lines.add(issues == 0 ? "<green>✔ Nada pesado nas suas configurações."
                : "<yellow>⚠ " + issues + (issues == 1 ? " ponto" : " pontos") + " de atenção acima.");

        FpsConfig.Profile rec = modes.resolveAuto(player, cfg);
        if (rec == null && issues > 0) rec = cfg.profile("medio");
        FpsConfig.Profile applied = state.profile();
        if (rec != null && applied != null && applied.id().equals(rec.id())) {
            lines.add("<green>✔ Você já está no modo recomendado: " + rec.displayName());
        } else if (rec != null) {
            lines.add("<gray>Recomendado: " + rec.displayName() + " <click:run_command:'/fps " + rec.id() + "'>"
                    + "<hover:show_text:'<green>Clique para aplicar'><green><bold>[Aplicar]</bold></green></hover></click>");
        } else if (!FpsConfig.MODE_AUTO.equals(currentMode)) {
            lines.add("<gray>Dica: o modo <aqua>Auto</aqua> ajusta sozinho se você baixar as configurações. "
                    + "<click:run_command:'/fps auto'><hover:show_text:'<green>Clique para ativar'><green><bold>[Ativar]</bold></green></hover></click>");
        }
        lines.add("<click:run_command:'/fps dicas'><hover:show_text:'Ver dicas'><gray>Mais dicas: <yellow><u>/fps dicas</u></yellow></hover></click>");
        return lines;
    }

    private static String row(String label, String value, String note) {
        return "<dark_gray>- <gray>" + label + ": <white>" + value + "</white> <dark_gray>(</dark_gray>" + note + "<dark_gray>)";
    }

    /** Staff only: counts entities near the player per culling category and how many we hide for them. */
    public static List<String> area(Player player, PlayerState state, FpsConfig cfg, double radius) {
        int n = cfg.categories().size();
        int[] total = new int[n];
        int[] hidden = new int[n];
        Map<EntityType, Integer> other = new EnumMap<>(EntityType.class);
        int all = 0;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player) continue;
            all++;
            FpsConfig.Category cat = cfg.categoryOf(e.getType());
            if (cat == null) {
                other.merge(e.getType(), 1, Integer::sum);
                continue;
            }
            total[cat.index()]++;
            if (state.isHiding(e.getUniqueId())) hidden[cat.index()]++;
        }
        List<String> lines = new ArrayList<>();
        lines.add("<white>Entidades num raio de " + (int) radius + " blocos: <aqua>" + all);
        for (FpsConfig.Category cat : cfg.categories()) {
            int i = cat.index();
            if (total[i] == 0) continue;
            lines.add("<dark_gray>- </dark_gray>" + cat.displayName() + "<gray>: <white>" + total[i]
                    + "</white>" + (hidden[i] > 0 ? " <dark_gray>(</dark_gray><green>" + hidden[i] + " escondidas pra você</green><dark_gray>)" : ""));
        }
        other.entrySet().stream()
                .sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(en -> lines.add("<dark_gray>- <gray>" + en.getKey().name().toLowerCase(Locale.ROOT) + ": <white>" + en.getValue()));
        return lines;
    }
}
