package com.moulberry.flashback.state.effect;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

/**
 * Manages the application and reversal of block effect layers on the server world.
 *
 * Effect layers with Replace effects modify blocks in-place on the server level.
 * The manager tracks original block states so they can be restored when effects
 * change or are removed.
 */
public class BlockEffectManager {

    /**
     * Map of block positions to their original (pre-effect) block state.
     * Used to restore blocks when effects are undone or changed.
     */
    private final Map<BlockPos, BlockState> originalStates = new HashMap<>();

    /**
     * The last set of effect layers that was applied, used to detect changes.
     */
    private int lastAppliedModCount = -1;

    /**
     * Apply all active block effect layers from the current editor scene to the world.
     * This restores any previously modified blocks first, then applies current effects.
     *
     * @param level       The server level to modify
     * @param editorState The editor state containing the scenes and effect layers
     */
    public void applyEffects(ServerLevel level, EditorState editorState) {
        int currentModCount = editorState.modCount;
        if (currentModCount == lastAppliedModCount) {
            return; // Nothing changed
        }
        lastAppliedModCount = currentModCount;

        // First, restore all previously modified blocks
        restoreAll(level);

        // Collect all active block effect layers from the current scene
        long stamp = editorState.acquireRead();
        List<EffectLayer> effectLayers;
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            effectLayers = new ArrayList<>(scene.effectLayers);
        } finally {
            editorState.release(stamp);
        }

        // Apply effects from bottom to top (list order)
        for (EffectLayer layer : effectLayers) {
            if (!layer.enabled) continue;

            if (layer instanceof BlockEffectLayer blockEffectLayer) {
                applyBlockEffectLayer(level, blockEffectLayer);
            }
        }
    }

    /**
     * Apply a single block effect layer to the world.
     */
    private void applyBlockEffectLayer(ServerLevel level, BlockEffectLayer blockEffectLayer) {
        BlockSelection selection = blockEffectLayer.getSelection();
        if (selection.isEmpty()) return;

        // Apply each enabled effect in the stack (bottom to top = list order)
        for (BlockEffect effect : blockEffectLayer.effects) {
            if (!effect.enabled) continue;

            if (effect instanceof ReplaceEffect replaceEffect) {
                applyReplaceEffect(level, selection, replaceEffect);
            }
        }
    }

    /**
     * Apply a Replace effect: replace all blocks in the selection with the specified block.
     */
    private void applyReplaceEffect(ServerLevel level, BlockSelection selection, ReplaceEffect replaceEffect) {
        BlockState replacementState = replaceEffect.getBlockState();

        int minY = level.getMinSectionY() << 4;
        int maxY = (level.getMaxSectionY() << 4) + 16;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Object holder : getChunkHolders(level)) {
            LevelChunk chunk = resolveChunkFromHolder(holder);
            if (chunk == null) {
                continue;
            }

            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();

            for (int x = minX; x < minX + 16; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < minZ + 16; z++) {
                        if (!selection.matches(level, x, y, z)) {
                            continue;
                        }

                        mutable.set(x, y, z);
                        BlockState currentState = level.getBlockState(mutable);
                        if (!replaceEffect.matchesFilter(currentState)) {
                            continue;
                        }

                        applyReplacementAt(level, mutable, currentState, replacementState);
                    }
                }
            }
        }
    }

    private void applyReplacementAt(ServerLevel level, BlockPos pos, BlockState currentState, BlockState replacementState) {
        BlockPos immutablePos = pos.immutable();

        // Only save original if we haven't already saved it
        if (!originalStates.containsKey(immutablePos)) {
            originalStates.put(immutablePos, currentState);
        }

        // Set the block without triggering updates (flag 2 = send to clients, no block update)
        level.setBlock(immutablePos, replacementState, 2);
    }

    private static Iterable<?> getChunkHolders(ServerLevel level) {
        Object chunkMap = level.getChunkSource().chunkMap;

        try {
            Method method = chunkMap.getClass().getMethod("getChunks");
            Object value = method.invoke(chunkMap);
            if (value instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Exception ignored) {
        }

        for (String fieldName : new String[] {"visibleChunkMap", "updatingChunkMap"}) {
            try {
                Field field = chunkMap.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(chunkMap);

                if (value instanceof Iterable<?> iterable) {
                    return iterable;
                }
                if (value instanceof Map<?, ?> map) {
                    return map.values();
                }

                Method valuesMethod = value.getClass().getMethod("values");
                Object values = valuesMethod.invoke(value);
                if (values instanceof Iterable<?> iterable) {
                    return iterable;
                }
            } catch (Exception ignored) {
            }
        }

        return Collections.emptyList();
    }

    private static LevelChunk resolveChunkFromHolder(Object holderOrChunk) {
        if (holderOrChunk instanceof LevelChunk levelChunk) {
            return levelChunk;
        }

        if (holderOrChunk instanceof Entry<?, ?> entry && entry.getValue() != null) {
            return resolveChunkFromHolder(entry.getValue());
        }

        for (String methodName : new String[] {"getTickingChunk", "getFullChunkNow", "getChunkToSend"}) {
            try {
                Method method = holderOrChunk.getClass().getMethod(methodName);
                Object value = method.invoke(holderOrChunk);
                if (value instanceof LevelChunk levelChunk) {
                    return levelChunk;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    /**
     * Restore all blocks that were modified by effect layers back to their original state.
     */
    public void restoreAll(ServerLevel level) {
        for (Map.Entry<BlockPos, BlockState> entry : originalStates.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
        originalStates.clear();
    }

    /**
     * Force a full re-application of effects on the next call.
     */
    public void invalidate() {
        this.lastAppliedModCount = -1;
    }

    /**
     * Check if there are any active modifications.
     */
    public boolean hasModifications() {
        return !originalStates.isEmpty();
    }
}
