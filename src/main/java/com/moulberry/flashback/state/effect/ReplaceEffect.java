package com.moulberry.flashback.state.effect;

import com.google.gson.*;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Type;

/**
 * Effect that replaces the selected blocks with a specified block type.
 */
public class ReplaceEffect extends BlockEffect {

    private String blockId;
    private String filterText = "";
    private transient BlockState cachedBlockState = null;
    private transient BlockFilter cachedFilter = null;
    public transient ImString blockIdEditField = null;
    public transient ImString filterEditField = null;

    public ReplaceEffect() {
        this("minecraft:air");
    }

    public ReplaceEffect(String blockId) {
        this.blockId = blockId;
    }

    public String getBlockId() {
        return this.blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
        this.cachedBlockState = null;
    }

    public String getFilterText() {
        return this.filterText;
    }

    public void setFilterText(String filterText) {
        this.filterText = filterText == null ? "" : filterText;
        this.cachedFilter = null;
    }

    public boolean matchesFilter(BlockState blockState) {
        if (this.filterText == null || this.filterText.isBlank()) {
            return true;
        }

        if (this.cachedFilter == null) {
            this.cachedFilter = BlockFilter.parse(this.filterText);
        }
        return this.cachedFilter.matches(blockState);
    }

    /**
     * Resolve the block ID to a BlockState. Returns air if the block ID is invalid.
     */
    public BlockState getBlockState() {
        if (this.cachedBlockState == null) {
            try {
                ResourceLocation location = ResourceLocation.parse(this.blockId);
                Block block = BuiltInRegistries.BLOCK.getValue(location);
                this.cachedBlockState = block.defaultBlockState();
            } catch (Exception e) {
                this.cachedBlockState = Blocks.AIR.defaultBlockState();
            }
        }
        return this.cachedBlockState;
    }

    @Override
    public String typeId() {
        return "replace";
    }

    @Override
    public String displayName() {
        return I18n.get("flashback.effect.replace");
    }

    @Override
    public BlockEffect copy() {
        ReplaceEffect copy = new ReplaceEffect(this.blockId);
        copy.filterText = this.filterText;
        copy.enabled = this.enabled;
        return copy;
    }

    public static class TypeAdapter implements JsonSerializer<ReplaceEffect>, JsonDeserializer<ReplaceEffect> {
        @Override
        public ReplaceEffect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String blockId = jsonObject.has("block_id") ? jsonObject.get("block_id").getAsString() : "minecraft:air";
            String filter = jsonObject.has("filter") ? jsonObject.get("filter").getAsString() : "";

            ReplaceEffect effect = new ReplaceEffect(blockId);
            effect.filterText = filter;
            return effect;
        }

        @Override
        public JsonElement serialize(ReplaceEffect src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("block_id", src.blockId);
            if (src.filterText != null && !src.filterText.isBlank()) {
                jsonObject.addProperty("filter", src.filterText);
            }
            return jsonObject;
        }
    }
}
