package com.kuilunfuzhe.monvhua.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayFeature;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore;
import com.kuilunfuzhe.monvhua.features.paint.PaintPaperStore;
import com.kuilunfuzhe.monvhua.item.paint.PaintPaperItem;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PaintGraffitiCommand {
    private static final int TILE_SIZE = 16;
    private static final int PAPER_TILE_SIZE = 25;
    private static final int PAPER_PIXEL_SIZE = PAPER_TILE_SIZE * TILE_SIZE;
    private static final int MAX_IMPORTED_PAPERS = 25 * 25;
    private static final int IMPORT_ALPHA_THRESHOLD = 24;
    private static final int IMPORT_ALPHA = 0xCC;
    private static final int IMPORT_COLOR_STEP = 16;
    private static final int MAX_GRID_CLEAR_RADIUS = 2048;
    private static final int MAX_CHUNK_CLEAR_RADIUS = 128;
    private static final int MAX_UPLOADED_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final ConcurrentHashMap<Path, CachedImportImage> IMPORT_IMAGE_CACHE = new ConcurrentHashMap<>();

    private PaintGraffitiCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("monvhua-graffiti")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("folder")
                        .executes(context -> showFolder(context.getSource())))
                .then(CommandManager.literal("import")
                        .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                .executes(context -> importImage(context.getSource(), StringArgumentType.getString(context, "file"), 1.0D))))
                .then(CommandManager.literal("import_scaled")
                        .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.05D, 8.0D))
                                .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                        .executes(context -> importImage(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "file"),
                                                DoubleArgumentType.getDouble(context, "scale"))))))
                .then(CommandManager.literal("clear")
                        .then(clearGridCommand("grid"))
                        .then(clearGridCommand("block-方块"))
                        .then(clearChunkCommand())));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> clearGridCommand(String name) {
        return CommandManager.literal(name)
                .then(CommandManager.argument("radius-半径", IntegerArgumentType.integer(0, MAX_GRID_CLEAR_RADIUS))
                        .executes(context -> clearGrid(context, sourceBlockPos(context.getSource())))
                        .then(CommandManager.argument("center", BlockPosArgumentType.blockPos())
                                .executes(context -> clearGrid(context, BlockPosArgumentType.getLoadedBlockPos(context, "center")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> clearChunkCommand() {
        return CommandManager.literal("chunk")
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(0, MAX_CHUNK_CLEAR_RADIUS))
                        .executes(context -> clearChunk(context, sourceBlockPos(context.getSource())))
                        .then(CommandManager.argument("center", BlockPosArgumentType.blockPos())
                                .executes(context -> clearChunk(context, BlockPosArgumentType.getLoadedBlockPos(context, "center")))));
    }

    private static int clearGrid(CommandContext<ServerCommandSource> context, BlockPos center) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        ServerWorld world = context.getSource().getWorld();
        int removed = clearStoredFaces(world, face -> isInsideGridRadius(face.pos(), center, radius));
        context.getSource().sendFeedback(
                () -> Text.literal("Cleared " + removed + " paint face(s) in grid radius " + radius
                        + " around " + center.toShortString() + "."),
                true
        );
        return removed;
    }

    private static int clearChunk(CommandContext<ServerCommandSource> context, BlockPos center) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        ServerWorld world = context.getSource().getWorld();
        ChunkPos centerChunk = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
        int removed = clearStoredFaces(world, face -> isInsideChunkRadius(face.pos(), centerChunk, radius));
        context.getSource().sendFeedback(
                () -> Text.literal("Cleared " + removed + " paint face(s) in chunk radius " + radius
                        + " around chunk [" + centerChunk.x + ", " + centerChunk.z + "]."),
                true
        );
        return removed;
    }

    private static int clearStoredFaces(ServerWorld world, java.util.function.Predicate<PaintOverlayStore.StoredFace> predicate) {
        int removed = 0;
        for (PaintOverlayStore.StoredFace face : PaintOverlayStore.get(world).toStoredFaces()) {
            if (!predicate.test(face)) {
                continue;
            }
            PaintOverlayFeature.clearFace(world, face.pos(), face.face());
            removed++;
        }
        return removed;
    }

    private static boolean isInsideGridRadius(BlockPos pos, BlockPos center, int radius) {
        return Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getY() - center.getY()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    private static boolean isInsideChunkRadius(BlockPos pos, ChunkPos centerChunk, int radius) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return Math.abs(chunkX - centerChunk.x) <= radius
                && Math.abs(chunkZ - centerChunk.z) <= radius;
    }

    private static BlockPos sourceBlockPos(ServerCommandSource source) {
        return BlockPos.ofFloored(source.getPosition());
    }

    private static int showFolder(ServerCommandSource source) {
        Path folder = graffitiFolder(source);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to create graffiti folder: " + e.getMessage()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Graffiti folder: " + folder), false);
        return 1;
    }

    private static int importImage(ServerCommandSource source, String filename, double scale) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        Path folder = graffitiFolder(source);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to create graffiti folder: " + e.getMessage()));
            return 0;
        }
        Path imagePath = folder.resolve(filename).normalize();
        if (!imagePath.startsWith(folder)) {
            source.sendError(Text.literal("Image path must stay inside the graffiti folder."));
            return 0;
        }
        if (!Files.isRegularFile(imagePath)) {
            source.sendError(Text.literal("Image not found: " + imagePath));
            return 0;
        }

        MinecraftServer server = source.getServer();
        UUID playerId = player.getUuid();
        source.sendFeedback(() -> Text.literal("Started importing " + imagePath.getFileName() + " at " + formatScale(scale) + "x..."), false);
        CompletableFuture.supplyAsync(() -> loadImport(imagePath, scale))
                .thenAccept(result -> server.execute(() -> finishImport(server, playerId, result)));
        return 1;
    }

    public static void importUploadedImage(ServerPlayerEntity player, String filename, double scale, byte[] imageBytes) {
        if (player == null) {
            return;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            player.sendMessage(Text.literal("No uploaded image data."), false);
            return;
        }
        if (imageBytes.length > MAX_UPLOADED_IMAGE_BYTES) {
            player.sendMessage(Text.literal("Uploaded image is too large, max 8 MiB."), false);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID playerId = player.getUuid();
        String safeFilename = safeFilename(filename);
        double safeScale = Math.max(0.05D, Math.min(8.0D, scale));
        byte[] copiedBytes = imageBytes.clone();
        player.sendMessage(Text.literal("Started importing uploaded " + safeFilename + " at " + formatScale(safeScale) + "x..."), false);
        CompletableFuture.supplyAsync(() -> loadImport(safeFilename, copiedBytes, safeScale))
                .thenAccept(result -> server.execute(() -> finishImport(server, playerId, result)));
    }

    private static ImportResult loadImport(Path imagePath, double scale) {
        BufferedImage image;
        try {
            image = cachedImportImage(imagePath).image();
        } catch (IOException e) {
            return ImportResult.error("Failed to read image: " + e.getMessage());
        }
        if (image == null) {
            return ImportResult.error("Unsupported image file: " + imagePath.getFileName());
        }
        image = scaledImage(image, scale);

        int paperColumns = Math.max(1, (image.getWidth() + PAPER_PIXEL_SIZE - 1) / PAPER_PIXEL_SIZE);
        int paperRows = Math.max(1, (image.getHeight() + PAPER_PIXEL_SIZE - 1) / PAPER_PIXEL_SIZE);
        if (paperColumns * paperRows > MAX_IMPORTED_PAPERS) {
            return ImportResult.error("Scaled image is too large: " + paperColumns + "x" + paperRows + " papers, max 25x25.");
        }

        String imageName = baseName(imagePath.getFileName().toString());
        List<ImportedPaper> papers = new ArrayList<>();
        for (int paperY = 0; paperY < paperRows; paperY++) {
            for (int paperX = 0; paperX < paperColumns; paperX++) {
                int pixelStartX = paperX * PAPER_PIXEL_SIZE;
                int pixelStartY = paperY * PAPER_PIXEL_SIZE;
                int remainingWidth = Math.max(1, image.getWidth() - pixelStartX);
                int remainingHeight = Math.max(1, image.getHeight() - pixelStartY);
                int tileColumns = Math.min(PAPER_TILE_SIZE, Math.max(1, (remainingWidth + TILE_SIZE - 1) / TILE_SIZE));
                int tileRows = Math.min(PAPER_TILE_SIZE, Math.max(1, (remainingHeight + TILE_SIZE - 1) / TILE_SIZE));
                int paperSize = Math.max(tileColumns, tileRows);
                List<PaintPaperStore.Cell> cells = paperCells(image, paperX, paperY, tileColumns, tileRows);
                papers.add(new ImportedPaper(
                        imageName + "(" + (paperX + 1) + "," + (paperY + 1) + ")",
                        paperSize,
                        cells
                ));
            }
        }

        return new ImportResult(null, imagePath.getFileName().toString(), scale, paperColumns, paperRows, papers);
    }

    private static ImportResult loadImport(String filename, byte[] imageBytes, double scale) {
        BufferedImage image;
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes)) {
            image = ImageIO.read(input);
        } catch (IOException e) {
            return ImportResult.error("Failed to read uploaded image: " + e.getMessage());
        }
        if (image == null) {
            return ImportResult.error("Unsupported uploaded image file: " + filename);
        }
        return loadImport(filename, image, scale);
    }

    private static ImportResult loadImport(String filename, BufferedImage image, double scale) {
        image = scaledImage(image, scale);

        int paperColumns = Math.max(1, (image.getWidth() + PAPER_PIXEL_SIZE - 1) / PAPER_PIXEL_SIZE);
        int paperRows = Math.max(1, (image.getHeight() + PAPER_PIXEL_SIZE - 1) / PAPER_PIXEL_SIZE);
        if (paperColumns * paperRows > MAX_IMPORTED_PAPERS) {
            return ImportResult.error("Scaled image is too large: " + paperColumns + "x" + paperRows + " papers, max 25x25.");
        }

        String imageName = baseName(filename);
        List<ImportedPaper> papers = new ArrayList<>();
        for (int paperY = 0; paperY < paperRows; paperY++) {
            for (int paperX = 0; paperX < paperColumns; paperX++) {
                int pixelStartX = paperX * PAPER_PIXEL_SIZE;
                int pixelStartY = paperY * PAPER_PIXEL_SIZE;
                int remainingWidth = Math.max(1, image.getWidth() - pixelStartX);
                int remainingHeight = Math.max(1, image.getHeight() - pixelStartY);
                int tileColumns = Math.min(PAPER_TILE_SIZE, Math.max(1, (remainingWidth + TILE_SIZE - 1) / TILE_SIZE));
                int tileRows = Math.min(PAPER_TILE_SIZE, Math.max(1, (remainingHeight + TILE_SIZE - 1) / TILE_SIZE));
                int paperSize = Math.max(tileColumns, tileRows);
                List<PaintPaperStore.Cell> cells = paperCells(image, paperX, paperY, tileColumns, tileRows);
                papers.add(new ImportedPaper(
                        imageName + "(" + (paperX + 1) + "," + (paperY + 1) + ")",
                        paperSize,
                        cells
                ));
            }
        }

        return new ImportResult(null, filename, scale, paperColumns, paperRows, papers);
    }

    private static CachedImportImage cachedImportImage(Path imagePath) throws IOException {
        long lastModified = Files.getLastModifiedTime(imagePath).toMillis();
        long size = Files.size(imagePath);
        CachedImportImage cached = IMPORT_IMAGE_CACHE.get(imagePath);
        if (cached != null && cached.lastModified() == lastModified && cached.size() == size) {
            return cached;
        }
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IOException("Unsupported image file: " + imagePath.getFileName());
        }
        CachedImportImage next = new CachedImportImage(lastModified, size, image);
        IMPORT_IMAGE_CACHE.put(imagePath, next);
        return next;
    }

    private static void finishImport(MinecraftServer server, UUID playerId, ImportResult result) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (result.error() != null) {
            player.sendMessage(Text.literal(result.error()), false);
            return;
        }
        ServerWorld world = player.getWorld();
        int created = 0;
        for (ImportedPaper imported : result.papers()) {
            ItemStack paper = PaintPaperItem.createSavedPaper(world, imported.name(), imported.size(), imported.cells());
            if (!player.getInventory().insertStack(paper)) {
                player.dropItem(paper, false);
            }
            created++;
        }
        player.sendMessage(Text.literal("Imported " + result.filename() + " at " + formatScale(result.scale()) + "x as " + created
                + " paint papers from " + result.paperColumns() + "x" + result.paperRows() + " paper pages."), false);
    }

    private static BufferedImage scaledImage(BufferedImage source, double scale) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        if (width == source.getWidth() && height == source.getHeight()) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private static Path graffitiFolder(ServerCommandSource source) {
        return FabricLoader.getInstance().getGameDir().resolve("graffiti").normalize();
    }

    private static List<PaintPaperStore.Cell> paperCells(BufferedImage image, int paperX, int paperY, int tileColumns, int tileRows) {
        List<PaintPaperStore.Cell> cells = new ArrayList<>();
        int tileStartX = paperX * PAPER_TILE_SIZE;
        int tileStartY = paperY * PAPER_TILE_SIZE;
        for (int y = 0; y < tileRows; y++) {
            for (int x = 0; x < tileColumns; x++) {
                int[] pixels = tilePixels(image, tileStartX + x, tileStartY + y);
                if (hasPixels(pixels)) {
                    cells.add(new PaintPaperStore.Cell(x, y, pixels));
                }
            }
        }
        return cells;
    }

    private static int[] tilePixels(BufferedImage image, int tileX, int tileY) {
        int[] pixels = new int[TILE_SIZE * TILE_SIZE];
        int startX = tileX * TILE_SIZE;
        int startY = tileY * TILE_SIZE;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int imageX = startX + x;
                int imageY = startY + y;
                if (imageX >= image.getWidth() || imageY >= image.getHeight()) {
                    continue;
                }
                int argb = image.getRGB(imageX, imageY);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < IMPORT_ALPHA_THRESHOLD) {
                    continue;
                }
                pixels[y * TILE_SIZE + x] = normalizeImportColor(argb);
            }
        }
        return pixels;
    }

    private static int normalizeImportColor(int argb) {
        int red = quantizeChannel((argb >>> 16) & 0xFF);
        int green = quantizeChannel((argb >>> 8) & 0xFF);
        int blue = quantizeChannel(argb & 0xFF);
        return (IMPORT_ALPHA << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int quantizeChannel(int channel) {
        return Math.min(255, ((channel + IMPORT_COLOR_STEP / 2) / IMPORT_COLOR_STEP) * IMPORT_COLOR_STEP);
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        String name = dot <= 0 ? filename : filename.substring(0, dot);
        return name.isBlank() ? "image" : name;
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image.png";
        }
        String safe = Path.of(filename).getFileName().toString();
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "image.png" : safe;
    }

    private static String formatScale(double scale) {
        return String.format(java.util.Locale.ROOT, "%.2f", scale);
    }

    private record ImportedPaper(String name, int size, List<PaintPaperStore.Cell> cells) {
    }

    private record CachedImportImage(long lastModified, long size, BufferedImage image) {
    }

    private record ImportResult(String error, String filename, double scale, int paperColumns, int paperRows, List<ImportedPaper> papers) {
        private static ImportResult error(String message) {
            return new ImportResult(message, "", 1.0D, 0, 0, List.of());
        }
    }
}
