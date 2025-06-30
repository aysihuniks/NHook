package nesoi.aysihuniks.nhook.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.managers.DatabaseManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * NHook PlaceholderAPI expansion class with advanced caching support.
 * <p>
 * Supported placeholders:
 *   %nhook_table_column% - Returns the specified column for the sender (self).
 *   %nhook_table_column_player% - Returns the specified column for the given player.
 *   %nhook_table_all% - Returns all columns for the sender (as JSON).
 *   %nhook_table_all_player% - Returns all columns for the given player (as JSON).
 * <p>
 * If the column name contains '_', use '-' in the placeholder, e.g. %nhook_players_last-login%.
 * <p>
 * The new system includes intelligent caching for better performance and reduced database load.
 */
public class NPlaceholder extends PlaceholderExpansion {

    private static final long TIMEOUT_SECONDS = 5; // Timeout for database operations
    private final DatabaseManager databaseManager;

    public NPlaceholder() {
        this.databaseManager = DatabaseManager.getInstance();
    }

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
        return "2.0";
    }

    @Override
    public boolean canRegister() {
        // Check if DatabaseManager is initialized before registering
        if (!DatabaseManager.isInitialized()) {
            NHook.getInstance().getLogger().warning("DatabaseManager is not initialized! PlaceholderAPI expansion cannot be registered.");
            return false;
        }

        if (!databaseManager.isConnected()) {
            NHook.getInstance().getLogger().warning("Database is not connected! PlaceholderAPI expansion cannot be registered.");
            return false;
        }

        return true;
    }

    @Override
    public boolean register() {
        if (!canRegister()) {
            return false;
        }

        boolean registered = super.register();
        if (registered) {
            NHook.getInstance().getLogger().info("NHook PlaceholderAPI expansion registered successfully with caching support!");
        }
        return registered;
    }

    /**
     * Handles placeholder requests, supporting dash '-' for columns with underscores.
     * Now includes intelligent caching for improved performance.
     * <p>
     * Examples:
     *  %nhook_players_last-login% (self, column: last_login)
     *  %nhook_players_last-login_aysihuniks% (user: aysihuniks, column: last_login)
     *  %nhook_players_all% (self, all columns as JSON)
     *  %nhook_players_all_aysihuniks% (user: aysihuniks, all columns as JSON)
     * <p>
     * @param player The player who sent the request, or null if from the console.
     * @param params The placeholder parameters, separated by '_' and '-' as described above.
     * @return       The requested value as a string, or empty string if not found.
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Early validation
        if (params.isEmpty()) {
            return "";
        }

        // Check if a database is connected
        if (!databaseManager.isConnected()) {
            NHook.getInstance().getLogger().warning("Database is not connected for placeholder request: " + params);
            return "";
        }

        // Parse parameters
        PlaceholderRequest request = parseParameters(params, player);
        if (request == null) {
            return "";
        }

        try {
            // Get the appropriate future based on a request type
            CompletableFuture<String> future = getFutureForRequest(request);

            // Wait for a result with timeout to prevent blocking
            String value = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return value != null ? value : "";

        } catch (Exception e) {
            // Log error and return empty string to prevent placeholder errors
            String errorMsg = String.format("Error fetching placeholder value for '%s': %s", params, e.getMessage());
            NHook.getInstance().getLogger().warning(errorMsg);

            // Log more details in debug mode
            if (NHook.getInstance().getConfig().getBoolean("cache.debug.enabled", false)) {
                e.printStackTrace();
            }

            return "";
        }
    }

    /**
     * Parses placeholder parameters into a structured request object
     */
    private PlaceholderRequest parseParameters(String params, OfflinePlayer player) {
        // Find the first underscore, which separates the table from the rest
        int underscoreIdx = params.indexOf('_');
        if (underscoreIdx == -1 || underscoreIdx >= params.length() - 1) {
            return null;
        }

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

        // Validate target player
        if (targetPlayer == null || targetPlayer.trim().isEmpty()) {
            return null;
        }

        return new PlaceholderRequest(table, column, targetPlayer);
    }

    /**
     * Gets the appropriate CompletableFuture based on the request type
     */
    private CompletableFuture<String> getFutureForRequest(PlaceholderRequest request) {
        if ("all".equalsIgnoreCase(request.column)) {
            // Fetch all columns as JSON
            return databaseManager.fetchPlayerAllValuesAsync(request.table, request.playerName);
        } else {
            // Fetch specific column value
            return databaseManager.fetchPlayerValueAsync(request.table, request.column, request.playerName);
        }
    }


    /**
     * Provides cache invalidation methods for external use
     */
    public void invalidateCache(String playerName) {
        if (databaseManager != null) {
            databaseManager.invalidatePlayerCache(playerName);
        }
    }

    /**
     * Invalidates cache for a specific table
     */
    public void invalidateTableCache(String table) {
        if (databaseManager != null) {
            databaseManager.invalidateTableCache(table);
        }
    }

    /**
     * Clears all cached data
     */
    public void clearCache() {
        if (databaseManager != null) {
            databaseManager.clearCache();
        }
    }

    /**
     * Gets cache statistics for monitoring
     */
    public String getCacheStats() {
        if (databaseManager != null) {
            return databaseManager.getCacheStats();
        }
        return "DatabaseManager not available";
    }

    /**
     * Inner class to represent a placeholder request
     */
    private static class PlaceholderRequest {
        final String table;
        final String column;
        final String playerName;

        PlaceholderRequest(String table, String column, String playerName) {
            this.table = table;
            this.column = column;
            this.playerName = playerName;
        }

        @Override
        public String toString() {
            return String.format("PlaceholderRequest{table='%s', column='%s', player='%s'}",
                    table, column, playerName);
        }
    }
}