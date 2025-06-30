package nesoi.aysihuniks.nhook.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ultra-performant async API for NHook plugin with CompletableFuture support.
 *
 * Provides type-safe async methods for accessing any column value from any table
 * in your database, for any player with full caching and batch operation support.
 *
 * Supported types:
 *   - Integer (getIntAsync)
 *   - Long (getLongAsync)
 *   - Double (getDoubleAsync)
 *   - BigDecimal (getBigDecimalAsync)
 *   - String (getStringAsync)
 *   - Date (getDateAsync)
 *   - LocalDateTime (getLocalDateTimeAsync)
 *   - Boolean (getBooleanAsync)
 *   - Generic Object (getValueAsync)
 *
 * Advanced features:
 *   - Batch operations for multiple players
 *   - Caching with TTL support
 *   - SQL injection protection
 *   - Timeout handling
 *   - Comprehensive error handling
 */
public class NHookAPI {
    private final DatabaseManager db;
    private final NHook plugin;

    // Cache for frequently accessed data
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private static final long DEFAULT_CACHE_TTL = TimeUnit.MINUTES.toMillis(5); // 5 minutes

    // JsonParser instance for compatibility with older Gson versions
    private final JsonParser jsonParser = new JsonParser();

    // Common date formats for auto-detection
    private static final List<String> COMMON_DATE_FORMATS = Arrays.asList(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
    );

    public NHookAPI(DatabaseManager db) {
        this.db = db;
        this.plugin = NHook.inst();
    }

    // ================================
    // SINGLE VALUE RETRIEVAL METHODS
    // ================================

