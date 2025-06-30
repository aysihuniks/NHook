package nesoi.aysihuniks.nhook.managers;

import lombok.Getter;
import lombok.Setter;
import nesoi.aysihuniks.nhook.NHook;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

@Getter
@Setter
public class ConfigManager {

    private static ConfigManager instance;
    private final NHook plugin;
    private static FileConfiguration config;

    // Database Settings
    private String host;
    private int port;
    private String db;
    private String user;
    private String password;

    // Legacy settings (for backward compatibility)
    private String table;
    private String column;

    // Cache Settings
    private boolean cacheEnabled;
    private int cacheMaxSize;
    private long cacheTtl; // Time to live in seconds
    private int cacheCleanupInterval; // Cleanup interval in seconds
    private boolean cacheAutoCleanupEnabled;
    private long cacheMaxIdleTime; // Max idle time in seconds before cleanup
    private boolean cacheStatsEnabled;
    private boolean cacheDebugEnabled;
    private boolean cacheWarmupEnabled;
    private int cacheWarmupDelay; // Warmup delay in seconds

    // Performance Settings
    private int maxConcurrentQueries;
    private int queryTimeoutSeconds;
    private boolean useConnectionPooling;
    private int connectionPoolSize;
    private long connectionIdleTimeout;

    // Logging Settings
    private boolean detailedLogging;
    private boolean queryLogging;
    private boolean performanceLogging;
    private int logLevel; // 0=OFF, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG

    private String messagePrefix;


