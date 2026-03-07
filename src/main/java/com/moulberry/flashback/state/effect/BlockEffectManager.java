package com.moulberry.flashback.state.effect;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BlockEffectManager {

    private static final int OPACITY_KEY_SCALE = 1_000_000;

    public record OpacityBucket(float opacity, LongList positions) {
    }

    public static class OpacityRenderPlan {
        public static final OpacityRenderPlan EMPTY = new OpacityRenderPlan(new Long2ObjectOpenHashMap<>(), LongLists.emptyList(), List.of());

        private final Long2ObjectMap<BlockState> blockStatesByPosition;
        private final LongList allPositions;
        private final List<OpacityBucket> buckets;

        private OpacityRenderPlan(Long2ObjectMap<BlockState> blockStatesByPosition, LongList allPositions, List<OpacityBucket> buckets) {
            this.blockStatesByPosition = blockStatesByPosition;
            this.allPositions = allPositions;
            this.buckets = buckets;
        }

        public boolean isEmpty() {
            return this.allPositions.isEmpty();
        }

        public LongList allPositions() {
            return this.allPositions;
        }

        public List<OpacityBucket> buckets() {
            return this.buckets;
        }

        public BlockState getBlockState(long position) {
            return this.blockStatesByPosition.get(position);
        }
    }

    private static class LevelState {
        private final Long2ObjectMap<BlockState> originalStates = new Long2ObjectOpenHashMap<>();
        private OpacityRenderPlan opacityRenderPlan = OpacityRenderPlan.EMPTY;
    }

    private final Map<ResourceKey<Level>, LevelState> levelStates = new HashMap<>();
    private final Set<ResourceKey<Level>> appliedLevelsForFrame = new HashSet<>();
    private int lastAppliedModCount = -1;
    private int lastAppliedReplayTick = Integer.MIN_VALUE;

    public void applyEffects(ServerLevel level, EditorState editorState, int replayTick) {
        int currentModCount = editorState.modCount;
        if (currentModCount != this.lastAppliedModCount || replayTick != this.lastAppliedReplayTick) {
            this.lastAppliedModCount = currentModCount;
            this.lastAppliedReplayTick = replayTick;
            this.appliedLevelsForFrame.clear();
        }

        if (!this.appliedLevelsForFrame.add(level.dimension())) {
            return;
        }

        LevelState levelState = this.levelStates.computeIfAbsent(level.dimension(), unused -> new LevelState());
        this.restoreAll(level);

        long stamp = editorState.acquireRead();
        List<EffectLayer> effectLayers;
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            effectLayers = new ArrayList<>(scene.effectLayers);
        } finally {
            editorState.release(stamp);
        }

        Long2FloatOpenHashMap opacityByPosition = new Long2FloatOpenHashMap();
        opacityByPosition.defaultReturnValue(1.0f);

        for (EffectLayer layer : effectLayers) {
            if (!layer.enabled) {
                continue;
            }
            if (layer instanceof BlockEffectLayer blockEffectLayer) {
                this.applyBlockEffectLayer(level, levelState, blockEffectLayer, opacityByPosition);
            }
        }

        this.buildOpacityRenderPlan(level, levelState, opacityByPosition);
    }

    public OpacityRenderPlan getOpacityRenderPlan(ServerLevel level) {
        LevelState levelState = this.levelStates.get(level.dimension());
        if (levelState == null) {
            return OpacityRenderPlan.EMPTY;
        }
        return levelState.opacityRenderPlan;
    }

    private void applyBlockEffectLayer(ServerLevel level, LevelState levelState, BlockEffectLayer blockEffectLayer, Long2FloatOpenHashMap opacityByPosition) {
        BlockSelection selection = blockEffectLayer.getSelection();
        if (selection.isEmpty()) {
            return;
        }

        for (BlockEffect effect : blockEffectLayer.effects) {
            if (!effect.enabled) {
                continue;
            }

            if (effect instanceof ReplaceEffect replaceEffect) {
                this.applyReplaceEffect(level, levelState, selection, replaceEffect);
            } else if (effect instanceof OpacityEffect opacityEffect) {
                this.accumulateOpacityEffect(level, selection, opacityEffect, opacityByPosition);
            }
        }
    }

    private void applyReplaceEffect(ServerLevel level, LevelState levelState, BlockSelection selection, ReplaceEffect replaceEffect) {
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

                        this.applyReplacementAt(level, levelState, mutable, currentState, replacementState);
                    }
                }
            }
        }
    }

    private void accumulateOpacityEffect(ServerLevel level, BlockSelection selection, OpacityEffect opacityEffect, Long2FloatOpenHashMap opacityByPosition) {
        float opacity = normalizeOpacity(opacityEffect.getOpacity());
        if (opacity >= 0.9999f) {
            return;
        }

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
                        if (currentState.isAir()) {
                            continue;
                        }

                        long position = BlockPos.asLong(x, y, z);
                        float currentOpacity = opacityByPosition.containsKey(position) ? opacityByPosition.get(position) : 1.0f;
                        opacityByPosition.put(position, normalizeOpacity(currentOpacity * opacity));
                    }
                }
            }
        }
    }

    private void buildOpacityRenderPlan(ServerLevel level, LevelState levelState, Long2FloatOpenHashMap opacityByPosition) {
        if (opacityByPosition.isEmpty()) {
            levelState.opacityRenderPlan = OpacityRenderPlan.EMPTY;
            return;
        }

        Long2ObjectOpenHashMap<BlockState> blockStatesByPosition = new Long2ObjectOpenHashMap<>();
        LongArrayList allPositions = new LongArrayList(opacityByPosition.size());
        Int2ObjectOpenHashMap<LongArrayList> groupedPositions = new Int2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (Long2FloatMap.Entry entry : opacityByPosition.long2FloatEntrySet()) {
            float opacity = normalizeOpacity(entry.getFloatValue());
            if (opacity >= 0.9999f) {
                continue;
            }

            long position = entry.getLongKey();
            mutable.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
            BlockState currentState = level.getBlockState(mutable);
            if (currentState.isAir()) {
                continue;
            }

            blockStatesByPosition.put(position, currentState);
            allPositions.add(position);
            groupedPositions.computeIfAbsent(toOpacityKey(opacity), unused -> new LongArrayList()).add(position);
        }

        if (allPositions.isEmpty()) {
            levelState.opacityRenderPlan = OpacityRenderPlan.EMPTY;
            return;
        }

        IntArrayList sortedOpacityKeys = new IntArrayList(groupedPositions.keySet());
        sortedOpacityKeys.sort(IntComparators.NATURAL_COMPARATOR);

        List<OpacityBucket> buckets = new ArrayList<>(sortedOpacityKeys.size());
        IntIterator iterator = sortedOpacityKeys.iterator();
        while (iterator.hasNext()) {
            int opacityKey = iterator.nextInt();
            buckets.add(new OpacityBucket(fromOpacityKey(opacityKey), groupedPositions.get(opacityKey)));
        }

        levelState.opacityRenderPlan = new OpacityRenderPlan(blockStatesByPosition, allPositions, buckets);
    }

    private void applyReplacementAt(ServerLevel level, LevelState levelState, BlockPos pos, BlockState currentState, BlockState replacementState) {
        if (currentState == replacementState) {
            return;
        }

        long position = pos.asLong();
        if (!levelState.originalStates.containsKey(position)) {
            levelState.originalStates.put(position, currentState);
        }

        level.setBlock(pos, replacementState, 2);
    }

    private static float normalizeOpacity(float opacity) {
        return fromOpacityKey(toOpacityKey(Math.clamp(opacity, 0.0f, 1.0f)));
    }

    private static int toOpacityKey(float opacity) {
        return Math.round(opacity * OPACITY_KEY_SCALE);
    }

    private static float fromOpacityKey(int opacityKey) {
        return opacityKey / (float) OPACITY_KEY_SCALE;
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

    public void restoreAll(ServerLevel level) {
        LevelState levelState = this.levelStates.get(level.dimension());
        if (levelState == null) {
            return;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<BlockState> entry : levelState.originalStates.long2ObjectEntrySet()) {
            long position = entry.getLongKey();
            mutable.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
            level.setBlock(mutable, entry.getValue(), 2);
        }

        levelState.originalStates.clear();
        levelState.opacityRenderPlan = OpacityRenderPlan.EMPTY;
    }

    public void invalidate() {
        this.lastAppliedModCount = -1;
        this.lastAppliedReplayTick = Integer.MIN_VALUE;
        this.appliedLevelsForFrame.clear();
    }

    public boolean hasModifications() {
        for (LevelState levelState : this.levelStates.values()) {
            if (!levelState.originalStates.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
