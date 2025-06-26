package nesoi.aysihuniks.nhook;

import lombok.Getter;
import lombok.Setter;
import nesoi.aysihuniks.nhook.api.NHookAPI;
import nesoi.aysihuniks.nhook.command.AllCommandExecutor;
import nesoi.aysihuniks.nhook.database.DatabaseManager;
import nesoi.aysihuniks.nhook.integrations.NPlaceholder;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Setter
public final class NHook extends JavaPlugin {

    private static NHook inst;

    private NHookAPI api;
    private Config nConfig;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        inst = this;
        this.api = new NHookAPI(databaseManager);

        nConfig = new Config(this).load();
        databaseManager = new DatabaseManager();
        databaseManager.connect();

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NPlaceholder().register();
        }

        PluginCommand command = getCommand("nhook");
        if (command != null) {
            command.setExecutor(new AllCommandExecutor());
            command.setTabCompleter(new AllCommandExecutor());
        }

        new Metrics(this, 26280);
    }

    public static NHook inst() {
        return inst;
    }

    @Override
    public void onDisable() {
        inst = null;
        databaseManager.disconnect();
    }

    public void tell(CommandSender receiver, String text) {
        receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6NHook&8]&r " + text));
    }
}
