package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.google.gson.*;
import com.kuilunfuzhe.monvhua.network.commandpanel.PanelStatusS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** CPU offscreen canvas; all text remains data-driven, never baked into assets. */
public final class PanelUiTexture {
    private static final Identifier FIXED_ID = Identifier.of("monvhua", "dynamic/command_panel_fixed");
    private static final Identifier ROLE_ID = Identifier.of("monvhua", "dynamic/command_panel_role");
    private static final Identifier MC_NAME_ID = Identifier.of("monvhua", "dynamic/command_panel_mc_name");
    private static final Identifier MAGIC_ID = Identifier.of("monvhua", "dynamic/command_panel_magic");
    private static final Identifier HEALTH_ID = Identifier.of("monvhua", "dynamic/command_panel_health");
    private static final Identifier DEATH_ID = PanelUiResources.id("death.png");
    private static final String ROLE_NAME_KEY = "\u89d2\u8272\u540d\u5b57";
    private static final String MC_NAME_KEY = "MC\u540d\u5b57";
    private static final String MAGIC_VALUE_KEY = "\u9b54\u5973\u5316\u6570\u503c";
    private static PanelStatusS2C status;
    private static NativeImageBackedTexture fixedTexture, roleTexture, mcNameTexture, magicTexture, healthTexture;
    private static int generation = -1, animationFrame = -1;
    private static long flashUntil;
    private static float previousHealth;
    private static boolean fixedDirty = true, roleDirty = true, mcNameDirty = true, magicDirty = true, healthDirty = true;
    private static String pendingRole = "";
    private static String pendingUuid = "";
    private static long pendingSession;
    private static int pendingRequest = -1;

