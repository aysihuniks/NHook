package nesoi.aysihuniks.nhook.database;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nesoi.aysihuniks.nhook.Config;
import nesoi.aysihuniks.nhook.NHook;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Async HikariCP-based database manager for ultra performance
 * Handles MySQL database operations with connection pooling and async operations
 */
public class DatabaseManager {

    private HikariDataSource dataSource;
    private final Config config;
    private final NHook plugin;
    private final ExecutorService executor;

    // Connection pool configuration
    private static final int MINIMUM_IDLE = 2;
    private static final int MAXIMUM_POOL_SIZE = 10;
    private static final long CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final long IDLE_TIMEOUT = 600000; // 10 minutes
    private static final long MAX_LIFETIME = 1800000; // 30 minutes
    private static final long LEAK_DETECTION_THRESHOLD = 60000; // 1 minute

    public DatabaseManager() {
        this.plugin = NHook.inst();
        this.config = new Config(plugin).load();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "NHook-Database-Thread");
            thread.setDaemon(true);
            return thread;
        });
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
            plugin.getLogger().info("HikariCP database connection pool established successfully.");

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
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("Database connection pool closed successfully.");
            }

            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

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
     * Fetches a specific column value for a player asynchronously
     *
     * @param table      The table name
     * @param column     The column name
     * @param playerName The player name
     * @return           CompletableFuture with the value as String
     */
    public CompletableFuture<String> fetchPlayerValueAsync(String table, String column, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("Database is not connected for fetchPlayerValue");
                return null;
            }

            String query = "SELECT `" + column + "` FROM `" + table + "` WHERE `player` = ? LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, playerName);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getString(column) : null;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching player value: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executor);
    }

    /**
     * Fetches all column values for a player as JSON asynchronously
     *
     * @param table      The table name
     * @param playerName The player name
     * @return           CompletableFuture with JSON string of all values
     */
    public CompletableFuture<String> fetchPlayerAllValuesAsync(String table, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
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
                        return resultSetRowToJson(rs);
                    }
                    return null;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching all player values: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executor);
    }

    /**
     * Executes a query with callback for immediate processing (memory efficient)
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
}