    private ConfigManager(NHook plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the Config singleton instance
     * @param plugin The NHook plugin instance
     * @return The initialized Config instance
     */
    public static ConfigManager initialize(NHook plugin) {
        if (instance == null) {
            instance = new ConfigManager(plugin);
            instance.load();
        }
        return instance;
    }

    /**
     * Get the Config singleton instance
     * @return The Config instance
     * @throws IllegalStateException if the instance hasn't been initialized
     */
    public static ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Config has not been initialized! Call initialize(plugin) first.");
        }
        return instance;
    }

    /**
     * Reset the singleton instance (useful for testing or plugin reload)
     */
    public static void reset() {
        instance = null;
    }

    public ConfigManager load() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);

        // Load database settings
        loadDatabaseSettings();

        // Load cache settings
        loadCacheSettings();

        // Load performance settings
        loadPerformanceSettings();

        // Load logging settings
        loadLoggingSettings();

        loadMessageSettings();

        return this;
    }

    private void loadMessageSettings() {
        setMessagePrefix(config.getString("messages.prefix", "&8[&6NHook&8]&r "));
    }
    private void saveMessageSettings() {
        config.set("messages.prefix", getMessagePrefix());
    }

    private void loadDatabaseSettings() {
        setHost(config.getString("database.host", "localhost"));
        setPort(config.getInt("database.port", 3306));
        setDb(config.getString("database.db", "minecraft"));
        setUser(config.getString("database.user", "root"));
        setPassword(config.getString("database.password", ""));

        // Legacy settings
        setTable(config.getString("database.table", "players"));
        setColumn(config.getString("database.column", "player"));
    }

    private void loadCacheSettings() {
        setCacheEnabled(config.getBoolean("cache.enabled", true));
        setCacheMaxSize(config.getInt("cache.max-size", 1000));
        setCacheTtl(config.getLong("cache.ttl-seconds", 300)); // 5-minute default
        setCacheCleanupInterval(config.getInt("cache.cleanup-interval-seconds", 60)); // 1-minute default

        setCacheAutoCleanupEnabled(config.getBoolean("cache.auto-cleanup.enabled", true));
        setCacheMaxIdleTime(config.getLong("cache.auto-cleanup.max-idle-seconds", 1800)); // 30-minute default

        setCacheStatsEnabled(config.getBoolean("cache.stats.enabled", false));
        setCacheDebugEnabled(config.getBoolean("cache.debug.enabled", false));

        setCacheWarmupEnabled(config.getBoolean("cache.warmup.enabled", false));
        setCacheWarmupDelay(config.getInt("cache.warmup.delay-seconds", 30));
    }

    private void loadPerformanceSettings() {
        setMaxConcurrentQueries(config.getInt("performance.max-concurrent-queries", 50));
        setQueryTimeoutSeconds(config.getInt("performance.query-timeout-seconds", 30));

        setUseConnectionPooling(config.getBoolean("performance.connection-pooling.enabled", true));
        setConnectionPoolSize(config.getInt("performance.connection-pooling.pool-size", 10));
        setConnectionIdleTimeout(config.getLong("performance.connection-pooling.idle-timeout-seconds", 600));
    }

    private void loadLoggingSettings() {
        setDetailedLogging(config.getBoolean("logging.detailed", false));
        setQueryLogging(config.getBoolean("logging.queries", false));
        setPerformanceLogging(config.getBoolean("logging.performance", false));
        setLogLevel(config.getInt("logging.level", 2)); // WARN level default
    }


    public void save() {
        try {
            // Save database settings
            saveDatabaseSettings();

            // Save cache settings
            saveCacheSettings();

            // Save performance settings
            savePerformanceSettings();

            // Save logging settings
            saveLoggingSettings();

            saveMessageSettings();

            config.save(new File(plugin.getDataFolder(), "config.yml"));
            plugin.getLogger().info("Configuration saved successfully.");

        } catch (IOException e) {
            plugin.getLogger().severe("An error occurred while saving config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveDatabaseSettings() {
        config.set("database.host", getHost());
        config.set("database.port", getPort());
        config.set("database.db", getDb());
        config.set("database.user", getUser());
        config.set("database.password", getPassword());
        config.set("database.table", getTable());
        config.set("database.column", getColumn());
    }

    private void saveCacheSettings() {
        config.set("cache.enabled", isCacheEnabled());
        config.set("cache.max-size", getCacheMaxSize());
        config.set("cache.ttl-seconds", getCacheTtl());
        config.set("cache.cleanup-interval-seconds", getCacheCleanupInterval());

        config.set("cache.auto-cleanup.enabled", isCacheAutoCleanupEnabled());
        config.set("cache.auto-cleanup.max-idle-seconds", getCacheMaxIdleTime());

        config.set("cache.stats.enabled", isCacheStatsEnabled());
        config.set("cache.debug.enabled", isCacheDebugEnabled());

        config.set("cache.warmup.enabled", isCacheWarmupEnabled());
        config.set("cache.warmup.delay-seconds", getCacheWarmupDelay());
    }

    private void savePerformanceSettings() {
        config.set("performance.max-concurrent-queries", getMaxConcurrentQueries());
        config.set("performance.query-timeout-seconds", getQueryTimeoutSeconds());

        config.set("performance.connection-pooling.enabled", isUseConnectionPooling());
        config.set("performance.connection-pooling.pool-size", getConnectionPoolSize());
        config.set("performance.connection-pooling.idle-timeout-seconds", getConnectionIdleTimeout());
    }

    private void saveLoggingSettings() {
        config.set("logging.detailed", isDetailedLogging());
        config.set("logging.queries", isQueryLogging());
        config.set("logging.performance", isPerformanceLogging());
        config.set("logging.level", getLogLevel());
    }

    /**
     * Reloads the configuration from a file
     */
    public void reload() {
        load();
        plugin.getLogger().info("Configuration reloaded successfully.");
    }

    /**
     * Creates default configuration with comments
     */
    public void createDefaults() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                plugin.getDataFolder().mkdirs();
                configFile.createNewFile();
            }

            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(configFile);

            // Add header comment
            defaultConfig.options().header(
                    "NHook Configuration File\n" +
                            "========================\n" +
                            "This file contains all configuration options for NHook plugin.\n" +
                            "For more information, visit: https://github.com/your-repo/nhook\n"
            );

            // Database settings
            addDatabaseDefaults(defaultConfig);

            // Cache settings
            addCacheDefaults(defaultConfig);

            // Performance settings
            addPerformanceDefaults(defaultConfig);

            // Logging settings
            addLoggingDefaults(defaultConfig);

            defaultConfig.save(configFile);

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addDatabaseDefaults(FileConfiguration config) {
        config.addDefault("database.host", "localhost");
        config.addDefault("database.port", 3306);
        config.addDefault("database.db", "minecraft");
        config.addDefault("database.user", "root");
        config.addDefault("database.password", "");
        config.addDefault("database.table", "players");
        config.addDefault("database.column", "player");
    }

    private void addCacheDefaults(FileConfiguration config) {
        config.addDefault("cache.enabled", true);
        config.addDefault("cache.max-size", 1000);
        config.addDefault("cache.ttl-seconds", 300);
        config.addDefault("cache.cleanup-interval-seconds", 60);

        config.addDefault("cache.auto-cleanup.enabled", true);
        config.addDefault("cache.auto-cleanup.max-idle-seconds", 1800);

        config.addDefault("cache.stats.enabled", false);
        config.addDefault("cache.debug.enabled", false);

        config.addDefault("cache.warmup.enabled", false);
        config.addDefault("cache.warmup.delay-seconds", 30);
    }

    private void addPerformanceDefaults(FileConfiguration config) {
        config.addDefault("performance.max-concurrent-queries", 50);
        config.addDefault("performance.query-timeout-seconds", 30);

        config.addDefault("performance.connection-pooling.enabled", true);
        config.addDefault("performance.connection-pooling.pool-size", 10);
        config.addDefault("performance.connection-pooling.idle-timeout-seconds", 600);
    }

    private void addLoggingDefaults(FileConfiguration config) {
        config.addDefault("logging.detailed", false);
        config.addDefault("logging.queries", false);
        config.addDefault("logging.performance", false);
        config.addDefault("logging.level", 2);
    }

    /**
     * Validates the configuration and fixes any invalid values
     */
    public boolean validate() {
        boolean valid = true;

        // Validate database settings
        if (getHost() == null || getHost().trim().isEmpty()) {
            plugin.getLogger().warning("Database host is empty, using default: localhost");
            setHost("localhost");
            valid = false;
        }

        if (getPort() <= 0 || getPort() > 65535) {
            plugin.getLogger().warning("Invalid database port, using default: 3306");
            setPort(3306);
            valid = false;
        }

        if (getDb() == null || getDb().trim().isEmpty()) {
            plugin.getLogger().warning("Database name is empty, using default: minecraft");
            setDb("minecraft");
            valid = false;
        }

        // Validate cache settings
        if (getCacheMaxSize() <= 0) {
            plugin.getLogger().warning("Invalid cache max size, using default: 1000");
            setCacheMaxSize(1000);
            valid = false;
        }

        if (getCacheTtl() <= 0) {
            plugin.getLogger().warning("Invalid cache TTL, using default: 300 seconds");
            setCacheTtl(300);
            valid = false;
        }

        if (getCacheCleanupInterval() <= 0) {
            plugin.getLogger().warning("Invalid cache cleanup interval, using default: 60 seconds");
            setCacheCleanupInterval(60);
            valid = false;
        }

        // Validate performance settings
        if (getMaxConcurrentQueries() <= 0) {
            plugin.getLogger().warning("Invalid max concurrent queries, using default: 50");
            setMaxConcurrentQueries(50);
            valid = false;
        }

        if (getQueryTimeoutSeconds() <= 0) {
            plugin.getLogger().warning("Invalid query timeout, using default: 30 seconds");
            setQueryTimeoutSeconds(30);
            valid = false;
        }

        if (getConnectionPoolSize() <= 0) {
            plugin.getLogger().warning("Invalid connection pool size, using default: 10");
            setConnectionPoolSize(10);
            valid = false;
        }

        // Validate logging settings
        if (getLogLevel() < 0 || getLogLevel() > 4) {
            plugin.getLogger().warning("Invalid log level, using default: 2 (WARN)");
            setLogLevel(2);
            valid = false;
        }

        if (!valid) {
            plugin.getLogger().info("Configuration validation completed with fixes applied.");
            save(); // Save the corrected configuration
        }

        return valid;
    }

    /**
     * Gets configuration summary for admin commands
     */
    public String getConfigSummary() {
        return String.format(
                "Database: %s:%d/%s | Cache: %s (Size: %d, TTL: %ds) | Pool: %d connections | Log Level: %d",
                getHost(), getPort(), getDb(),
                isCacheEnabled() ? "ON" : "OFF",
                getCacheMaxSize(), getCacheTtl(),
                getConnectionPoolSize(), getLogLevel()
        );
    }
}