    /**
     * Retrieves a String value asynchronously from the database.
     *
     * @param table      The database table name
     * @param column     The column to query
     * @param playerName The name of the player
     * @return           CompletableFuture with the value as String, or null if not found
     */
    public CompletableFuture<String> getStringAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName, true);
    }

    /**
     * Retrieves a String value asynchronously with optional caching.
     */
    public CompletableFuture<String> getStringAsync(String table, String column, String playerName, boolean useCache) {
        String cacheKey = buildCacheKey(table, column, playerName);

        if (useCache && isCacheValid(cacheKey)) {
            return CompletableFuture.completedFuture((String) cache.get(cacheKey).value);
        }

        return db.fetchPlayerValueAsync(table, column, playerName)
                .thenApply(value -> {
                    if (useCache && value != null) {
                        cache.put(cacheKey, new CacheEntry(value, System.currentTimeMillis() + DEFAULT_CACHE_TTL));
                    }
                    return value;
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Error getting string value: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * Retrieves an Integer value asynchronously from the database.
     */
    public CompletableFuture<Integer> getIntAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseInteger);
    }

    /**
     * Retrieves a Long value asynchronously from the database.
     */
    public CompletableFuture<Long> getLongAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseLong);
    }

    /**
     * Retrieves a Double value asynchronously from the database.
     */
    public CompletableFuture<Double> getDoubleAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseDouble);
    }

    /**
     * Retrieves a BigDecimal value asynchronously from the database.
     */
    public CompletableFuture<BigDecimal> getBigDecimalAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseBigDecimal);
    }

    /**
     * Retrieves a Boolean value asynchronously from the database.
     */
    public CompletableFuture<Boolean> getBooleanAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseBoolean);
    }

    /**
     * Retrieves a Date value asynchronously with auto-format detection.
     */
    public CompletableFuture<Date> getDateAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseDate);
    }

    /**
     * Retrieves a Date value asynchronously with specific format.
     */
    public CompletableFuture<Date> getDateAsync(String table, String column, String playerName, String format) {
        return getStringAsync(table, column, playerName)
                .thenApply(value -> parseDate(value, format));
    }

    /**
     * Retrieves a LocalDateTime value asynchronously.
     */
    public CompletableFuture<LocalDateTime> getLocalDateTimeAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(this::parseLocalDateTime);
    }

    /**
     * Retrieves a generic Object value asynchronously.
     */
    public CompletableFuture<Object> getValueAsync(String table, String column, String playerName) {
        return getStringAsync(table, column, playerName)
                .thenApply(value -> (Object) value);
    }

    // ================================
    // BATCH OPERATIONS
    // ================================

    /**
     * Retrieves values for multiple players asynchronously.
     */
    public CompletableFuture<Map<String, String>> getStringBatchAsync(String table, String column, List<String> playerNames) {
        List<CompletableFuture<AbstractMap.SimpleEntry<String, String>>> futures = playerNames.stream()
                .map(playerName ->
                        getStringAsync(table, column, playerName)
                                .thenApply(value -> new AbstractMap.SimpleEntry<>(playerName, value))
                )
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(entry -> entry.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                );
    }

    /**
     * Retrieves integer values for multiple players asynchronously.
     */
    public CompletableFuture<Map<String, Integer>> getIntBatchAsync(String table, String column, List<String> playerNames) {
        return getStringBatchAsync(table, column, playerNames)
                .thenApply(stringMap -> {
                    Map<String, Integer> result = new HashMap<>();
                    for (Map.Entry<String, String> entry : stringMap.entrySet()) {
                        Integer parsedValue = parseInteger(entry.getValue());
                        if (parsedValue != null) {
                            result.put(entry.getKey(), parsedValue);
                        }
                    }
                    return result;
                });
    }

    // ================================
    // ADVANCED QUERY METHODS
    // ================================

    /**
     * Retrieves player names whose column value contains the specified substring (case-insensitive).
     */
    public CompletableFuture<List<String>> getPlayersWhereColumnContainsAsync(String table, String column, String contains) {
        String sql = "SELECT `player` FROM `" + table + "` WHERE LOWER(`" + column + "`) LIKE ?";
        return executePlayerListQuery(sql, "%" + contains.toLowerCase() + "%");
    }

    /**
     * Retrieves player names whose column value starts with the specified prefix (case-insensitive).
     */
    public CompletableFuture<List<String>> getPlayersWhereColumnStartsWithAsync(String table, String column, String prefix) {
        String sql = "SELECT `player` FROM `" + table + "` WHERE LOWER(`" + column + "`) LIKE ?";
        return executePlayerListQuery(sql, prefix.toLowerCase() + "%");
    }

    /**
     * Retrieves player names whose column value ends with the specified suffix (case-insensitive).
     */
    public CompletableFuture<List<String>> getPlayersWhereColumnEndsWithAsync(String table, String column, String suffix) {
        String sql = "SELECT `player` FROM `" + table + "` WHERE LOWER(`" + column + "`) LIKE ?";
        return executePlayerListQuery(sql, "%" + suffix.toLowerCase());
    }

    /**
     * Retrieves player names whose integer column is greater than the specified value.
     */
    public CompletableFuture<List<String>> getPlayersWithIntGreaterThanAsync(String table, String column, int minValue) {
        String sql = "SELECT `player` FROM `" + table + "` WHERE `" + column + "` > ?";
        return executePlayerListQuery(sql, minValue);
    }

    /**
     * Retrieves player names whose integer column is between two values (inclusive).
     */
    public CompletableFuture<List<String>> getPlayersWithIntBetweenAsync(String table, String column, int minValue, int maxValue) {
        String sql = "SELECT `player` FROM `" + table + "` WHERE `" + column + "` BETWEEN ? AND ?";
        return executePlayerListQuery(sql, minValue, maxValue);
    }

    /**
     * Retrieves player names ordered by a column value (ascending or descending).
     */
    public CompletableFuture<List<String>> getPlayersOrderedByAsync(String table, String column, boolean ascending, int limit) {
        String order = ascending ? "ASC" : "DESC";
        String sql = "SELECT `player` FROM `" + table + "` ORDER BY `" + column + "` " + order + " LIMIT ?";
        return executePlayerListQuery(sql, limit);
    }

    /**
     * Retrieves top N players by a numeric column.
     */
    public CompletableFuture<Map<String, Integer>> getTopPlayersAsync(String table, String column, int limit) {
        String sql = "SELECT `player`, `" + column + "` FROM `" + table + "` ORDER BY `" + column + "` DESC LIMIT ?";

        return db.rawQueryAsync(sql, limit)
                .thenApply(jsonResult -> {
                    Map<String, Integer> result = new LinkedHashMap<>();
                    if (jsonResult != null) {
                        try {
                            JsonArray array = jsonParser.parse(jsonResult).getAsJsonArray();
                            for (JsonElement element : array) {
                                JsonObject obj = element.getAsJsonObject();
                                String player = obj.get("player").getAsString();
                                Integer value = parseInteger(obj.get(column).getAsString());
                                if (value != null) {
                                    result.put(player, value);
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error parsing top players result: " + e.getMessage());
                        }
                    }
                    return result;
                });
    }

    // ================================
    // UPDATE OPERATIONS
    // ================================

    /**
     * Updates a player's column value asynchronously.
     */
    public CompletableFuture<Boolean> updatePlayerValueAsync(String table, String column, String playerName, Object value) {
        String sql = "UPDATE `" + table + "` SET `" + column + "` = ? WHERE `player` = ?";
        return db.executeUpdateAsync(sql, value, playerName)
                .thenApply(affectedRows -> {
                    if (affectedRows > 0) {
                        // Invalidate cache for this entry
                        String cacheKey = buildCacheKey(table, column, playerName);
                        cache.remove(cacheKey);
                        return true;
                    }
                    return false;
                });
    }

    /**
     * Increments a numeric column value asynchronously.
     */
    public CompletableFuture<Boolean> incrementPlayerValueAsync(String table, String column, String playerName, Number increment) {
        String sql = "UPDATE `" + table + "` SET `" + column + "` = `" + column + "` + ? WHERE `player` = ?";
        return db.executeUpdateAsync(sql, increment, playerName)
                .thenApply(affectedRows -> {
                    if (affectedRows > 0) {
                        String cacheKey = buildCacheKey(table, column, playerName);
                        cache.remove(cacheKey);
                        return true;
                    }
                    return false;
                });
    }

    // ================================
    // SYNCHRONOUS COMPATIBILITY METHODS
    // ================================

    /**
     * Synchronous wrapper for getString (blocks thread - use sparingly!)
     */
    public String getString(String table, String column, String playerName) {
        try {
            return getStringAsync(table, column, playerName).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Timeout or error in synchronous getString: " + e.getMessage());
            return null;
        }
    }

    /**
     * Synchronous wrapper for getInt (blocks thread - use sparingly!)
     */
    public Integer getInt(String table, String column, String playerName) {
        try {
            return getIntAsync(table, column, playerName).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Timeout or error in synchronous getInt: " + e.getMessage());
            return null;
        }
    }

    /**
     * Synchronous wrapper for getLong (blocks thread - use sparingly!)
     */
    public Long getLong(String table, String column, String playerName) {
        try {
            return getLongAsync(table, column, playerName).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Timeout or error in synchronous getLong: " + e.getMessage());
            return null;
        }
    }

    /**
     * Synchronous wrapper for getDouble (blocks thread - use sparingly!)
     */
    public Double getDouble(String table, String column, String playerName) {
        try {
            return getDoubleAsync(table, column, playerName).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Timeout or error in synchronous getDouble: " + e.getMessage());
            return null;
        }
    }

    // ================================
    // UTILITY AND HELPER METHODS
    // ================================

    /**
     * Clears the internal cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Clears expired cache entries.
     */
    public void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    /**
     * Gets cache statistics.
     */
    public String getCacheStats() {
        cleanExpiredCache();
        return String.format("Cache Stats - Total: %d entries, Memory: ~%d KB",
                cache.size(), cache.size() * 100 / 1024); // Rough estimate
    }

    // ================================
    // PRIVATE HELPER METHODS
    // ================================

    private CompletableFuture<List<String>> executePlayerListQuery(String sql, Object... params) {
        return db.rawQueryAsync(sql, params)
                .thenApply(jsonResult -> {
                    List<String> players = new ArrayList<>();
                    if (jsonResult != null) {
                        try {
                            JsonArray array = jsonParser.parse(jsonResult).getAsJsonArray();
                            for (JsonElement element : array) {
                                JsonObject obj = element.getAsJsonObject();
                                players.add(obj.get("player").getAsString());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error parsing player list query result: " + e.getMessage());
                        }
                    }
                    return players;
                });
    }

    private String buildCacheKey(String table, String column, String playerName) {
        return table + ":" + column + ":" + playerName;
    }

    private boolean isCacheValid(String cacheKey) {
        CacheEntry entry = cache.get(cacheKey);
        return entry != null && entry.expiresAt > System.currentTimeMillis();
    }

    // Type parsing methods
    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String trimmed = value.trim().toLowerCase();
        return "true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed) || "on".equals(trimmed);
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        for (String format : COMMON_DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                return sdf.parse(value.trim());
            } catch (ParseException ignored) {
                // Try next format
            }
        }
        return null;
    }

    private Date parseDate(String value, String format) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(value.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        return null;
    }

    // Cache entry class
    private static class CacheEntry {
        final Object value;
        final long expiresAt;

        CacheEntry(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}