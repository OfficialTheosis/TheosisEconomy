package me.Short.TheosisEconomy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class MostRecentPlayerNamesStore
{

    private final TheosisEconomy instance;
    private final DatabaseManager databaseManager;

    private int limit;

    private final LinkedHashMap<UUID, String> mostRecentPlayerNames = new LinkedHashMap<>();

    private volatile Map<UUID, String> mostRecentPlayerNamesSnapshot = Map.of();

    private long mostRecentTimestamp;

    public MostRecentPlayerNamesStore(TheosisEconomy instance, DatabaseManager databaseManager, int limit)
    {
        this.instance = instance;
        this.databaseManager = databaseManager;
        this.limit = Math.max(0, limit);

        load();
    }

    // Add a player to the map, causing the oldest one to get removed if it exceeds the limit
    public synchronized void add(UUID uuid, String username)
    {
        // Ensure every update has a strictly increasing timestamp, even if multiple updates happen in the same millisecond
        long timestamp = Math.max(System.currentTimeMillis(), mostRecentTimestamp + 1);

        mostRecentTimestamp = timestamp;

        mostRecentPlayerNames.remove(uuid);
        mostRecentPlayerNames.put(uuid, username);

        UUID removedUuid = null;

        if (mostRecentPlayerNames.size() > limit)
        {
            removedUuid = mostRecentPlayerNames.keySet().iterator().next();
            mostRecentPlayerNames.remove(removedUuid);
        }

        updateSnapshot();

        UUID finalRemovedUuid = removedUuid;

        databaseManager.executeTask(() ->
        {
            try
            {
                upsertPlayerNameInDatabase(uuid, username, timestamp);

                if (finalRemovedUuid != null)
                {
                    deletePlayerNameFromDatabase(finalRemovedUuid);
                }
            }
            catch (SQLException e)
            {
                instance.getLogger().log(Level.WARNING, "Failed to save most recent player name.", e);
            }
        });
    }

    // Load the most recent player names from the database
    private void load()
    {
        try
        {
            databaseManager.submitTask(() ->
            {
                try
                {
                    trimDatabaseToLimit(limit);

                    String sql = """
                        SELECT uuid, username, last_seen_timestamp
                        FROM most_recent_player_names
                        ORDER BY last_seen_timestamp ASC, uuid DESC;
                        """;

                    try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql);
                         ResultSet resultSet = statement.executeQuery())
                    {
                        mostRecentPlayerNames.clear();

                        while (resultSet.next())
                        {
                            UUID uuid = UUID.fromString(resultSet.getString("uuid"));

                            mostRecentPlayerNames.put(uuid, resultSet.getString("username"));

                            mostRecentTimestamp = Math.max(mostRecentTimestamp, resultSet.getLong("last_seen_timestamp"));
                        }
                    }

                    updateSnapshot();

                    return null;
                }
                catch (SQLException e)
                {
                    throw new CompletionException(e);
                }
            }).join();
        }
        catch (CompletionException e)
        {
            instance.getLogger().log(Level.WARNING, "Failed to load most recent player names from the database.", e.getCause() != null ? e.getCause() : e);
        }
    }

    // Insert or update a player's cached username
    private void upsertPlayerNameInDatabase(UUID uuid, String username, long timestamp) throws SQLException
    {
        String sql = """
            INSERT INTO most_recent_player_names (
                uuid,
                username,
                last_seen_timestamp
            )
            VALUES (?, ?, ?)
            ON CONFLICT(uuid)
            DO UPDATE SET
                username = excluded.username,
                last_seen_timestamp = excluded.last_seen_timestamp;
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setLong(3, timestamp);

            statement.executeUpdate();
        }
    }

    // Remove an evicted player name from the database
    private void deletePlayerNameFromDatabase(UUID uuid) throws SQLException
    {
        String sql = """
            DELETE FROM most_recent_player_names
            WHERE uuid = ?;
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setString(1, uuid.toString());

            statement.executeUpdate();
        }
    }

    // Trim the database down to the configured limit
    private void trimDatabaseToLimit(int limit) throws SQLException
    {
        String sql = """
            DELETE FROM most_recent_player_names
            WHERE uuid NOT IN (
                SELECT uuid
                FROM most_recent_player_names
                ORDER BY last_seen_timestamp DESC, uuid ASC
                LIMIT ?
            );
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setInt(1, limit);

            statement.executeUpdate();
        }
    }

    // Update the snapshot of the most recent player names map
    private void updateSnapshot()
    {
        mostRecentPlayerNamesSnapshot = Map.copyOf(mostRecentPlayerNames);
    }

    // Getter for "mostRecentPlayerNamesSnapshot"
    public Map<UUID, String> getMostRecentPlayerNamesSnapshot()
    {
        return mostRecentPlayerNamesSnapshot;
    }

    public synchronized void setLimit(int limit)
    {
        limit = Math.max(0, limit);
        this.limit = limit;

        while (mostRecentPlayerNames.size() > limit)
        {
            mostRecentPlayerNames.remove(mostRecentPlayerNames.keySet().iterator().next());
        }

        updateSnapshot();

        int finalLimit = limit;
        databaseManager.executeTask(() ->
        {
            try
            {
                trimDatabaseToLimit(finalLimit);
            }
            catch (SQLException e)
            {
                instance.getLogger().log(Level.WARNING, "Failed to trim most recent player names.", e);
            }
        });
    }

}