package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.area_tip.AreaTipPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public final class TextAreaResourceSyncClient {
    private static final int BYTES_PER_TICK = 3072;
    private static final Queue<UploadJob> UPLOADS = new ArrayDeque<>();
    private static final Map<String, DownloadState> DOWNLOADS = new HashMap<>();
    private static UploadJob activeUpload;
    private static boolean initialized;

    private TextAreaResourceSyncClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(TextAreaResourceSyncClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceSyncStartS2C.ID, (packet, context) ->
                context.client().execute(() -> startDownload(packet)));
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceSyncChunkS2C.ID, (packet, context) ->
                context.client().execute(() -> receiveChunk(packet)));
        ClientPlayNetworking.registerGlobalReceiver(AreaTipPackets.ResourceDeleteS2C.ID, (packet, context) ->
                context.client().execute(() -> deleteLocal(packet.filename())));
    }

    public static void enqueueUpload(String filename, Path path) {
        if (filename == null || filename.isBlank() || path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                return;
            }
            UPLOADS.removeIf(job -> job.filename().equals(filename));
            UPLOADS.add(new UploadJob(filename, bytes, Math.max(1, (int) Math.ceil(bytes.length / (double) BYTES_PER_TICK)), 0));
        } catch (IOException ignored) {
        }
    }

    public static void deleteRemote(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        UPLOADS.removeIf(job -> job.filename().equals(filename));
        if (activeUpload != null && activeUpload.filename().equals(filename)) {
            activeUpload = null;
        }
        SafeClientNetworking.send(new AreaTipPackets.ResourceDeleteC2S(filename));
    }

    public static void requestMissingResources(Iterable<String> backgrounds) {
        if (backgrounds == null) {
            return;
        }
        for (String background : backgrounds) {
            if (background == null || background.isBlank() || background.startsWith("gui/text_ui/")) {
                continue;
            }
            Path path = resolveLocal(background);
            if (!Files.isRegularFile(path)) {
                SafeClientNetworking.send(new AreaTipPackets.ResourceRequestC2S(background));
            }
        }
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (activeUpload == null) {
            activeUpload = UPLOADS.poll();
            if (activeUpload != null) {
                SafeClientNetworking.send(new AreaTipPackets.ResourceUploadStartC2S(
                        activeUpload.filename(),
                        activeUpload.bytes().length,
                        activeUpload.totalChunks()
                ));
            }
        }
        if (activeUpload == null) {
            return;
        }
        int offset = activeUpload.chunkIndex() * BYTES_PER_TICK;
        if (offset >= activeUpload.bytes().length) {
            activeUpload = null;
            return;
        }
        int end = Math.min(activeUpload.bytes().length, offset + BYTES_PER_TICK);
        byte[] chunk = Arrays.copyOfRange(activeUpload.bytes(), offset, end);
        SafeClientNetworking.send(new AreaTipPackets.ResourceUploadChunkC2S(
                activeUpload.filename(),
                activeUpload.chunkIndex(),
                activeUpload.totalChunks(),
                chunk
        ));
        int nextChunk = activeUpload.chunkIndex() + 1;
        activeUpload = nextChunk >= activeUpload.totalChunks()
                ? null
                : new UploadJob(activeUpload.filename(), activeUpload.bytes(), activeUpload.totalChunks(), nextChunk);
    }

    private static void startDownload(AreaTipPackets.ResourceSyncStartS2C packet) {
        if (packet.totalBytes() <= 0 || packet.totalChunks() <= 0) {
            return;
        }
        try {
            Path temp = tempPath(packet.filename());
            Files.createDirectories(temp.getParent());
            Files.deleteIfExists(temp);
            DOWNLOADS.put(packet.filename(), new DownloadState(packet.filename(), packet.totalBytes(), packet.totalChunks(), temp, 0, 0));
        } catch (IOException ignored) {
        }
    }

    private static void receiveChunk(AreaTipPackets.ResourceSyncChunkS2C packet) {
        DownloadState state = DOWNLOADS.get(packet.filename());
        if (state == null || packet.chunkIndex() != state.nextChunk()) {
            return;
        }
        byte[] bytes = packet.bytes();
        if (bytes.length == 0 || state.receivedBytes() + bytes.length > state.totalBytes()) {
            DOWNLOADS.remove(packet.filename());
            deleteQuietly(state.tempPath());
            return;
        }
        try {
            try (OutputStream output = Files.newOutputStream(state.tempPath(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                output.write(bytes);
            }
            int nextChunk = state.nextChunk() + 1;
            int received = state.receivedBytes() + bytes.length;
            if (nextChunk >= state.totalChunks() && received == state.totalBytes()) {
                Path target = resolveLocal(packet.filename());
                Files.createDirectories(target.getParent());
                Files.move(state.tempPath(), target, StandardCopyOption.REPLACE_EXISTING);
                DOWNLOADS.remove(packet.filename());
                TextGroupRenderer.cleanupTextures();
            } else {
                DOWNLOADS.put(packet.filename(), new DownloadState(state.filename(), state.totalBytes(), state.totalChunks(), state.tempPath(), nextChunk, received));
            }
        } catch (IOException ignored) {
            DOWNLOADS.remove(packet.filename());
            deleteQuietly(state.tempPath());
        }
    }

    private static void deleteLocal(String filename) {
        deleteQuietly(resolveLocal(filename));
        TextGroupRenderer.cleanupTextures();
    }

    private static Path resolveLocal(String filename) {
        Path root = TextGroupRenderer.textureDir().normalize();
        Path path = root.resolve(safeResourceName(filename)).normalize();
        return path.startsWith(root) ? path : root.resolve("background.png");
    }

    private static Path tempPath(String filename) {
        return TextGroupRenderer.textureDir().resolve(".download_" + safeResourceName(filename) + ".tmp").normalize();
    }

    private static String safeResourceName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "background.png";
        }
        String clean = filename.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) {
            clean = clean.substring(slash + 1);
        }
        clean = clean.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return clean.isBlank() ? "background.png" : clean;
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    private record UploadJob(String filename, byte[] bytes, int totalChunks, int chunkIndex) {
    }

    private record DownloadState(String filename, int totalBytes, int totalChunks, Path tempPath, int nextChunk, int receivedBytes) {
    }
}
