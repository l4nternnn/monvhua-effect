package com.kuilunfuzhe.monvhua.features.daynight;

import com.google.gson.*;

public final class DayNightConfigMigration {
    private static final Gson GSON=new Gson();
    public static DayNightConfig read(JsonElement element) {
        if(element==null||!element.isJsonObject()) throw new IllegalArgumentException("Missing configuration");
        JsonObject obj=element.getAsJsonObject();
        DayNightConfig config=obj.has("segments")?GSON.fromJson(obj,DayNightConfig.class):
                new DayNightConfig(obj.get("enabled").getAsBoolean(),obj.get("dawn").getAsInt(),
                        obj.get("day").getAsInt(),obj.get("dusk").getAsInt(),obj.get("night").getAsInt());
        if(!config.valid()) throw new IllegalArgumentException(config.validationError());return config;
    }
}
