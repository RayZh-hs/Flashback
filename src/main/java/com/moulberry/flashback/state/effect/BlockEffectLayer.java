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
    private boolean includeAir = false;
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

    public boolean isIncludeAir() {
        return this.includeAir;
    }

    public void setIncludeAir(boolean includeAir) {
        if (this.includeAir != includeAir) {
            this.includeAir = includeAir;
            this.cachedSelection = null;
            this.cachedSelectionText = null;
        }
    }

    private String getEffectiveSelectionText() {
        if (this.selectionText == null || this.selectionText.isBlank()) {
            return "";
        }

        if (this.includeAir) {
            return this.selectionText;
        }

        return "(" + this.selectionText + ")[!air]";
    }

    /**
     * Get the parsed selection. Cached until the text changes.
     */
    public BlockSelection getSelection() {
        String effectiveSelectionText = this.getEffectiveSelectionText();
        if (this.cachedSelection == null || !effectiveSelectionText.equals(this.cachedSelectionText)) {
            this.cachedSelection = BlockSelection.parse(effectiveSelectionText);
            this.cachedSelectionText = effectiveSelectionText;
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
        copy.includeAir = this.includeAir;
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
            if (jsonObject.has("include_air")) {
                layer.includeAir = jsonObject.get("include_air").getAsBoolean();
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
            jsonObject.addProperty("include_air", src.includeAir);

            JsonArray effectsArray = new JsonArray();
            for (BlockEffect effect : src.effects) {
                effectsArray.add(context.serialize(effect, BlockEffect.class));
            }
            jsonObject.add("effects", effectsArray);

            return jsonObject;
        }
    }
}
