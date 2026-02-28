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
    private transient BlockState cachedBlockState = null;
    public transient ImString blockIdEditField = null;

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
        copy.enabled = this.enabled;
        return copy;
    }

    public static class TypeAdapter implements JsonSerializer<ReplaceEffect>, JsonDeserializer<ReplaceEffect> {
        @Override
        public ReplaceEffect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String blockId = jsonObject.has("block_id") ? jsonObject.get("block_id").getAsString() : "minecraft:air";
            return new ReplaceEffect(blockId);
        }

        @Override
        public JsonElement serialize(ReplaceEffect src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("block_id", src.blockId);
            return jsonObject;
        }
    }
}
