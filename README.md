# NHook - Advanced Player Data Management

NHook is a modern, flexible, and developer-friendly Minecraft plugin for advanced player data management using MySQL.

**Main Purpose:**  
NHook enables seamless synchronization between your website and your Minecraft server.  
It allows you to display any data stored on your website—such as player credit, registration date, or custom profile information—directly in-game.  
With NHook, you can easily show web-based stats, player history, and other database-driven details to players while they play, bridging the gap between web and server worlds.

[![discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_vector.svg)](https://discord.gg/qcW6YrxwqJ)

---

## ✨ Features

- 🛡️ **Seamless MySQL Integration** – Instantly connect your server to your MySQL database.
- ⚡ **High Performance** – Optimized queries and lightweight design for minimal server impact.
- 🏷️ **Type-Safe API** – Fetch player data as `String`, `Integer`, `Long`, `Double`, `Date`, and more.
- 🔎 **Advanced Search & Filtering** – Find players by nickname, stats, or any custom criteria with powerful query helpers.
- 📊 **Leaderboard & Ranking Support** – Effortlessly build leaderboards and ranking systems using your data.
- 🔄 **Real-Time Data Access** – Instantly fetch and update player information in-game.
- 🧩 **Extensible & Modular** – Clean, well-documented codebase makes it easy to extend, integrate, and customize.
- ♻️ **Automatic Connection Management** – Handles reconnects and safely manages database sessions.
- 📨 **Plugin-to-Plugin API** – Easily access player data from other plugins.
- 📝 **Comprehensive Logging** – Detailed error and info logging for easy debugging.

---

## 🛠️ Quick Start

1. **Download & Install**
    - Place the latest NHook `.jar` in your server's `plugins/` folder.
    - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) required.

2. **Configure MySQL**
    - Set your database details in `config.yml`:
    ```yaml
    database:
      host: localhost
      port: 3306
      db: nesoi
      user: root
      password: ""
    ```

3. **Use the API in Your Plugin**
    ```java
    DatabaseManager db = new DatabaseManager();
    db.connect();

    NHookAPI api = new NHookAPI(db);

    // Get player's credit from website database
    int credit = api.getInt("players", "credit", "PlayerName");

    // Find all players whose nickname contains "cat"
    List<String> catPlayers = api.getPlayersWhereColumnContains("players", "nickname", "cat");

    db.disconnect(); // Disconnect when your plugin is disabled
    ```

---

## 📚 API Overview

| Method                                         | Description                                       |
|------------------------------------------------|---------------------------------------------------|
| `getString(table, column, player)`             | Get a value as `String`                           |
| `getInt(table, column, player)`                | Get a value as `Integer`                          |
| `getLong(table, column, player)`               | Get a value as `Long`                             |
| `getDouble(table, column, player)`             | Get a value as `Double`                           |
| `getDate(table, column, player, format)`       | Get a value as `Date` (with date format)          |
| `getPlayersWhereColumnContains(...)`           | Find players where column contains a string       |
| `getPlayersWhereColumnStartsWith(...)`         | Find players where column starts with string      |
| `getPlayersWhereColumnEndsWith(...)`           | Find players where column ends with string        |
| `getPlayersWithIntGreaterThan(...)`            | Find players where int column > value             |

---

## 💡 Example: Top-Rich Players

```java
List<String> richPlayers = api.getPlayersWithIntGreaterThan("players", "credit", 100000);
for (String player : richPlayers) {
    System.out.println("Rich: " + player);
}
```

---

## 📄 License

This project is licensed under the NESOI Plugin License v1.0.

---

## Picture

![hologram](https://github.com/user-attachments/assets/56ea88f5-e169-4a7a-bdad-66c03b32ef16)
