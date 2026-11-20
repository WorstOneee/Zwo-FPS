package br.com.zwo.fps;

import br.com.zwo.fps.command.FpsCommands;
import br.com.zwo.fps.config.FpsConfig;
import br.com.zwo.fps.core.CullingService;
import br.com.zwo.fps.core.ModeService;
import br.com.zwo.fps.core.PlayerPrefs;
import br.com.zwo.fps.core.PlayerState;
import br.com.zwo.fps.hook.FpsPlaceholders;
import br.com.zwo.fps.listener.FpsListener;
import br.com.zwo.fps.menu.FpsMenu;
import br.com.zwo.fps.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ZwoFPS extends JavaPlugin {
    private volatile FpsConfig config;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private Text text;
    private PlayerPrefs prefs;
    private CullingService culling;
    private ModeService modes;
    private FpsMenu menu;
    private FpsCommands commands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = FpsConfig.load(getConfig(), getLogger());
        text = new Text(this::cfg);
        prefs = new PlayerPrefs(this, this::cfg);
        culling = new CullingService(this, this::cfg, states);
        modes = new ModeService(this, this::cfg, prefs, states, culling);
        menu = new FpsMenu(this);
        commands = new FpsCommands(this);

        for (String name : new String[]{"fps", "zwofps"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }
        Bukkit.getPluginManager().registerEvents(new FpsListener(this), this);
        Bukkit.getPluginManager().registerEvents(menu, this);
        culling.start();

        // Supports /reload and plugin managers: pick up players already online.
        for (Player p : Bukkit.getOnlinePlayers()) {
            modes.state(p);
            culling.track(p);
            modes.apply(p);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new FpsPlaceholders(this).register();
            getLogger().info("PlaceholderAPI detectado: %zwofps_mode%, %zwofps_profile%, %zwofps_hidden%, %zwofps_send_distance%");
        }
        getLogger().info("Zwo FPS " + getPluginMeta().getVersion() + " ativo: " + config.profiles().size()
                + " perfis, " + config.categories().size() + " categorias, modo padrao '" + config.defaultMode() + "'.");
    }

    @Override
    public void onDisable() {
        if (culling != null) culling.stop();
        if (modes != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    modes.restore(p);
                } catch (Throwable t) {
                    getLogger().warning("Falha ao restaurar " + p.getName() + ": " + t);
                }
            }
        }
        states.clear();
    }

    public void reload() {
        reloadConfig();
        config = FpsConfig.load(getConfig(), getLogger());
        for (Player p : Bukkit.getOnlinePlayers()) modes.apply(p);
    }

    public FpsConfig cfg() { return config; }
    public Text text() { return text; }
    public PlayerPrefs prefs() { return prefs; }
    public CullingService culling() { return culling; }
    public ModeService modes() { return modes; }
    public FpsMenu menu() { return menu; }
    public FpsCommands commands() { return commands; }
    public Map<UUID, PlayerState> states() { return states; }
}
