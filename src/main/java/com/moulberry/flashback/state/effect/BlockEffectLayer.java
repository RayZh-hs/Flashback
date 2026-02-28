package com.moulberry.flashback.state.effect;

import com.google.gson.*;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * An effect layer that applies effects to selected blocks in the world.
 *
 * The selection is defined by a text field using the BlockSelection syntax.
 * Effects are stacked and applied in order (bottom to top in the stack,
 * bottom to top across layers in the timeline).
 */
public class BlockEffectLayer extends EffectLayer {

    private String selectionText = "";
    private transient BlockSelection cachedSelection = null;
    private transient String cachedSelectionText = null;

    public final List<BlockEffect> effects = new ArrayList<>();

    public transient ImString selectionEditField = null;

    public BlockEffectLayer(String name) {
        super(name);
    }

    public String getSelectionText() {
        return this.selectionText;
    }

    public void setSelectionText(String selectionText) {
        this.selectionText = selectionText;
        this.cachedSelection = null;
        this.cachedSelectionText = null;
    }

    /**
     * Get the parsed selection. Cached until the text changes.
     */
    public BlockSelection getSelection() {
        if (this.cachedSelection == null || !this.selectionText.equals(this.cachedSelectionText)) {
            this.cachedSelection = BlockSelection.parse(this.selectionText);
            this.cachedSelectionText = this.selectionText;
        }
        return this.cachedSelection;
    }

    @Override
    public String typeId() {
        return "block";
    }

    @Override
    public String typeDisplayName() {
        return I18n.get("flashback.effect_layer.block");
    }

    @Override
    public EffectLayer copy() {
        BlockEffectLayer copy = new BlockEffectLayer(this.name);
        copy.enabled = this.enabled;
        copy.selectionText = this.selectionText;
        for (BlockEffect effect : this.effects) {
            copy.effects.add(effect.copy());
        }
        return copy;
    }

    public static class TypeAdapter implements JsonSerializer<BlockEffectLayer>, JsonDeserializer<BlockEffectLayer> {
        @Override
        public BlockEffectLayer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "Block Effect Layer";
            BlockEffectLayer layer = new BlockEffectLayer(name);

            if (jsonObject.has("selection")) {
                layer.selectionText = jsonObject.get("selection").getAsString();
            }

            if (jsonObject.has("effects")) {
                JsonArray effectsArray = jsonObject.getAsJsonArray("effects");
                for (JsonElement element : effectsArray) {
                    BlockEffect effect = context.deserialize(element, BlockEffect.class);
                    layer.effects.add(effect);
                }
            }

            return layer;
        }

        @Override
        public JsonElement serialize(BlockEffectLayer src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("selection", src.selectionText);

            JsonArray effectsArray = new JsonArray();
            for (BlockEffect effect : src.effects) {
                effectsArray.add(context.serialize(effect, BlockEffect.class));
            }
            jsonObject.add("effects", effectsArray);

            return jsonObject;
        }
    }
}
