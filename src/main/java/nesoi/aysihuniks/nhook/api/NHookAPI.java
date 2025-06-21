package nesoi.aysihuniks.nhook.api;

import nesoi.aysihuniks.nhook.NHook;
import nesoi.aysihuniks.nhook.database.DatabaseManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;

/**
 * Public API for NHook plugin.
 * <p>
 * Provides type-safe methods for accessing any column value from any table
 * in your database, for any player.
 * <p>
 * Supported types:
 *   - Integer (getInt)
 *   - Long (getLong)
 *   - Double (getDouble)
 *   - String (getString)
 *   - Date (getDate) // Date columns must be stored in a supported string format (e.g., "yyyy-MM-dd HH:mm:ss")
 * <p>
 * Also includes methods for advanced queries, such as finding all players whose column value contains a string.
 */
public class NHookAPI {
    private final DatabaseManager db;

    public NHookAPI(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Retrieves a String value from the database for the specified table, column, and player.
     *
     * @param table      The database table name.
     * @param column     The column to query.
     * @param playerName The name of the player.
     * @return           The value as String, or null if not found.
     */
    public String getString(String table, String column, String playerName) {
        return db.fetchPlayerValue(table, column, playerName);
    }

    /**
     * Retrieves an Integer value from the database for the specified table, column, and player.
     * Returns null if the column does not contain a valid integer.
     *
     * @param table      The database table name.
     * @param column     The column to query.
     * @param playerName The name of the player.
     * @return           The value as Integer, or null if not found or cannot be parsed.
     */
    public Integer getInt(String table, String column, String playerName) {
        String value = db.fetchPlayerValue(table, column, playerName);
        try {
            return value != null ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Retrieves a Long value from the database for the specified table, column, and player.
     * Returns null if the column does not contain a valid long.
     *
     * @param table      The database table name.
     * @param column     The column to query.
     * @param playerName The name of the player.
     * @return           The value as Long or null if not found or cannot be parsed.
     */
    public Long getLong(String table, String column, String playerName) {
        String value = db.fetchPlayerValue(table, column, playerName);
        try {
            return value != null ? Long.parseLong(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Retrieves a Double value from the database for the specified table, column, and player.
     * Returns null if the column does not contain a valid double.
     *
     * @param table      The database table name.
     * @param column     The column to query.
     * @param playerName The name of the player.
     * @return           The value as Double, or null if not found or cannot be parsed.
     */
    public Double getDouble(String table, String column, String playerName) {
        String value = db.fetchPlayerValue(table, column, playerName);
        try {
            return value != null ? Double.parseDouble(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Retrieves a Date value from the database for the specified table, column, and player.
     * The date string in the database must match the provided date format.
     *
     * @param table      The database table name.
     * @param column     The column to query.
     * @param playerName The name of the player.
     * @param format     The date format pattern, e.g. "yyyy-MM-dd HH:mm:ss"
     * @return           The value as Date, or null if not found or cannot be parsed.
     */
    public Date getDate(String table, String column, String playerName, String format) {
        String value = db.fetchPlayerValue(table, column, playerName);
        if (value == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Retrieves a generic Object value from the database as a String.
     * You can cast or parse this value as needed.
     *
     * @param table      The database table name.
     * @param column     The column to query.
     * @param playerName The name of the player.
     * @return           The value as Object (actually String), or null if not found.
     */
    public Object getValue(String table, String column, String playerName) {
        return db.fetchPlayerValue(table, column, playerName);
    }

    /**
     * Retrieves a list of player names whose column value contains the specified substring (case-insensitive).
     *
     * @param table     The database table name.
     * @param column    The column to check (e.g. "nickname").
     * @param contains  The substring to search for.
     * @return          List of player names where the column contains the substring.
     */
    public List<String> getPlayersWhereColumnContains(String table, String column, String contains) {
        List<String> players = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = db.rawQuery(
                    "SELECT player FROM " + table + " WHERE LOWER(" + column + ") LIKE ?",
                    "%" + contains.toLowerCase() + "%"
            );
            if (rs != null) {
                while (rs.next()) {
                    players.add(rs.getString("player"));
                }
                rs.close();
            }
        } catch (Exception e) {
            NHook.inst().getLogger().severe("Error executing getPlayersWhereColumnContains: " + e.getMessage());
        }
        return players;
    }

    /**
     * Retrieves a list of player names whose column value starts with the specified prefix (case-insensitive).
     *
     * @param table     The database table name.
     * @param column    The column to check.
     * @param prefix    The prefix to match.
     * @return          List of player names where the column starts with the prefix.
     */
    public List<String> getPlayersWhereColumnStartsWith(String table, String column, String prefix) {
        List<String> players = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = db.rawQuery(
                    "SELECT player FROM " + table + " WHERE LOWER(" + column + ") LIKE ?",
                    prefix.toLowerCase() + "%"
            );
            if (rs != null) {
                while (rs.next()) {
                    players.add(rs.getString("player"));
                }
                rs.close();
            }
        } catch (Exception e) {
            NHook.inst().getLogger().severe("Error executing getPlayersWhereColumnStartsWith: " + e.getMessage());
        }
        return players;
    }

    /**
     * Retrieves a list of player names whose column value ends with the specified suffix (case-insensitive).
     *
     * @param table     The database table name.
     * @param column    The column to check.
     * @param suffix    The suffix to match.
     * @return          List of player names where the column ends with the suffix.
     */
    public List<String> getPlayersWhereColumnEndsWith(String table, String column, String suffix) {
        List<String> players = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = db.rawQuery(
                    "SELECT player FROM " + table + " WHERE LOWER(" + column + ") LIKE ?",
                    "%" + suffix.toLowerCase()
            );
            if (rs != null) {
                while (rs.next()) {
                    players.add(rs.getString("player"));
                }
                rs.close();
            }
        } catch (Exception e) {
            NHook.inst().getLogger().severe("Error executing getPlayersWhereColumnEndsWith: " + e.getMessage());
        }
        return players;
    }

    /**
     * Retrieves a list of player names whose integer column is greater than the specified value.
     *
     * @param table     The database table name.
     * @param column    The column to check (should be an integer column).
     * @param minValue  The minimum value.
     * @return          List of player names where column > minValue.
     */
    public List<String> getPlayersWithIntGreaterThan(String table, String column, int minValue) {
        List<String> players = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = db.rawQuery(
                    "SELECT player FROM " + table + " WHERE " + column + " > ?",
                    minValue
            );
            if (rs != null) {
                while (rs.next()) {
                    players.add(rs.getString("player"));
                }
                rs.close();
            }
        } catch (Exception e) {
            NHook.inst().getLogger().severe("Error executing getPlayersWithIntGreaterThan: " + e.getMessage());
        }
        return players;
    }
}