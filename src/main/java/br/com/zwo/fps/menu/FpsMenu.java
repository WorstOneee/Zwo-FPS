package br.com.zwo.fps.menu;

import br.com.zwo.fps.ZwoFPS;
import br.com.zwo.fps.config.FpsConfig;
import br.com.zwo.fps.core.PlayerState;
import br.com.zwo.fps.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FpsMenu implements Listener {
    private static final int[] MODE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_RAIN = 21;
    private static final int SLOT_TIPS = 22;
    private static final int SLOT_AREA = 23;

    private final ZwoFPS plugin;

    public FpsMenu(ZwoFPS plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> modeBySlot = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        FpsConfig cfg = plugin.cfg();
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(cfg.menuTitle()));
        holder.inventory = inv;

        String current = plugin.prefs().mode(player);
        PlayerState state = plugin.modes().state(player);
        List<String> modes = cfg.modeIds();
        for (int i = 0; i < modes.size() && i < MODE_SLOTS.length; i++) {
            String mode = modes.get(i);
            holder.modeBySlot.put(MODE_SLOTS[i], mode);
            inv.setItem(MODE_SLOTS[i], modeItem(cfg, mode, mode.equals(current)));
        }

        inv.setItem(SLOT_STATUS, statusItem(player, state, cfg, current));
        boolean showRain = plugin.prefs().showRain(player);
        inv.setItem(SLOT_RAIN, item(showRain ? Material.WATER_BUCKET : Material.BUCKET,
                showRain ? "<aqua>Chuva: <white>visível" : "<aqua>Chuva: <white>escondida",
                List.of("<gray>Chuva e neve geram muitas", "<gray>partículas. Os perfis escondem", "<gray>por padrão.", "", "<yellow>Clique para alternar"), false));
        inv.setItem(SLOT_TIPS, item(Material.BOOK, "<gold>Dicas para o seu PC",
                List.of("<gray>Dicas baseadas no seu client", "<gray>e nas suas configurações.", "", "<yellow>Clique para ver"), false));
        inv.setItem(SLOT_AREA, item(Material.SPYGLASS, "<light_purple>O que tem por perto",
                List.of("<gray>Mostra as entidades ao redor", "<gray>e quantas estão escondidas", "<gray>para você.", "", "<yellow>Clique para ver"), false));
        player.openInventory(inv);
    }

    private ItemStack modeItem(FpsConfig cfg, String mode, boolean active) {
        List<String> lore = new ArrayList<>();
        String name;
        Material icon;
        if (mode.equals(FpsConfig.MODE_OFF)) {
            name = "<red>Desligado";
            icon = Material.BARRIER;
            lore.add("<gray>Tudo normal, sem otimização.");
        } else if (mode.equals(FpsConfig.MODE_AUTO)) {
            name = "<aqua>Automático";
            icon = Material.COMPARATOR;
            lore.add("<gray>Escolhe o perfil pelo render");
            lore.add("<gray>distance e partículas do seu jogo.");
            lore.add("<gray>Se o seu jogo está no alto, não");
            lore.add("<gray>altera nada.");
        } else {
            FpsConfig.Profile p = cfg.profile(mode);
            name = p.displayName();
            icon = p.icon();
            lore.addAll(p.description());
            lore.add("");
            lore.add("<dark_gray>Chunks enviados: <gray>" + (p.sendViewDistance() > 0 ? p.sendViewDistance() : "padrão"));
            lore.add("<dark_gray>Clima limpo: <gray>" + (p.clearWeather() ? "sim" : "não"));
        }
        lore.add("");
        lore.add(active ? "<green>✔ Selecionado" : "<yellow>Clique para usar");
        return item(icon, name, lore, active);
    }

    private ItemStack statusItem(Player player, PlayerState state, FpsConfig cfg, String mode) {
        List<String> lore = new ArrayList<>();
        FpsConfig.Profile p = state.profile();
        lore.add("<gray>Modo: <white>" + mode);
        lore.add("<gray>Perfil aplicado: " + (p == null ? "<white>nenhum" : p.displayName()));
        lore.add("<gray>Chunks enviados: <white>" + (state.appliedSendDistance() > 0 ? state.appliedSendDistance() : "padrão do servidor"));
        lore.add("<gray>Entidades escondidas: <white>" + state.hiddenCount());
        if (cfg.isWorldDisabled(player.getWorld().getName())) lore.add("<yellow>Este mundo está desativado.");
        return item(Material.PLAYER_HEAD, "<white>Seu status", lore, false);
    }

    private static ItemStack item(Material material, String name, List<String> lore, boolean glow) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Text.item(l));
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getRawSlot();
        String mode = holder.modeBySlot.get(slot);
        if (mode != null) {
            player.closeInventory();
            plugin.commands().setMode(player, player, mode);
            return;
        }
        switch (slot) {
            case SLOT_RAIN -> {
                plugin.commands().toggleRain(player);
                open(player);
            }
            case SLOT_TIPS -> {
                player.closeInventory();
                plugin.commands().sendTips(player);
            }
            case SLOT_AREA -> {
                player.closeInventory();
                plugin.commands().sendArea(player);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
    }
}
