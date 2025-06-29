package nesoi.aysihuniks.nhook.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nesoi.aysihuniks.nhook.NHook;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * NHook PlaceholderAPI expansion class.
 * <p>
 * Supported placeholders:
 *   %nhook_table_column% - Returns the specified column for the sender (self).
 *   %nhook_table_column_playername% - Returns the specified column for the given player.
 *   %nhook_table_all% - Returns all columns for the sender (as JSON).
 *   %nhook_table_all_playername% - Returns all columns for the given player (as JSON).
 * <p>
 * If the column name contains '_', use '-' in the placeholder, e.g. %nhook_players_last-login%.
 */
public class NPlaceholder extends PlaceholderExpansion {

    @Override
    public @NotNull String getAuthor() {
        return "aysihuniks";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nhook";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    /**
     * Handles placeholder requests, supporting dash '-' for columns with underscores.
     * <p>
     * Examples:
     *  %nhook_players_last-login% (self, column: last_login)
     *  %nhook_players_last-login_aysihuniks% (user: aysihuniks, column: last_login)
     * <p>
     * @param player The player who sent the request, or null if from the console.
     * @param params The placeholder parameters, separated by '_' and '-' as described above.
     * @return       The requested value as a string, or empty string if not found.
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.isEmpty()) return "";

        // Find the first underscore, which separates the table from the rest
        int underscoreIdx = params.indexOf('_');
        if (underscoreIdx == -1 || underscoreIdx >= params.length() - 1) return "";

        String table = params.substring(0, underscoreIdx);
        String rest = params.substring(underscoreIdx + 1);

        // Find where the column ends and (optional) player name starts
        int playerIdx = rest.indexOf('_');
        String column;
        String targetPlayer;

        if (playerIdx == -1) {
            // No player name provided: %nhook_players_last-login%
            column = rest.replace('-', '_');
            targetPlayer = player != null ? player.getName() : null;
        } else {
            // Player name provided: %nhook_players_last-login_aysihuniks%
            column = rest.substring(0, playerIdx).replace('-', '_');
            targetPlayer = rest.substring(playerIdx + 1);
        }

        if (targetPlayer == null || targetPlayer.isEmpty()) return "";

        // If the column requested is "all", fetch all columns as a JSON string
        String value;
        if ("all".equalsIgnoreCase(column)) {
            value = NHook.inst().getDatabaseManager().fetchPlayerAllValues(table, targetPlayer);
        } else {
            // Otherwise, fetch the specific column value
            value = NHook.inst().getDatabaseManager().fetchPlayerValue(table, column, targetPlayer);
        }
        return value != null ? value : "";
    }
}