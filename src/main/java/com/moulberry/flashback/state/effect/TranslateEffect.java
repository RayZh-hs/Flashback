package com.moulberry.flashback.state.effect;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import net.minecraft.client.resources.language.I18n;

/**
 * Effect that translates selected blocks by a vector offset.
 */
public class TranslateEffect extends BlockEffect {

    private float x;
    private float y;
    private float z;
    private transient boolean detailsExpanded = true;

    public TranslateEffect() {
        this(0.0f, 0.0f, 0.0f);
    }

    public TranslateEffect(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isDetailsExpanded() {
        return this.detailsExpanded;
    }

    public void setDetailsExpanded(boolean detailsExpanded) {
        this.detailsExpanded = detailsExpanded;
    }

    @Override
    public String typeId() {
        return "translate";
    }

    @Override
    public String displayName() {
        return I18n.get("flashback.effect.translate");
    }

    @Override
    public BlockEffect copy() {
        TranslateEffect copy = new TranslateEffect(this.x, this.y, this.z);
        copy.detailsExpanded = this.detailsExpanded;
        copy.enabled = this.enabled;
        return copy;
    }

    public static class TypeAdapter implements JsonSerializer<TranslateEffect>, JsonDeserializer<TranslateEffect> {
        @Override
        public TranslateEffect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float x = jsonObject.has("x") ? jsonObject.get("x").getAsFloat() : 0.0f;
            float y = jsonObject.has("y") ? jsonObject.get("y").getAsFloat() : 0.0f;
            float z = jsonObject.has("z") ? jsonObject.get("z").getAsFloat() : 0.0f;
            return new TranslateEffect(x, y, z);
        }

        @Override
        public JsonElement serialize(TranslateEffect src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("x", src.x);
            jsonObject.addProperty("y", src.y);
            jsonObject.addProperty("z", src.z);
            return jsonObject;
        }
    }
}
