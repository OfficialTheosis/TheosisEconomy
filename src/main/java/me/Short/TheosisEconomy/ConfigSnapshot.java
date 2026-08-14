package me.Short.TheosisEconomy;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigSnapshot
{

    private final Map<String, Object> values;
    private final Set<String> sections;
    private final String prefix;

    private ConfigSnapshot(Map<String, Object> values, Set<String> sections, String prefix)
    {
        this.values = values;
        this.sections = sections;
        this.prefix = prefix;
    }

    public static ConfigSnapshot create(ConfigurationSection config)
    {
        Map<String, Object> values = new HashMap<>();
        Set<String> sections = new HashSet<>();

        for (String path : config.getKeys(true))
        {
            Object value = config.get(path);

            if (value instanceof ConfigurationSection)
            {
                sections.add(path);
                continue;
            }

            values.put(path, immutableCopy(value));
        }

        return new ConfigSnapshot(Collections.unmodifiableMap(values), Collections.unmodifiableSet(sections), "");
    }

    private static Object immutableCopy(Object value)
    {
        if (value == null)
        {
            return null;
        }

        // Normal YAML scalar values are already immutable
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>)
        {
            return value;
        }

        if (value instanceof List<?> list)
        {
            List<Object> copy = new ArrayList<>(list.size());

            for (Object element : list)
            {
                copy.add(immutableCopy(element));
            }

            return Collections.unmodifiableList(copy);
        }

        if (value instanceof Map<?, ?> map)
        {
            Map<Object, Object> copy = new HashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                copy.put(immutableCopy(entry.getKey()), immutableCopy(entry.getValue()));
            }

            return Collections.unmodifiableMap(copy);
        }

        throw new IllegalArgumentException("Unsupported config value type: " + value.getClass().getName());
    }

    private String resolve(String path)
    {
        if (prefix.isEmpty())
        {
            return path;
        }

        if (path.isEmpty())
        {
            return prefix;
        }

        return prefix + "." + path;
    }

    public boolean contains(String path)
    {
        String resolvedPath = resolve(path);

        return values.containsKey(resolvedPath) || sections.contains(resolvedPath);
    }

    public Object get(String path)
    {
        String resolvedPath = resolve(path);

        Object value = values.get(resolvedPath);

        if (value != null || values.containsKey(resolvedPath))
        {
            return value;
        }

        if (sections.contains(resolvedPath))
        {
            return new ConfigSnapshot(values, sections, resolvedPath);
        }

        return null;
    }

    public ConfigSnapshot getConfigurationSection(String path)
    {
        String resolvedPath = resolve(path);

        if (!sections.contains(resolvedPath))
        {
            return null;
        }

        return new ConfigSnapshot(values, sections, resolvedPath);
    }

    public Set<String> getKeys(boolean deep)
    {
        Set<String> keys = new HashSet<>();

        String base = prefix.isEmpty() ? "" : prefix + ".";

        for (String path : sections)
        {
            addRelativeKey(keys, path, base, deep);
        }

        for (String path : values.keySet())
        {
            addRelativeKey(keys, path, base, deep);
        }

        return Collections.unmodifiableSet(keys);
    }

    private static void addRelativeKey(Set<String> keys, String path, String base, boolean deep)
    {
        if (!path.startsWith(base))
        {
            return;
        }

        String relativePath = path.substring(base.length());

        if (relativePath.isEmpty())
        {
            return;
        }

        if (deep)
        {
            keys.add(relativePath);
            return;
        }

        int separatorIndex = relativePath.indexOf('.');

        if (separatorIndex == -1)
        {
            keys.add(relativePath);
        }
        else
        {
            keys.add(relativePath.substring(0, separatorIndex));
        }
    }

    public String getString(String path)
    {
        Object value = values.get(resolve(path));

        return value != null ? value.toString() : null;
    }

    public String getString(String path, String defaultValue)
    {
        Object value = values.get(resolve(path));

        return value != null ? value.toString() : defaultValue;
    }

    public int getInt(String path)
    {
        return getInt(path, 0);
    }

    public int getInt(String path, int defaultValue)
    {
        Object value = values.get(resolve(path));

        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    public long getLong(String path)
    {
        return getLong(path, 0L);
    }

    public long getLong(String path, long defaultValue)
    {
        Object value = values.get(resolve(path));

        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    public double getDouble(String path)
    {
        return getDouble(path, 0.0);
    }

    public double getDouble(String path, double defaultValue)
    {
        Object value = values.get(resolve(path));

        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    public boolean getBoolean(String path)
    {
        return getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean defaultValue)
    {
        Object value = values.get(resolve(path));

        return value instanceof Boolean bool ? bool : defaultValue;
    }

    public List<String> getStringList(String path)
    {
        Object value = values.get(resolve(path));

        if (!(value instanceof List<?> list))
        {
            return List.of();
        }

        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    public List<Integer> getIntegerList(String path)
    {
        Object value = values.get(resolve(path));

        if (!(value instanceof List<?> list))
        {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();

        for (Object element : list)
        {
            if (element instanceof Integer integer)
            {
                result.add(integer);
            }
            else if (element instanceof Number number)
            {
                result.add(number.intValue());
            }
        }

        return List.copyOf(result);
    }

    public List<Long> getLongList(String path)
    {
        Object value = values.get(resolve(path));

        if (!(value instanceof List<?> list))
        {
            return List.of();
        }

        List<Long> result = new ArrayList<>();

        for (Object element : list)
        {
            if (element instanceof Long longValue)
            {
                result.add(longValue);
            }
            else if (element instanceof Number number)
            {
                result.add(number.longValue());
            }
        }

        return List.copyOf(result);
    }

}