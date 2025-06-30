package nesoi.aysihuniks.nhook;


import nesoi.aysihuniks.nhook.api.NHookAPI;
import nesoi.aysihuniks.nhook.command.AllCommandExecutor;
import nesoi.aysihuniks.nhook.managers.DatabaseManager;
import nesoi.aysihuniks.nhook.integrations.NPlaceholder;
import nesoi.aysihuniks.nhook.managers.ConfigManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NHook extends JavaPlugin {

    private static NHook INSTANCE;
    private NHookAPI NHookApi;

    @Override
    public void onEnable() {
        INSTANCE = this;

        ConfigManager.initialize(this);
        DatabaseManager.initialize();


        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NPlaceholder().register();
        }

        PluginCommand command = getCommand("nhook");
        if (command != null) {
            command.setExecutor(new AllCommandExecutor());
            command.setTabCompleter(new AllCommandExecutor());
        }

        this.NHookApi = new NHookAPI(DatabaseManager.getInstance());

        new Metrics(this, 26280);

    }

    public static NHook getInstance() {
        return INSTANCE;
    }

    public NHookAPI getAPI() {
        return this.NHookApi;
    }

    @Override
    public void onDisable() {
        INSTANCE = null;
        DatabaseManager.getInstance().disconnect();
    }

    public void tell(CommandSender receiver, String text) {
        String prefix = ConfigManager.getInstance().getMessagePrefix();
        receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + text));
    }

}
