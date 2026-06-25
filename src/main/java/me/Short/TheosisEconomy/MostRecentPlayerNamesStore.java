package me.Short.TheosisEconomy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class MostRecentPlayerNamesStore
{

    private final LinkedHashMap<UUID, String> mostRecentPlayerNamesMap = new LinkedHashMap<>();

    private volatile LinkedHashMap<UUID, String> latestSnapshot = new LinkedHashMap<>();

    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();

    private final AtomicBoolean saving = new AtomicBoolean(false);
    private final AtomicBoolean saveAgain = new AtomicBoolean(false);

    private final TheosisEconomy instance;
    private final Path file;
    private final int limit;

    private final Gson gson;

    private volatile Set<String> mostRecentPlayerNamesSet = Set.of();

    public MostRecentPlayerNamesStore(TheosisEconomy instance, Path file, int limit)
    {
        this.instance = instance;
        this.file = file;
        this.limit = limit;

        this.gson = instance.getGson();

        load();
    }

    // Method to add a player to the map, causing the oldest ones to get removed if it exceeds the limit
    public void add(UUID uuid, String value)
    {
        mostRecentPlayerNamesMap.remove(uuid);
        mostRecentPlayerNamesMap.put(uuid, value);

        trimToLimit();
    }

    // Method to get a snapshot of the map
    private LinkedHashMap<UUID, String> snapshot()
    {
        return new LinkedHashMap<>(mostRecentPlayerNamesMap);
    }

    // Method to load the map from the JSON file
    private void load()
    {
        if (Files.notExists(file))
        {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            LinkedHashMap<UUID, String> loaded = gson.fromJson(reader, new TypeToken<LinkedHashMap<UUID, String>>() {}.getType());

            if (loaded != null)
            {
                mostRecentPlayerNamesMap.clear();
                mostRecentPlayerNamesMap.putAll(loaded);

                trimToLimit();
            }
        }
        catch (IOException e)
        {
            instance.getLogger().log(Level.WARNING, "Failed to load most recent player names.", e);
        }
    }

    // Method to request the map to be saved to the JSON file
    private void requestSave()
    {
        latestSnapshot = snapshot();

        saveAgain.set(true);

        if (!saving.compareAndSet(false, true))
        {
            return;
        }

        saveExecutor.submit(() ->
        {
            try
            {
                do
                {
                    saveAgain.set(false);

                    saveSnapshot(latestSnapshot);
                }
                while (saveAgain.get());
            }
            finally
            {
                saving.set(false);

                if (saveAgain.get())
                {
                    requestSave();
                }
            }
        });
    }

    // Method to save a snapshot of the map to the JSON file
    private void saveSnapshot(LinkedHashMap<UUID, String> snapshot)
    {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");

        try
        {
            // Write the snapshot to a temporary file
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
            {
                gson.toJson(snapshot, writer);
            }

            // Atomically move the temporary file to the real JSON file
            try
            {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e)
        {
            instance.getLogger().log(Level.WARNING, "Failed to save most recent player names.", e);
        }
    }

    // Method to trim the map down to its limit
    private void trimToLimit()
    {
        while (mostRecentPlayerNamesMap.size() > limit)
        {
            mostRecentPlayerNamesMap.remove(mostRecentPlayerNamesMap.keySet().iterator().next());
        }

        updateSet();

        requestSave();
    }

    // Method to update the set of player names with the values from the map
    private void updateSet()
    {
        mostRecentPlayerNamesSet = Set.copyOf(mostRecentPlayerNamesMap.values());
    }

    // Method to shut down the executor service and save the map to the JSON file
    public void shutdownAndSave()
    {
        requestSave();

        saveExecutor.shutdown();

        try
        {
            if (!saveExecutor.awaitTermination(30, TimeUnit.SECONDS))
            {
                saveExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Getter for "mostRecentPlayerNamesSet"
    public Set<String> getMostRecentPlayerNamesSet()
    {
        return mostRecentPlayerNamesSet;
    }

}