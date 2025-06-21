package nesoi.aysihuniks.nhook.command.admin;

import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.command.BaseCommand;

public class ReloadCommand extends BaseCommand {
    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {

        if (!sender.hasPermission("nhook.reload")) {
            NHook.inst().tell(sender, "&cYou don't have permission to do that!");
            return true;
        }

        NHook plugin = NHook.inst();

        plugin.getNConfig().save();

        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().disconnect();
            plugin.getDatabaseManager().connect();
        }

        plugin.tell(sender, "&aConfiguration files reloaded.");
        return true;
    }
}
