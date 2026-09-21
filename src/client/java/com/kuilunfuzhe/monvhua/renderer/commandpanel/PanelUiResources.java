package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.google.gson.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One atomic, validated resource-pack snapshot. Never reads the source tree. */
final class PanelUiResources {
    private static final String DIRECTORY = "textures/gui/command_panel_ui_and_text";
    static final String BASE = DIRECTORY + "/";
    record Region(String key, String kind, int x, int y, int width, int height, int size, String text) {}
    record Snapshot(int width, int height, int renderScale, List<Region> regions, JsonObject names,
                    JsonObject descriptions, Map<Identifier, BufferedImage> images, Font font, Font nameFont, boolean guides) {}
    static Identifier id(String file) { return Identifier.of("monvhua", BASE + file); }
    static volatile Snapshot current = fallback();
    static volatile int generation;

    static void reload(ResourceManager manager) {
        try {
            JsonObject layout = json(manager, "layout.json");
            JsonArray canvas = layout.getAsJsonArray("canvas");
            int width = integer(canvas.get(0), "canvas.width", 64, 2048);
            int height = integer(canvas.get(1), "canvas.height", 64, 2048);
            int renderScale = integer(layout.has("render_scale") ? layout.get("render_scale") : new JsonPrimitive(1),
                    "render_scale", 1, 4);
            List<Region> regions = new ArrayList<>();
            for (var entry : layout.getAsJsonObject("regions").entrySet()) {
                String key = entry.getKey();
                JsonObject r = entry.getValue().getAsJsonObject();
                regions.add(new Region(key, string(r,"kind","text"),
                        integer(r.get("x"), key+".x", -4096,4096), integer(r.get("y"),key+".y",-4096,4096),
                        integer(r.get("width"),key+".width",1,4096), integer(r.get("height"),key+".height",1,4096),
                        integer(r.get("size"),key+".size",1,512), string(r,"text","")));
            }
            JsonObject names = json(manager,"charactor_name.json");
            for (var entry : names.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) { entry.getValue().getAsString(); continue; }
                JsonObject style = entry.getValue().getAsJsonObject();
                String name = style.get("name").getAsString();
                for (String color : List.of("color","first_color"))
                    if (style.has(color) && !style.get(color).getAsString().matches("#[0-9a-fA-F]{6}"))
                        throw new IllegalArgumentException(entry.getKey()+"."+color+": expected #RRGGBB");
                if (style.has("char_scales")) for (var scale : style.getAsJsonObject("char_scales").entrySet()) {
                    int index = Integer.parseInt(scale.getKey());
                    double value = scale.getValue().getAsDouble();
                    if (index < 1 || index > name.codePointCount(0,name.length()) || !Double.isFinite(value) || value <= 0 || value > 16)
                        throw new IllegalArgumentException(entry.getKey()+".char_scales."+scale.getKey());
                }
            }
            JsonObject descriptions = json(manager,"text.json");
            for (var e : descriptions.entrySet()) e.getValue().getAsString();
            Map<Identifier,BufferedImage> images = new HashMap<>();
            // Resource searches require a directory without a trailing slash.
            loadImages(manager, DIRECTORY, images);
            loadImages(manager, "textures/gui/sprites/hud/heart", images);
            Font font = new Font(string(layout,"font_family","Microsoft YaHei"), Font.PLAIN, 20);
            if (layout.has("font_resource")) {
                try (InputStream stream = manager.getResourceOrThrow(Identifier.of(layout.get("font_resource").getAsString())).getInputStream()) {
                    font = Font.createFont(Font.TRUETYPE_FONT, stream);
                }
            }
            Font nameFont = new Font(string(layout,"name_font_family","STZhongsong"), Font.PLAIN, 20);
            if (layout.has("font_resource")) nameFont = font;
            current = new Snapshot(width,height,renderScale,List.copyOf(regions),names,descriptions,Map.copyOf(images),font,nameFont,
                    layout.has("debug_guides") && layout.get("debug_guides").getAsBoolean());
            generation++;
            LoggerFactory.getLogger("monvhua-panel-ui").info("Panel UI resources loaded: {}x{} logical, scale {}, {} regions, {} images", width, height, renderScale, regions.size(), images.size());
        } catch (Exception ex) {
            LoggerFactory.getLogger("monvhua-panel-ui").error("Panel UI resource reload rejected; retaining last valid layout", ex);
        }
    }

    private static void loadImages(ResourceManager manager, String directory, Map<Identifier,BufferedImage> images) throws IOException {
        for (var entry : manager.findResources(directory, id -> id.getPath().endsWith(".png")).entrySet()) {
            try (InputStream input = entry.getValue().getInputStream()) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) throw new IOException("Unreadable image: "+entry.getKey());
                images.put(entry.getKey(),image);
            }
        }
    }
    private static JsonObject json(ResourceManager manager, String file) throws IOException {
        try (Reader reader = new InputStreamReader(manager.getResourceOrThrow(id(file)).getInputStream(),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException ex) { throw new IOException(file+": "+ex.getMessage(),ex); }
    }
    private static int integer(JsonElement e, String field, int min, int max) {
        double value = e.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max)
            throw new IllegalArgumentException(field+": out of range");
        return (int)value;
    }
    static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }
    private static Snapshot fallback() {
        return new Snapshot(1000,640,1,List.of(new Region("提示","text",60,60,880,200,28,"面板资源尚未加载")),
                new JsonObject(),new JsonObject(),Map.of(),new Font("Dialog",Font.PLAIN,20),new Font("Dialog",Font.PLAIN,20),false);
    }
}
