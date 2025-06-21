package nesoi.aysihuniks.nhook.database;

import nesoi.aysihuniks.nhook.Config;
import nesoi.aysihuniks.nhook.NHook;

import java.sql.*;

/**
 * Handles MySQL database operations for the plugin.
 * Manages connection, disconnection, and basic queries.
 */
public class DatabaseManager {

    private Connection connection;
    private final Config config;
    private final NHook plugin;

    public DatabaseManager() {
        plugin = NHook.inst();
        config = new Config(plugin).load();
    }

    /**
     * Establishes a connection to the MySQL database using the configuration.
     */
    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                plugin.getLogger().warning("Database connection is already active.");
                return;
            }

            String url = "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDb() + "?useSSL=false";
            connection = DriverManager.getConnection(url, config.getUser(), config.getPassword());
            plugin.getLogger().info("Database connectivity established.");
        } catch (Exception e) {
            plugin.getLogger().severe("An error occurred while connecting to database: " + e.getMessage());
        }
    }

    /**
     * Closes the connection to the database if it is open.
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connectivity closed.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("An error occurred while closing database connection: " + e.getMessage());
        }
    }

    /**
     * Checks whether the database connection is currently open.
     *
     * @return true if the connection is inactive or closed, false otherwise.
     */
    public boolean isConnected() {
        try {
            return connection == null || connection.isClosed();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Executes a parameterized SQL query and returns the ResultSet.
     * You must close the ResultSet after reading!
     *
     * @param sql     The SQL query with ? placeholders.
     * @param params  The parameters to set in the query.
     * @return        The ResultSet object, or null if an error occurs.
     */
    public ResultSet rawQuery(String sql, Object... params) {
        try {
            if (isConnected()) return null;
            PreparedStatement stmt = connection.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            return stmt.executeQuery();
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing rawQuery: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the value of a specified column for a player from the given table in the database.
     *
     * @param table      The table name (e.g., "players").
     * @param column     The column name (e.g., "credit", "last_login").
     * @param playerName The player name.
     * @return           The value as a String, or null if not found.
     */
    public String fetchPlayerValue(String table, String column, String playerName) {
        String result = null;
        try {
            if (isConnected()) return null;
            String query = "SELECT " + column + " FROM " + table + " WHERE player = ? LIMIT 1";
            PreparedStatement ps = connection.prepareStatement(query);
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) result = rs.getString(column);
            rs.close();
            ps.close();
        } catch (Exception e) {
            plugin.getLogger().severe("Error fetching player value: " + e.getMessage());
        }
        return result;
    }

    /**
     * Retrieves all columns for a player from the given table as a JSON string.
     *
     * @param table      The table name (e.g., "players").
     * @param playerName The player name.
     * @return           All column data as JSON or null if not found.
     */
    public String fetchPlayerAllValues(String table, String playerName) {
        String result = null;
        try {
            if (isConnected()) return null;
            String query = "SELECT * FROM " + table + " WHERE player = ? LIMIT 1";
            PreparedStatement ps = connection.prepareStatement(query);
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                StringBuilder builder = new StringBuilder();
                builder.append("{");
                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnName(i);
                    String val = rs.getString(i);
                    builder.append("\"").append(colName).append("\":\"").append(val).append("\"");
                    if (i < columnCount) builder.append(",");
                }
                builder.append("}");
                result = builder.toString();
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            plugin.getLogger().severe("Error fetching all player values: " + e.getMessage());
        }
        return result;
    }
}