package com.moulberry.flashback.state.effect;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.client.resources.language.I18n;

import java.lang.reflect.Type;

/**
 * Effect that scales block opacity in a selection.
 */
public class OpacityEffect extends BlockEffect {

    private float opacity;

    public OpacityEffect() {
        this(1.0f);
    }

    public OpacityEffect(float opacity) {
        this.opacity = Math.clamp(opacity, 0.0f, 1.0f);
    }

    public float getOpacity() {
        return this.opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.clamp(opacity, 0.0f, 1.0f);
    }

    @Override
    public String typeId() {
        return "opacity";
    }

    @Override
    public String displayName() {
        return I18n.get("flashback.effect.opacity");
    }

    @Override
    public BlockEffect copy() {
        OpacityEffect copy = new OpacityEffect(this.opacity);
        copy.enabled = this.enabled;
        return copy;
    }

    public static class TypeAdapter implements JsonSerializer<OpacityEffect>, JsonDeserializer<OpacityEffect> {
        @Override
        public OpacityEffect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float opacity = jsonObject.has("opacity") ? jsonObject.get("opacity").getAsFloat() : 1.0f;
            return new OpacityEffect(opacity);
        }

        @Override
        public JsonElement serialize(OpacityEffect src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("opacity", src.opacity);
            return jsonObject;
        }
    }
}
