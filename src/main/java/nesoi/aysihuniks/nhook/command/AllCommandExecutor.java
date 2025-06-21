package nesoi.aysihuniks.nhook.command;

import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.command.admin.ReloadCommand;
import nesoi.aysihuniks.nhook.command.root.AboutCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class AllCommandExecutor implements CommandExecutor, TabCompleter {

    private final HashMap<String, BaseCommand> commands = new HashMap<>();
    private final HashMap<String, BaseCommand> adminCommands = new HashMap<>();

    public AllCommandExecutor() {
        adminCommands.put("reload", new ReloadCommand());
        commands.put("about", new AboutCommand());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        Player player = (Player) sender;

        if (args.length == 0) {
            NHook.inst().tell(player, "&fCommand not found.");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("admin")) {
            if (!player.hasPermission("nhook.admin")) {
                NHook.inst().tell(player, "&cYou don't have permission to do that!");
                return true;
            }

            if (args.length == 1) {
                NHook.inst().tell(player, "&fAvailable subcommands: &6reload");
                return true;
            }

            String adminSubCommand = args[1].toLowerCase();
            BaseCommand adminCmd = adminCommands.get(adminSubCommand);

            if (adminCmd != null) {
                return adminCmd.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
            } else {
                NHook.inst().tell(player, "&fCommand not found.");
                return true;
            }
        }

        BaseCommand cmd = commands.get(subCommand);
        if (cmd != null) {
            return cmd.onCommand(sender, command, label, args);
        }

        NHook.inst().tell(player, "&fCommand not found.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(commands.keySet());
            if (sender.hasPermission("nhook.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("nhook.admin")) {
                completions.addAll(adminCommands.keySet());
            }
        } else if (args.length > 2 && args[0].equalsIgnoreCase("admin")) {
            BaseCommand adminCmd = adminCommands.get(args[1].toLowerCase());
            if (adminCmd != null) {
                List<String> subCompletions = adminCmd.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
                if (subCompletions != null) {
                    completions.addAll(subCompletions);
                }
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