    static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return Identifier.of("monvhua","panel_ui_layout"); }
            public void reload(ResourceManager manager) {
                int before = PanelUiResources.generation;
                PanelUiResources.reload(manager);
                if (before != PanelUiResources.generation) markAllDirty();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(PanelStatusS2C.ID, (packet, context) -> context.client().execute(() -> {
            if (packet.sessionId() != com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.sessionId()
                    || packet.requestSeq() < com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.requestSeq()) return;
            var resources = PanelUiResources.current;
            String oldRole = role(resources);
            PanelStatusS2C old = status;
            boolean sameSelection = status != null
                    && packet.sessionId() == status.sessionId()
                    && packet.requestSeq() == status.requestSeq()
                    && packet.roleTag().equals(status.roleTag())
                    && packet.hasTarget() == status.hasTarget()
                    && packet.username().equals(status.username());
            if (!sameSelection) {
                previousHealth = packet.health();
                flashUntil = 0;
            } else if (packet.health() != status.health()) {
                previousHealth = status.health();
                flashUntil = System.currentTimeMillis() + (packet.health() < status.health() ? 1000 : 500);
            }
            status = packet;
            if (packet.sessionId() == pendingSession && packet.requestSeq() == pendingRequest
                    && packet.roleTag().equals(pendingRole)) {
                pendingRole = "";
                pendingUuid = "";
                pendingSession = 0;
                pendingRequest = -1;
            }
            if (!oldRole.equals(role(resources))) roleDirty = true;
            if (old == null || old.hasTarget() != packet.hasTarget() || !old.username().equals(packet.username())) {
                mcNameDirty = true;
            }
            if (old == null || old.hasTarget() != packet.hasTarget() || old.hasScore() != packet.hasScore()
                    || old.score() != packet.score()) magicDirty = true;
            if (old == null || old.hasTarget() != packet.hasTarget() || old.health() != packet.health()
                    || old.maximum() != packet.maximum() || old.absorption() != packet.absorption()
                    || old.dead() != packet.dead() || !old.heartType().equals(packet.heartType())
                    || old.hardcore() != packet.hardcore() || old.regenerating() != packet.regenerating()) {
                healthDirty = true;
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            status = null;
            flashUntil = 0;
            previousHealth = 0;
            pendingRole = "";
            pendingUuid = "";
            pendingSession = 0;
            pendingRequest = -1;
            markAllDirty();
            com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.resetConnection();
            destroyTextures(client);
        }));
    }

    /** Rebuilds the panel resource snapshot for a player targeted by the server command. */
    public static void reloadForCommand() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getResourceManager() != null) {
            PanelUiResources.reload(client.getResourceManager());
            markAllDirty();
        }
    }

    private enum Layer { ROLE, NAME, MAGIC, HEALTH }
    private record Bounds(int x, int y, int width, int height) {}

    private static void markAllDirty() {
        fixedDirty = true;
        roleDirty = true;
        markDynamicDirty();
    }

    private static void markDynamicDirty() {
        mcNameDirty = true;
        magicDirty = true;
        healthDirty = true;
        animationFrame = -1;
    }

    private static void markRoleAndDynamicDirty() {
        roleDirty = true;
        markDynamicDirty();
    }

    private static void clearStaleStatus() {
        if (status != null && (status.sessionId() != com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.sessionId()
                || status.requestSeq() < com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.requestSeq())) {
            status = null;
            flashUntil = 0;
            previousHealth = 0;
            markRoleAndDynamicDirty();
        }
    }

    private static void destroyTextures(MinecraftClient client) {
        client.getTextureManager().destroyTexture(FIXED_ID);
        client.getTextureManager().destroyTexture(ROLE_ID);
        client.getTextureManager().destroyTexture(MC_NAME_ID);
        client.getTextureManager().destroyTexture(MAGIC_ID);
        client.getTextureManager().destroyTexture(HEALTH_ID);
        fixedTexture = null;
        roleTexture = null;
        mcNameTexture = null;
        magicTexture = null;
        healthTexture = null;
    }

    private static Bounds boundsForStatic(PanelUiResources.Snapshot resources) {
        return new Bounds(0, 0, resources.width(), resources.height());
    }

    private static Bounds boundsForDynamic(PanelUiResources.Snapshot resources, Layer layer) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (var region : resources.regions()) {
            if (layerOf(region) != layer) continue;
            minX = Math.min(minX, region.x());
            minY = Math.min(minY, region.y());
            maxX = Math.max(maxX, region.x() + region.width());
            maxY = Math.max(maxY, region.y() + region.height());
        }
        if (minX == Integer.MAX_VALUE) return new Bounds(0, 0, 1, 1);
        return new Bounds(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private static Layer layerOf(PanelUiResources.Region region) {
        if (ROLE_NAME_KEY.equals(region.key()) || "avatar".equals(region.kind()) || "intro".equals(region.kind())) return Layer.ROLE;
        if (MC_NAME_KEY.equals(region.key())) return Layer.NAME;
        if (MAGIC_VALUE_KEY.equals(region.key())) return Layer.MAGIC;
        if ("health".equals(region.kind())) return Layer.HEALTH;
        return null;
    }

    private static Bounds boundsForRole(PanelUiResources.Snapshot resources) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (var region : resources.regions()) {
            if (layerOf(region) != Layer.ROLE) continue;
            minX = Math.min(minX, region.x());
            minY = Math.min(minY, region.y());
            maxX = Math.max(maxX, region.x() + region.width());
            maxY = Math.max(maxY, region.y() + region.height());
        }
        if (minX == Integer.MAX_VALUE) return new Bounds(0, 0, 1, 1);
        return new Bounds(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private static void ensureTextures(MinecraftClient client, PanelUiResources.Snapshot resources, long now) {
        if (generation != PanelUiResources.generation) {
            generation = PanelUiResources.generation;
            markAllDirty();
        }
        if (fixedDirty) {
            Bounds bounds = boundsForStatic(resources);
            fixedTexture = upload(client, FIXED_ID, fixedTexture,
                    paint(resources, bounds, region -> layerOf(region) == null, true, now));
            fixedDirty = false;
        }
        if (roleDirty) {
            Bounds bounds = boundsForRole(resources);
            roleTexture = upload(client, ROLE_ID, roleTexture,
                    paint(resources, bounds, region -> layerOf(region) == Layer.ROLE, false, now));
            roleDirty = false;
        }
        if (mcNameDirty) {
            Bounds bounds = boundsForDynamic(resources, Layer.NAME);
            mcNameTexture = upload(client, MC_NAME_ID, mcNameTexture,
                    paint(resources, bounds, region -> layerOf(region) == Layer.NAME, false, now));
            mcNameDirty = false;
        }
        if (magicDirty) {
            Bounds bounds = boundsForDynamic(resources, Layer.MAGIC);
            magicTexture = upload(client, MAGIC_ID, magicTexture,
                    paint(resources, bounds, region -> layerOf(region) == Layer.MAGIC, false, now));
            magicDirty = false;
        }
        if (healthDirty) {
            Bounds bounds = boundsForDynamic(resources, Layer.HEALTH);
            healthTexture = upload(client, HEALTH_ID, healthTexture,
                    paint(resources, bounds, region -> layerOf(region) == Layer.HEALTH, false, now));
            healthDirty = false;
        }
    }

    private static BufferedImage paint(PanelUiResources.Snapshot resources, Bounds bounds,
                                       Predicate<PanelUiResources.Region> include, boolean background, long now) {
        int scale = resources.renderScale();
        BufferedImage image = new BufferedImage(Math.max(1, bounds.width() * scale),
                Math.max(1, bounds.height() * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(scale, scale);
        graphics.translate(-bounds.x(), -bounds.y());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (background) {
            BufferedImage panel = resources.images().get(PanelUiResources.id("backgorund.png"));
            if (panel != null) graphics.drawImage(panel, 0, 0, resources.width(), resources.height(), null);
            else {
                graphics.setColor(new Color(6, 8, 16));
                graphics.fillRect(0, 0, resources.width(), resources.height());
            }
        }
        String role = role(resources);
        for (var region : resources.regions()) {
            if (!include.test(region)) continue;
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(region.x(), region.y());
            if (!ROLE_NAME_KEY.equals(region.key())) g.clipRect(0, 0, region.width(), region.height());
            g.setColor(new Color(0x2e2322));
            g.setFont(resources.font().deriveFont((float) region.size()));
            paintRegion(g, resources, region, role, now);
            g.dispose();
            if (resources.guides()) {
                graphics.setColor(new Color(0x536078));
                graphics.drawRect(region.x(), region.y(), region.width(), region.height());
            }
        }
        graphics.dispose();
        return image;
    }

    private static void paintRegion(Graphics2D g, PanelUiResources.Snapshot resources,
                                    PanelUiResources.Region region, String role, long now) {
        switch (region.kind()) {
            case "profile_frame" -> {
                BufferedImage frame = resources.images().get(PanelUiResources.id("profile_frame.png"));
                if (frame != null) g.drawImage(frame, 0, 0, region.width(), region.height(), null);
            }
            case "avatar" -> {
                BufferedImage portrait = portrait(resources, role);
                if (portrait != null) fit(g, portrait, region.width(), region.height());
                else text(g, "No portrait", region);
            }
            case "background" -> fit(g, resources.images().get(PanelUiResources.id("name_background.png")), region.width(), region.height());
            case "intro" -> text(g, PanelUiResources.string(resources.descriptions(), role, "No character introduction"), region);
            case "health" -> hearts(g, resources, region, now);
            default -> {
                if (ROLE_NAME_KEY.equals(region.key())) name(g, resources, region, role);
                else if (MC_NAME_KEY.equals(region.key())) text(g, playerName(), region);
                else if (MAGIC_VALUE_KEY.equals(region.key())) text(g, magicValue(), region);
                else text(g, region.text(), region);
            }
        }
    }

    private static String playerName() {
        if (status != null && status.hasTarget()) return status.username();
        if (status != null && !status.roleTag().isBlank()) return "No online player";
        if (!pendingRole.isBlank()) return pendingUuid.isBlank() ? "No online player" : "Loading...";
        return "--";
    }

    private static String magicValue() {
        return status == null || !status.hasTarget() || !status.hasScore() ? "--" : Integer.toString(status.score());
    }

    private static NativeImageBackedTexture upload(MinecraftClient client, Identifier id,
                                                    NativeImageBackedTexture current, BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        if (current == null || current.getImage().getWidth() != width || current.getImage().getHeight() != height) {
            if (current != null) client.getTextureManager().destroyTexture(id);
            current = new NativeImageBackedTexture(() -> "Command panel UI layer", new NativeImage(width, height, false));
            client.getTextureManager().registerTexture(id, current);
        }
        NativeImage pixels = current.getImage();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels.setColorArgb(x, y, image.getRGB(x, y));
        current.upload();
        return current;
    }

    private static void drawPart(net.minecraft.client.util.math.MatrixStack matrices,
                                 net.minecraft.client.render.VertexConsumerProvider vertices, Identifier id,
                                 Bounds bounds, PanelUiResources.Snapshot resources, int light, int overlay) {
        float panelX0 = -5.0f / 16.0f, panelX1 = 5.0f / 16.0f;
        float panelZ0 = -3.2f / 16.0f, panelZ1 = 3.2f / 16.0f;
        // The original full-panel quad reverses U (U=1 at local left). Keep
        // cropped layers on the same logical side by mirroring their X span.
        float x0 = panelX0 + (float) (resources.width() - bounds.x() - bounds.width())
                / resources.width() * (panelX1 - panelX0);
        float x1 = panelX0 + (float) (resources.width() - bounds.x())
                / resources.width() * (panelX1 - panelX0);
        float z0 = panelZ0 + (float) (resources.height() - bounds.y() - bounds.height()) / resources.height() * (panelZ1 - panelZ0);
        float z1 = panelZ0 + (float) (resources.height() - bounds.y()) / resources.height() * (panelZ1 - panelZ0);
        float y = (id.equals(FIXED_ID) ? 1.015f : id.equals(DEATH_ID) ? 1.017f : 1.016f) / 16.0f;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertices.getBuffer(net.minecraft.client.render.RenderLayer.getEntityTranslucent(id));
        int fullBright = 0xF000F0;
        buffer.vertex(matrix, x0, y, z0).color(255, 255, 255, 255).texture(1, 1).overlay(overlay).light(fullBright).normal(matrices.peek(), 0, 1, 0);
        buffer.vertex(matrix, x1, y, z0).color(255, 255, 255, 255).texture(0, 1).overlay(overlay).light(fullBright).normal(matrices.peek(), 0, 1, 0);
        buffer.vertex(matrix, x1, y, z1).color(255, 255, 255, 255).texture(0, 0).overlay(overlay).light(fullBright).normal(matrices.peek(), 0, 1, 0);
        buffer.vertex(matrix, x0, y, z1).color(255, 255, 255, 255).texture(1, 0).overlay(overlay).light(fullBright).normal(matrices.peek(), 0, 1, 0);
    }

    public static void acceptSelection(long session, int request, boolean accepted, String role, String uuid) {
        if (session != com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.sessionId()
                || request != com.kuilunfuzhe.monvhua.client.commandpanel.CommandPanelItemUiState.requestSeq()) return;
        if (!accepted) {
            status = null;
            pendingRole = "";
            pendingUuid = "";
            pendingSession = 0;
            pendingRequest = -1;
            flashUntil = 0;
            previousHealth = 0;
            markRoleAndDynamicDirty();
            return;
        }
        String oldRole = role(PanelUiResources.current);
        pendingRole = role == null ? "" : role;
        pendingUuid = uuid == null ? "" : uuid;
        pendingSession = session;
        pendingRequest = request;
        status = null;
        flashUntil = 0;
        previousHealth = 0;
        if (!oldRole.equals(role(PanelUiResources.current))) roleDirty = true;
        markDynamicDirty();
    }

    public static void render(net.minecraft.client.util.math.MatrixStack matrices,
                              net.minecraft.client.render.VertexConsumerProvider vertices, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        PanelUiResources.Snapshot resources = PanelUiResources.current;
        clearStaleStatus();
        long now = System.currentTimeMillis();
        boolean animate = status != null && status.hasTarget()
                && (status.regenerating() || status.health() <= 4 || now < flashUntil);
        int frame = animate ? (int) (now / 150 % 10000) : -1;
        if (frame != animationFrame) healthDirty = true;
        animationFrame = frame;
        ensureTextures(client, resources, now);
        drawPart(matrices, vertices, FIXED_ID, boundsForStatic(resources), resources, light, overlay);
        drawPart(matrices, vertices, ROLE_ID, boundsForRole(resources), resources, light, overlay);
        drawPart(matrices, vertices, MC_NAME_ID, boundsForDynamic(resources, Layer.NAME), resources, light, overlay);
        drawPart(matrices, vertices, MAGIC_ID, boundsForDynamic(resources, Layer.MAGIC), resources, light, overlay);
        drawPart(matrices, vertices, HEALTH_ID, boundsForDynamic(resources, Layer.HEALTH), resources, light, overlay);
        if (status != null && status.dead()) {
            var avatar = resources.regions().stream().filter(r -> "avatar".equals(r.kind())).findFirst().orElse(null);
            if (avatar != null) drawPart(matrices, vertices, DEATH_ID,
                    new Bounds(avatar.x(), avatar.y(), avatar.width(), avatar.height()), resources, light, overlay);
        }
    }

    static Identifier get() {
        ensureTextures(MinecraftClient.getInstance(), PanelUiResources.current, System.currentTimeMillis());
        return FIXED_ID;
    }

    private static String role(PanelUiResources.Snapshot r) {
        if (!pendingRole.isBlank() && (r.names().has(pendingRole) || r.descriptions().has(pendingRole))) return pendingRole;
        if (status == null) return "";
        if (!status.roleTag().isBlank() && (r.names().has(status.roleTag()) || r.descriptions().has(status.roleTag()))) return status.roleTag();
        if (!status.hasTarget()) return "";
        try {
            for (var tag : JsonParser.parseString(status.tags()).getAsJsonArray()) {
                String id = tag.getAsString();
                if (r.names().has(id) || r.descriptions().has(id)) return id;
            }
        } catch (RuntimeException ignored) {
            // A malformed optional tag list must not break the render thread.
        }
        return "";
    }
    private static void fit(Graphics2D g, BufferedImage image, int width, int height) {
        if (image == null) return;
        double ratio = Math.min(1, Math.min((double)width/image.getWidth(),(double)height/image.getHeight()));
        int w = Math.max(1,(int)Math.round(image.getWidth()*ratio)), h = Math.max(1,(int)Math.round(image.getHeight()*ratio));
        g.drawImage(image,(width-w)/2,(height-h)/2,w,h,null);
    }

    private static BufferedImage portrait(PanelUiResources.Snapshot resources, String role) {
        if (role == null || role.isBlank()) return null;
        Identifier exact = Identifier.tryParse("monvhua", PanelUiResources.BASE + "charactor/" + role + ".png");
        BufferedImage image = exact == null ? null : resources.images().get(exact);
        if (image != null) return image;
        Identifier lower = Identifier.tryParse("monvhua", PanelUiResources.BASE + "charactor/" + role.toLowerCase(Locale.ROOT) + ".png");
        image = lower == null ? null : resources.images().get(lower);
        if (image != null) return image;
        String expected = (PanelUiResources.BASE + "charactor/" + role + ".png").toLowerCase(Locale.ROOT);
        for (var entry : resources.images().entrySet()) {
            if (entry.getKey().getNamespace().equals("monvhua")
                    && entry.getKey().getPath().toLowerCase(Locale.ROOT).equals(expected)) return entry.getValue();
        }
        return null;
    }
    private static void text(Graphics2D g, String value, PanelUiResources.Region r) {
        FontMetrics metrics = g.getFontMetrics();
        int baseline = metrics.getAscent(), step = metrics.getHeight()+2;
        StringBuilder line = new StringBuilder();
        for (int cp : (value+"\n").codePoints().toArray()) {
            String ch = new String(Character.toChars(cp));
            if (cp == '\n' || !line.isEmpty() && metrics.stringWidth(line+ch) > r.width()) {
                if (baseline+metrics.getDescent() > r.height()) return;
                g.drawString(line.toString(),0,baseline); baseline += step; line.setLength(0);
            }
            if (cp != '\n') line.append(ch);
        }
    }
    private static void name(Graphics2D g, PanelUiResources.Snapshot resources, PanelUiResources.Region r, String role) {
        JsonElement entry = resources.names().get(role);
        JsonObject style = entry != null && entry.isJsonObject() ? entry.getAsJsonObject() : new JsonObject();
        String value = entry == null ? role.isEmpty() ? "未指定角色" : role
                : entry.isJsonPrimitive() ? entry.getAsString() : PanelUiResources.string(style,"name",role);
        if (value.isBlank()) value = role;
        JsonObject scales = style.has("char_scales") ? style.getAsJsonObject("char_scales") : JsonParser.parseString("{\"1\":1.5}").getAsJsonObject();
        int[] chars = value.codePoints().toArray();
        List<Font> fonts = new ArrayList<>();
        int baseline = 0;
        for (int i=0; i<chars.length; i++) {
            float scale = scales.has(""+(i+1)) ? scales.get(""+(i+1)).getAsFloat() : 1;
            Font font = resources.nameFont().deriveFont(r.size()*scale); fonts.add(font);
            baseline = Math.max(baseline,g.getFontMetrics(font).getAscent());
        }
        int x = 0;
        for (int i=0; i<chars.length; i++) {
            g.setFont(fonts.get(i));
            g.setColor(Color.decode(PanelUiResources.string(style,i==0 ? "first_color" : "color","#dddddd")));
            String ch = new String(Character.toChars(chars[i])); g.drawString(ch,x,baseline);
            x += g.getFontMetrics().stringWidth(ch);
        }
    }

    private static void hearts(Graphics2D g, PanelUiResources.Snapshot resources, PanelUiResources.Region r, long now) {
        if (status == null || !status.hasTarget()) { text(g,"等待玩家状态",r); return; }
        // Bound pathological modded attributes; normal and extra maximum health
        // each retain their own containers, absorption is drawn after them.
        int normal = Math.clamp((int)Math.ceil(status.maximum()/2),1,1000);
        int absorbed = Math.clamp((int)Math.ceil(status.absorption()/2),0,1000);
        int columns = Math.max(1,Math.min(10,(r.width()+8)/(r.size()+8)));
        int rows = (normal+absorbed+columns-1)/columns;
        double size = Math.max(0.1,Math.min(r.size(),Math.min((double)r.width()/columns,(double)r.height()/rows)));
        double stepX = Math.min(size+8,(double)r.width()/columns), stepY = Math.min(size+8,(double)r.height()/rows);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        boolean blink = now < flashUntil && now/150%2==0;
        String hardcore = status.hardcore() ? "hardcore_" : "";
        for (int i=0; i<normal+absorbed; i++) {
            int x=(int)Math.round(i%columns*stepX), y=(int)Math.round(i/columns*stepY);
            if (status.health()<=4 && i<normal) y += (int)((now/150+i*31)%2);
            if (status.regenerating() && i<normal && i==now/150%normal) y -= 2;
            int s=Math.max(1,(int)Math.floor(size));
            sprite(g,resources,"container_"+hardcore+(blink ? "blinking" : ""),x,y,s);
            boolean absorption = i>=normal;
            float remaining = absorption ? status.absorption()-(i-normal)*2 : status.health()-i*2;
            String type = absorption ? status.heartType().equals("withered") ? "withered_" : "absorbing_"
                    : status.heartType().equals("normal") ? "" : status.heartType()+"_";
            if (!absorption && blink && previousHealth-i*2>0)
                sprite(g,resources,type+hardcore+(previousHealth-i*2>1 ? "full" : "half")+"_blinking",x,y,s);
            if (remaining>0) sprite(g,resources,type+hardcore+(remaining>1 ? "full" : "half"),x,y,s);
        }
    }
    private static void sprite(Graphics2D g, PanelUiResources.Snapshot resources, String name, int x, int y, int size) {
        if (name.endsWith("_")) name=name.substring(0,name.length()-1);
        BufferedImage image=resources.images().get(Identifier.of("minecraft","textures/gui/sprites/hud/heart/"+name+".png"));
        if (image!=null) g.drawImage(image,x,y,size,size,null);
    }
    private PanelUiTexture() {}
}
