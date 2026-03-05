package com.moulberry.flashback.state.effect;

import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Base class for effects that can be applied to an effect layer.
 * Effects are stacked and applied in order (bottom to top).
 */
public abstract class BlockEffect {

    public boolean enabled = true;

    /**
     * Returns the type identifier for serialization.
     */
    public abstract String typeId();

    /**
     * Returns the display name for the UI.
     */
    public abstract String displayName();

    /**
     * Creates a deep copy of this effect.
     */
    public abstract BlockEffect copy();

    /**
     * Gson type adapter for polymorphic serialization of BlockEffect.
     */
    public static class TypeAdapter implements JsonSerializer<BlockEffect>, JsonDeserializer<BlockEffect> {
        @Override
        public BlockEffect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String type = jsonObject.get("effect_type").getAsString();
            BlockEffect effect = switch (type) {
                case "replace" -> context.deserialize(json, ReplaceEffect.class);
                case "opacity" -> context.deserialize(json, OpacityEffect.class);
                case "translate" -> context.deserialize(json, TranslateEffect.class);
                default -> throw new IllegalStateException("Unknown block effect type: " + type);
            };
            if (jsonObject.has("enabled")) {
                effect.enabled = jsonObject.get("enabled").getAsBoolean();
            }
            return effect;
        }

        @Override
        public JsonElement serialize(BlockEffect src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject;
            switch (src) {
                case ReplaceEffect replaceEffect -> {
                    jsonObject = (JsonObject) context.serialize(replaceEffect, ReplaceEffect.class);
                }
                case OpacityEffect opacityEffect -> {
                    jsonObject = (JsonObject) context.serialize(opacityEffect, OpacityEffect.class);
                }
                case TranslateEffect translateEffect -> {
                    jsonObject = (JsonObject) context.serialize(translateEffect, TranslateEffect.class);
                }
                default -> throw new IllegalStateException("Unknown block effect type: " + src.getClass());
            }
            jsonObject.addProperty("effect_type", src.typeId());
            jsonObject.addProperty("enabled", src.enabled);
            return jsonObject;
        }
    }
}
