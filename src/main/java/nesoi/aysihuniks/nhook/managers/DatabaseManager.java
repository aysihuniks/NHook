package nesoi.aysihuniks.nhook.managers;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nesoi.aysihuniks.nhook.NHook;

import java.sql.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Async HikariCP-based database manager with advanced caching system
 * Handles MySQL database operations with connection pooling, async operations and intelligent caching
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private static boolean initialized = false;

    private HikariDataSource dataSource;
    private final ConfigManager config;
    private final NHook plugin;
    private final ExecutorService executor;

    // Advanced Cache System
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final ScheduledExecutorService cacheCleanupExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingQueries;

    // Connection pool configuration
    private static final int MINIMUM_IDLE = 2;
    private static final int MAXIMUM_POOL_SIZE = 10;
    private static final long CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final long IDLE_TIMEOUT = 600000; // 10 minutes
    private static final long MAX_LIFETIME = 1800000; // 30 minutes
    private static final long LEAK_DETECTION_THRESHOLD = 60000; // 1 minute

    private DatabaseManager() {
        this.plugin = NHook.getInstance();
        this.config = ConfigManager.getInstance();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "NHook-Database-Thread");
            thread.setDaemon(true);
            return thread;
        });

        // Initialize cache system
        this.cache = new ConcurrentHashMap<>();
        this.pendingQueries = new ConcurrentHashMap<>();
        this.cacheCleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "NHook-Cache-Cleanup");
            thread.setDaemon(true);
            return thread;
        });

        // Start cache cleanup task
        startCacheCleanup();
    }

    /**
     * Gets the singleton instance of DatabaseManager
     * @return DatabaseManager instance
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initializes the database manager (must be called before use)
     * @return true if initialization successful, false otherwise
     */
    public static boolean initialize() {
        if (initialized) {
            return true;
        }

        try {
            DatabaseManager manager = getInstance();
            manager.connect();
            initialized = true;
            manager.plugin.getLogger().info("DatabaseManager initialized successfully with caching system");
            return true;
        } catch (Exception e) {
            NHook.getInstance().getLogger().severe("Failed to initialize DatabaseManager: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if the database manager is initialized
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Cache entry class for storing cached data with TTL
     */
    private static class CacheEntry {
        private final String value;
        private final long expiryTime;
        private final long accessTime;
        private volatile int hitCount;

        public CacheEntry(String value, long ttlMillis) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMillis;
            this.accessTime = System.currentTimeMillis();
            this.hitCount = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }

        public String getValue() {
            hitCount++;
            return value;
        }

        public int getHitCount() {
            return hitCount;
        }

        public long getAccessTime() {
            return accessTime;
        }
    }

    /**
     * Starts the cache cleanup task
     */
    private void startCacheCleanup() {
        cacheCleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredCache();
                if (config.isCacheStatsEnabled()) {
                    logCacheStats();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error during cache cleanup: " + e.getMessage());
            }
        }, config.getCacheCleanupInterval(), config.getCacheCleanupInterval(), TimeUnit.SECONDS);
    }

    /**
     * Cleans up expired cache entries
     */
    private void cleanupExpiredCache() {
        int removedCount = 0;
        long currentTime = System.currentTimeMillis();

        cache.entrySet().removeIf(entry -> {
            CacheEntry cacheEntry = entry.getValue();
            boolean shouldRemove = cacheEntry.isExpired() ||
                    (config.isCacheAutoCleanupEnabled() &&
                            (currentTime - cacheEntry.getAccessTime()) > config.getCacheMaxIdleTime() * 1000);
            return shouldRemove;
        });

        // Cleanup pending queries that might be stuck
        pendingQueries.entrySet().removeIf(entry -> entry.getValue().isDone());

        if (config.isCacheDebugEnabled()) {
            plugin.getLogger().info("Cache cleanup completed. Removed " + removedCount + " expired entries. Current cache size: " + cache.size());
        }
    }

    /**
     * Logs cache statistics
     */
    private void logCacheStats() {
        int totalEntries = cache.size();
        int hitCount = cache.values().stream().mapToInt(CacheEntry::getHitCount).sum();

        plugin.getLogger().info(String.format(
                "Cache Stats - Total Entries: %d, Total Hits: %d, Pending Queries: %d",
                totalEntries, hitCount, pendingQueries.size()
        ));
    }

    /**
     * Generates cache key for database queries
     */
    private String generateCacheKey(String table, String column, String playerName) {
        return String.format("%s:%s:%s", table, column, playerName);
    }

    /**
     * Generates cache key for all values queries
     */
    private String generateAllValuesCacheKey(String table, String playerName) {
        return String.format("%s:all:%s", table, playerName);
    }

    /**
     * Gets value from cache if available and not expired
     */
    private String getFromCache(String cacheKey) {
        if (!config.isCacheEnabled()) {
            return null;
        }

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            if (config.isCacheDebugEnabled()) {
                plugin.getLogger().info("Cache HIT for key: " + cacheKey);
            }
            return entry.getValue();
        }

        if (entry != null && entry.isExpired()) {
            cache.remove(cacheKey);
            if (config.isCacheDebugEnabled()) {
                plugin.getLogger().info("Cache EXPIRED for key: " + cacheKey);
            }
        }

        return null;
    }

    /**
     * Stores value in a cache
     */
    private void storeInCache(String cacheKey, String value) {
        if (!config.isCacheEnabled() || value == null) {
            return;
        }

        // Check cache size limit
        if (cache.size() >= config.getCacheMaxSize()) {
            // Remove oldest entries based on LRU
            cache.entrySet().stream()
                    .min((e1, e2) -> Long.compare(e1.getValue().getAccessTime(), e2.getValue().getAccessTime()))
                    .ifPresent(entry -> cache.remove(entry.getKey()));
        }

        cache.put(cacheKey, new CacheEntry(value, config.getCacheTtl() * 1000));

        if (config.isCacheDebugEnabled()) {
            plugin.getLogger().info("Cache STORE for key: " + cacheKey);
        }
    }

    /**
     * Invalidates cache entries for a specific player
     */
    public void invalidatePlayerCache(String playerName) {
        if (!config.isCacheEnabled()) {
            return;
        }

        cache.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + playerName));

        if (config.isCacheDebugEnabled()) {
            plugin.getLogger().info("Cache invalidated for player: " + playerName);
        }
    }

    /**
     * Invalidates cache entries for a specific table
     */
    public void invalidateTableCache(String table) {
        if (!config.isCacheEnabled()) {
            return;
        }

        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(table + ":"));

        if (config.isCacheDebugEnabled()) {
            plugin.getLogger().info("Cache invalidated for table: " + table);
        }
    }

    /**
     * Clears entire cache
     */
    public void clearCache() {
        cache.clear();
        pendingQueries.clear();
        plugin.getLogger().info("Cache cleared completely");
    }

    /**
     * Initializes HikariCP connection pool with optimized settings
     */
    public void connect() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                plugin.getLogger().warning("Database connection pool is already active.");
                return;
            }

            HikariConfig hikariConfig = new HikariConfig();

            // Basic connection settings
            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s",
                    config.getHost(), config.getPort(), config.getDb());
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.getUser());
            hikariConfig.setPassword(config.getPassword());

            // Pool configuration for ultra performance
            hikariConfig.setMinimumIdle(MINIMUM_IDLE);
            hikariConfig.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
            hikariConfig.setConnectionTimeout(CONNECTION_TIMEOUT);
            hikariConfig.setIdleTimeout(IDLE_TIMEOUT);
            hikariConfig.setMaxLifetime(MAX_LIFETIME);
            hikariConfig.setLeakDetectionThreshold(LEAK_DETECTION_THRESHOLD);

            // MySQL optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            hikariConfig.addDataSourceProperty("useSSL", "false");
            hikariConfig.addDataSourceProperty("allowPublicKeyRetrieval", "true");

            // Connection test and validation
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setValidationTimeout(5000);

            // Pool name for monitoring
            hikariConfig.setPoolName("NHook-MySQL-Pool");

            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("HikariCP database connection pool established successfully with caching system.");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database connection pool: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes the HikariCP connection pool and executor service
     */
    public void disconnect() {
        try {
            // Stop cache cleanup
            if (cacheCleanupExecutor != null && !cacheCleanupExecutor.isShutdown()) {
                cacheCleanupExecutor.shutdown();
                if (!cacheCleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cacheCleanupExecutor.shutdownNow();
                }
            }

            // Clear cache
            clearCache();

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("Database connection pool closed successfully.");
            }

            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            initialized = false;

        } catch (Exception e) {
            plugin.getLogger().severe("Error occurred while closing database resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if the connection pool is active and healthy
     *
     * @return true if connected and healthy, false otherwise
     */
    public boolean isConnected() {
        try {
            return dataSource != null && !dataSource.isClosed() && dataSource.isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Executes a raw SQL query asynchronously with parameters
     *
     * @param sql      The SQL query with ? placeholders
     * @param params   The parameters to bind
     * @return         CompletableFuture with ResultSet data as JsonArray string
     */
    public CompletableFuture<String> rawQueryAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for rawQuery");
                return null;
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Bind parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    return resultSetToJson(rs);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing async raw query: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executor);
    }

    /**
     * Executes an update/insert/delete query asynchronously
     *
     * @param sql      The SQL query
     * @param params   The parameters to bind
     * @return         CompletableFuture with affected rows count
     */
    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for executeUpdate");
                return -1;
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Bind parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                return stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing async update: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
        }, executor);
    }

    /**
     * Fetches a specific column value for a player asynchronously with caching
     *
     * @param table      The table name
     * @param column     The column name
     * @param playerName The player name
     * @return           CompletableFuture with the value as String
     */
    public CompletableFuture<String> fetchPlayerValueAsync(String table, String column, String playerName) {
        String cacheKey = generateCacheKey(table, column, playerName);

        // Check cache first
        String cachedValue = getFromCache(cacheKey);
        if (cachedValue != null) {
            return CompletableFuture.completedFuture(cachedValue);
        }

        // Check if a query is already pending to avoid duplicate requests
        CompletableFuture<String> pendingFuture = pendingQueries.get(cacheKey);
        if (pendingFuture != null && !pendingFuture.isDone()) {
            return pendingFuture;
        }

        // Create a new query future
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for fetchPlayerValue");
                return null;
            }

            String query = "SELECT `" + column + "` FROM `" + table + "` WHERE `player` = ? LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, playerName);

                try (ResultSet rs = stmt.executeQuery()) {
                    String result = rs.next() ? rs.getString(column) : null;

                    // Store in cache
                    if (result != null) {
                        storeInCache(cacheKey, result);
                    }

                    return result;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching player value: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executor);

        // Store pending query
        pendingQueries.put(cacheKey, future);

        // Remove from pending when complete
        future.whenComplete((result, throwable) -> pendingQueries.remove(cacheKey));

        return future;
    }

    /**
     * Fetches all column values for a player as JSON asynchronously with caching
     *
     * @param table      The table name
     * @param playerName The player name
     * @return           CompletableFuture with JSON string of all values
     */
    public CompletableFuture<String> fetchPlayerAllValuesAsync(String table, String playerName) {
        String cacheKey = generateAllValuesCacheKey(table, playerName);

        // Check cache first
        String cachedValue = getFromCache(cacheKey);
        if (cachedValue != null) {
            return CompletableFuture.completedFuture(cachedValue);
        }

        // Check if a query is already pending
        CompletableFuture<String> pendingFuture = pendingQueries.get(cacheKey);
        if (pendingFuture != null && !pendingFuture.isDone()) {
            return pendingFuture;
        }

        // Create a new query future
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for fetchPlayerAllValues");
                return null;
            }

            String query = "SELECT * FROM `" + table + "` WHERE `player` = ? LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, playerName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String result = resultSetRowToJson(rs);

                        // Store in cache
                        if (result != null) {
                            storeInCache(cacheKey, result);
                        }

                        return result;
                    }
                    return null;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching all player values: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executor);

        // Store pending query
        pendingQueries.put(cacheKey, future);

        // Remove from pending when complete
        future.whenComplete((result, throwable) -> pendingQueries.remove(cacheKey));

        return future;
    }

    /**
     * Executes a query with a callback for immediate processing (memory efficient)
     *
     * @param sql      The SQL query
     * @param callback The callback to process ResultSet
     * @param params   The parameters to bind
     */
    public void executeQueryWithCallback(String sql, Consumer<ResultSet> callback, Object... params) {
        executor.submit(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for callback query");
                return;
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Bind parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    callback.accept(rs);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing callback query: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Batch insert/update operations for high performance
     *
     * @param sql        The SQL query
     * @param paramsList List of parameter arrays
     * @return           CompletableFuture with total affected rows
     */
    public CompletableFuture<Integer> executeBatchAsync(String sql, Object[][] paramsList) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for batch operation");
                return -1;
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);

                for (Object[] params : paramsList) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                    stmt.addBatch();
                }

                int[] results = stmt.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);

                return java.util.Arrays.stream(results).sum();

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing batch operation: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
        }, executor);
    }

    /**
     * Converts a single ResultSet row to JSON string
     */
    private String resultSetRowToJson(ResultSet rs) throws SQLException {
        JsonObject json = new JsonObject();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnName(i);
            Object value = rs.getObject(i);

            if (value == null) {
                json.add(columnName, null);
            } else if (value instanceof String) {
                json.addProperty(columnName, (String) value);
            } else if (value instanceof Number) {
                json.addProperty(columnName, (Number) value);
            } else if (value instanceof Boolean) {
                json.addProperty(columnName, (Boolean) value);
            } else {
                json.addProperty(columnName, value.toString());
            }
        }

        return json.toString();
    }

    /**
     * Converts full ResultSet to JSON array string
     */
    private String resultSetToJson(ResultSet rs) throws SQLException {
        StringBuilder jsonArray = new StringBuilder("[");
        boolean first = true;

        while (rs.next()) {
            if (!first) {
                jsonArray.append(",");
            }
            jsonArray.append(resultSetRowToJson(rs));
            first = false;
        }

        jsonArray.append("]");
        return jsonArray.toString();
    }

    /**
     * Gets connection pool statistics for monitoring
     */
    public String getPoolStats() {
        if (dataSource == null) return "Pool not initialized";

        return String.format(
                "Pool Stats - Active: %d, Idle: %d, Total: %d, Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * Gets cache statistics for monitoring
     */
    public String getCacheStats() {
        if (!config.isCacheEnabled()) {
            return "Cache is disabled";
        }

        int totalEntries = cache.size();
        int totalHits = cache.values().stream().mapToInt(CacheEntry::getHitCount).sum();
        int pendingCount = pendingQueries.size();

        return String.format(
                "Cache Stats - Entries: %d, Total Hits: %d, Pending: %d, Max Size: %d",
                totalEntries, totalHits, pendingCount, config.getCacheMaxSize()
        );
    }
}