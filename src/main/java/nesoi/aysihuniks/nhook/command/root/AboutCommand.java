package nesoi.aysihuniks.nhook.command.root;

import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.command.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;

public class AboutCommand extends BaseCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginDescriptionFile descriptionFile = NHook.inst().getDescription();
        String pluginName = descriptionFile.getName();
        String pluginVersion = descriptionFile.getVersion();
        String pluginAuthor = String.join(", ", descriptionFile.getAuthors());

        NHook.inst().tell(sender, "&fThis server is running &6" + pluginName + " " + pluginVersion + " &fby" + "&6" + pluginAuthor);
        return true;
    }
}
