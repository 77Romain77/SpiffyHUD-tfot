package de.keksuccino.spiffyhud.util.level;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Utility class for getting NBT data from entities as strings.
 */
public class EntityNbtUtils {

    private static final int MAX_CACHED_PATHS = 512;

    /**
     * Entity keys are weak so changing worlds cannot retain old entities.
     * Each snapshot and resolved value is valid for one entity tick.
     */
    private static final Map<Entity, EntityCache> ENTITY_CACHE = new WeakHashMap<>();

    /**
     * Parsed NBT paths are immutable and can be reused for every entity/tick.
     */
    private static final Map<String, NbtPathArgument.NbtPath> PATH_CACHE = new HashMap<>();
    private static final Set<String> INVALID_PATH_CACHE = new HashSet<>();

    private EntityNbtUtils() {
    }

    /**
     * Gets NBT data from an entity as a string using the specified path.
     * For numeric values, returns just the number without type suffix.
     *
     * @param entity The entity to get data from
     * @param path NBT path (like 'Health' or 'Inventory[0].id')
     * @return The data at the specified path as a string, or null if not found
     */
    @Nullable
    public static String getNbtString(@NotNull Entity entity, @NotNull String path) {
        EntityCache entityCache = getEntityCache(entity);

        if (entityCache.resolvedValues.containsKey(path)) {
            return entityCache.resolvedValues.get(path);
        }

        NbtPathArgument.NbtPath nbtPath = getParsedPath(path);
        if (nbtPath == null) {
            entityCache.resolvedValues.put(path, null);
            return null;
        }

        String result = null;
        try {
            List<Tag> results = nbtPath.get(entityCache.entityData);
            if (!results.isEmpty()) {
                result = tagToString(results.get(0));
            }
        } catch (CommandSyntaxException ignored) {
        }

        entityCache.resolvedValues.put(path, result);
        return result;
    }

    /**
     * Gets all possible NBT paths in an entity.
     *
     * @param entity The entity to get paths from
     * @return A list of all NBT paths in the entity
     */
    @NotNull
    public static List<String> getAllNbtPaths(@NotNull Entity entity) {
        CompoundTag entityData = getEntityCache(entity).entityData;

        List<String> paths = new ArrayList<>();
        collectPaths("", entityData, paths);

        Collections.sort(paths);
        return paths;
    }

    @NotNull
    private static EntityCache getEntityCache(@NotNull Entity entity) {
        EntityCache cache = ENTITY_CACHE.computeIfAbsent(entity, ignored -> new EntityCache());
        if (cache.entityTick != entity.tickCount) {
            cache.refresh(entity);
        }
        return cache;
    }

    @Nullable
    private static NbtPathArgument.NbtPath getParsedPath(@NotNull String path) {
        NbtPathArgument.NbtPath cached = PATH_CACHE.get(path);
        if (cached != null) return cached;
        if (INVALID_PATH_CACHE.contains(path)) return null;

        if (PATH_CACHE.size() + INVALID_PATH_CACHE.size() >= MAX_CACHED_PATHS) {
            PATH_CACHE.clear();
            INVALID_PATH_CACHE.clear();
        }

        try {
            NbtPathArgument.NbtPath parsed = NbtPathArgument.nbtPath().parse(new StringReader(path));
            PATH_CACHE.put(path, parsed);
            return parsed;
        } catch (CommandSyntaxException ignored) {
            INVALID_PATH_CACHE.add(path);
            return null;
        }
    }

    @NotNull
    private static String tagToString(@NotNull Tag tag) {
        String value = tag.getAsString();

        if (tag instanceof NumericTag numericTag) {
            if (value.endsWith("d")
                    || value.endsWith("f")
                    || value.endsWith("b")
                    || value.endsWith("s")
                    || value.endsWith("L")) {
                if (value.contains(".")) {
                    return String.valueOf(numericTag.getAsDouble());
                }
                return String.valueOf(numericTag.getAsLong());
            }
        }

        return value;
    }

    /**
     * Recursively collects all paths in an NBT tag.
     */
    private static void collectPaths(String prefix, Tag tag, List<String> paths) {
        if (prefix != null && !prefix.isEmpty()) {
            paths.add(prefix);
        }

        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                String newPrefix = prefix.isEmpty() ? key : prefix + "." + key;
                collectPaths(newPrefix, compound.get(key), paths);
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                String newPrefix = prefix + "[" + i + "]";
                collectPaths(newPrefix, list.get(i), paths);
            }
        } else if (tag instanceof ByteArrayTag
                || tag instanceof IntArrayTag
                || tag instanceof LongArrayTag) {
            int size;
            if (tag instanceof ByteArrayTag byteArrayTag) {
                size = byteArrayTag.getAsByteArray().length;
            } else if (tag instanceof IntArrayTag intArrayTag) {
                size = intArrayTag.getAsIntArray().length;
            } else {
                size = ((LongArrayTag) tag).getAsLongArray().length;
            }

            for (int i = 0; i < size; i++) {
                paths.add(prefix + "[" + i + "]");
            }
        }
    }

    private static final class EntityCache {
        private int entityTick = Integer.MIN_VALUE;
        private CompoundTag entityData = new CompoundTag();
        private final Map<String, String> resolvedValues = new HashMap<>();

        private void refresh(@NotNull Entity entity) {
            CompoundTag freshData = new CompoundTag();
            entity.saveWithoutId(freshData);

            this.entityData = freshData;
            this.entityTick = entity.tickCount;
            this.resolvedValues.clear();
        }
    }
}
