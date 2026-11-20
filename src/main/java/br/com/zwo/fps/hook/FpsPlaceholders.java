package br.com.zwo.fps.hook;

import br.com.zwo.fps.ZwoFPS;
import br.com.zwo.fps.core.PlayerState;
import br.com.zwo.fps.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** %zwofps_mode%, %zwofps_profile%, %zwofps_hidden%, %zwofps_send_distance% */
public final class FpsPlaceholders extends PlaceholderExpansion {
    private final ZwoFPS plugin;

    public FpsPlaceholders(ZwoFPS plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "zwofps"; }
    @Override public String getAuthor() { return "Zwo"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (!(offline instanceof Player player) || !player.isOnline()) return "";
        PlayerState state = plugin.states().get(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "mode" -> plugin.prefs().mode(player);
            case "profile" -> state == null || state.profile() == null ? "nenhum" : Text.plain(Text.mm(state.profile().displayName()));
            case "hidden" -> String.valueOf(state == null ? 0 : state.hiddenCount());
            case "send_distance" -> String.valueOf(state == null || state.appliedSendDistance() <= 0 ? player.getViewDistance() : state.appliedSendDistance());
            default -> null;
        };
    }
}
