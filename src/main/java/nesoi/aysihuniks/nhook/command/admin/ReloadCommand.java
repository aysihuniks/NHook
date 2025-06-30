package nesoi.aysihuniks.nhook.command.admin;

import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.command.BaseCommand;
import nesoi.aysihuniks.nhook.managers.ConfigManager;
import nesoi.aysihuniks.nhook.managers.DatabaseManager;

public class ReloadCommand extends BaseCommand {
    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {

        if (!sender.hasPermission("nhook.reload")) {
            NHook.getInstance().tell(sender, "&cYou don't have permission to do that!");
            return true;
        }

        NHook plugin = NHook.getInstance();

        ConfigManager.getInstance().save();

        if (DatabaseManager.getInstance() != null) {
            DatabaseManager.getInstance().disconnect();
            DatabaseManager.getInstance().connect();
        }

        plugin.tell(sender, "&aConfiguration files reloaded.");
        return true;
    }
}
