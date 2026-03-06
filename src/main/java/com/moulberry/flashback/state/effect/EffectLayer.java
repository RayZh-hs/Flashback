package com.moulberry.flashback.state.effect;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import imgui.moulberry90.type.ImString;

/**
 * Base class for effect layers. Effect layers are slots that apply a stack
 * of effects to objects in the world, similar to adjustment layers in After Effects.
 *
 * Different subclasses target different types of objects (blocks, entities, etc.).
 */
public abstract class EffectLayer {

    public String name;
    public boolean enabled = true;
    public int customColour = 0;

    public transient ImString nameEditField = null;
    public transient boolean forceFocusTrack = false;

    public EffectLayer(String name) {
        this.name = name;
    }

    /**
     * Returns the type identifier for serialization.
     */
    public abstract String typeId();

    /**
     * Returns the display name for the UI (i.e. the layer type label).
     */
    public abstract String typeDisplayName();

    /**
     * Creates a deep copy of this effect layer.
     */
    public abstract EffectLayer copy();

    /**
     * Gson type adapter for polymorphic serialization of EffectLayer.
     */
    public static class TypeAdapter implements JsonSerializer<EffectLayer>, JsonDeserializer<EffectLayer> {
        @Override
        public EffectLayer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String type = jsonObject.get("layer_type").getAsString();
            EffectLayer layer = switch (type) {
                case "block" -> context.deserialize(json, BlockEffectLayer.class);
                default -> throw new IllegalStateException("Unknown effect layer type: " + type);
            };
            if (jsonObject.has("name")) {
                layer.name = jsonObject.get("name").getAsString();
            }
            if (jsonObject.has("enabled")) {
                layer.enabled = jsonObject.get("enabled").getAsBoolean();
            }
            if (jsonObject.has("custom_colour")) {
                layer.customColour = jsonObject.get("custom_colour").getAsInt();
            }
            return layer;
        }

        @Override
        public JsonElement serialize(EffectLayer src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject;
            switch (src) {
                case BlockEffectLayer blockEffectLayer -> {
                    jsonObject = (JsonObject) context.serialize(blockEffectLayer, BlockEffectLayer.class);
                }
                default -> throw new IllegalStateException("Unknown effect layer type: " + src.getClass());
            }
            jsonObject.addProperty("layer_type", src.typeId());
            jsonObject.addProperty("name", src.name);
            jsonObject.addProperty("enabled", src.enabled);
            if (src.customColour != 0) {
                jsonObject.addProperty("custom_colour", src.customColour);
            }
            return jsonObject;
        }
    }
}
