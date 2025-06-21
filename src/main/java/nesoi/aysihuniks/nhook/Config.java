package nesoi.aysihuniks.nhook;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

@Getter
@Setter
public class Config {

    private final NHook plugin;
    private static FileConfiguration config;

    private String host;
    private int port;
    private String db;
    private String user;
    private String password;

    private String table;
    private String column;

    public Config(NHook plugin) {
        this.plugin = plugin;
    }

    public Config load() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        setHost( config.getString("database.host") );
        setPort( config.getInt("database.port") );
        setDb( config.getString("database.db") );
        setUser( config.getString("database.user") );
        setPassword( config.getString("database.password") );

        return this;
    }


    public void save() {
        try {
            config.set("database.host", getHost());
            config.set("database.port", getPort());
            config.set("database.db", getDb());
            config.set("database.user", getUser());
            config.set("database.password", getPassword());

            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while saving config.yml");
        }
    }
}
