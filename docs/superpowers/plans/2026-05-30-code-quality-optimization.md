# 代码质量优化实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 系统性修复代码审查中发现的 100+ 问题，按严重程度分 5 批次执行，每批可独立验证并合并。

**架构：** 采用渐进式修复策略——先修安全漏洞和核心 bug（Batch 1-2），再消除重复代码（Batch 3-4），最后做性能优化和命名清理（Batch 5）。每批独立可合，不引入新功能。

**技术栈：** Java 21, Fabric Loom, Minecraft 1.21.8, Mojang Mappings

---

## 批次路线图

| 批次 | 优先级 | 内容 | 预计改动量 | 风险 |
|------|--------|------|-----------|------|
| Batch 1 | P0 🔴 | 安全权限 + 镜像矩阵 bug | ~20 行 | 低 |
| Batch 2 | P1 🟡 | Null 安全 + 防御性编程 | ~30 行 | 低 |
| Batch 3 | P2 🟠 | 高影响去重（5 处） | ~200 行 | 中 |
| Batch 4 | P3 🔵 | 配置类合并 + UI 去重 | ~300 行 | 中高 |
| Batch 5 | P4 ⚪ | 命名规范 + 死代码清理 + 魔法数字 | ~150 行 | 低 |

---

## Batch 1：安全权限 + 核心 Bug修复（P0）

### 任务 1：MirrorConfigUpdateC2SPacket 添加权限检查

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/MonvhuaMod.java:228-241`

**背景：** 第 228 行的 `MirrorConfigUpdateC2SPacket` 接收器无条件接受客户端发来的配置并覆盖服务端。同一文件中第 252 行的 `SecrecyConfigUpdateC2SPacket` 有正确的 `hasPermissionLevel(2)` 检查，这是遗漏而非设计。

- [ ] **步骤 1：添加权限检查**

```java
// 修改前（第 228-241 行）：
ServerPlayNetworking.registerGlobalReceiver(MirrorConfigUpdateC2SPacket.ID, (packet, context) -> {
    context.server().execute(() -> {
        MirrorConfig newConfig = MirrorConfig.fromJson(packet.json());
        if (newConfig != null) {
            MirrorConfig.setInstance(newConfig);
            for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, new MirrorConfigS2CPacket(newConfig.toJson()));
            }
            if (context.player() != null) {
                context.player().sendMessage(Text.literal("§a镜子配置已更新并同步"), true);
            }
        }
    });
});

// 修改后：
ServerPlayNetworking.registerGlobalReceiver(MirrorConfigUpdateC2SPacket.ID, (packet, context) -> {
    context.server().execute(() -> {
        if (context.player() != null && !context.player().hasPermissionLevel(2)) {
            context.player().sendMessage(Text.literal("§c你没有权限修改镜子配置"), true);
            return;
        }
        MirrorConfig newConfig = MirrorConfig.fromJson(packet.json());
        if (newConfig != null) {
            MirrorConfig.setInstance(newConfig);
            for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, new MirrorConfigS2CPacket(newConfig.toJson()));
            }
            if (context.player() != null) {
                context.player().sendMessage(Text.literal("§a镜子配置已更新并同步"), true);
            }
        }
    });
});
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```
预期：BUILD SUCCESSFUL，无新增警告。

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/MonvhuaMod.java
git commit -m "fix: MirrorConfigUpdateC2SPacket 添加 OP 权限检查，防止任意客户端覆盖服务端配置"
```

---

### 任务 2：GazeConfig UpdateConfigC2SPacket 添加权限检查

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/features/guidance/Gazeguidance.java:262-275`

**背景：** 第 262 行无条件执行 `GazeConfig.setInstance(newConfig)`，而 `isCreative()` 检查仅影响消息提示。应改为 `hasPermissionLevel(2)` 检查。

- [ ] **步骤 1：添加权限检查**

```java
// 修改前（第 262-275 行）：
ServerPlayNetworking.registerGlobalReceiver(UpdateConfigC2SPacket.ID, (payload, context) -> {
    context.server().execute(() -> {
        GazeConfig newConfig = GazeConfig.fromJson(payload.json());
        GazeConfig.setInstance(newConfig);
        for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new SyncConfigS2CPacket(newConfig.toJson()));
        }
        if (context.player() != null && context.player().isCreative()) {
            context.player().sendMessage(Text.literal("§a配置已更新并同步至所有玩家"), false);
        }
    });
});

// 修改后：
ServerPlayNetworking.registerGlobalReceiver(UpdateConfigC2SPacket.ID, (payload, context) -> {
    context.server().execute(() -> {
        if (context.player() != null && !context.player().hasPermissionLevel(2)) {
            context.player().sendMessage(Text.literal("§c你没有权限修改凝视诱导配置"), false);
            return;
        }
        GazeConfig newConfig = GazeConfig.fromJson(payload.json());
        if (newConfig != null) {
            GazeConfig.setInstance(newConfig);
            for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, new SyncConfigS2CPacket(newConfig.toJson()));
            }
            if (context.player() != null) {
                context.player().sendMessage(Text.literal("§a配置已更新并同步至所有玩家"), false);
            }
        }
    });
});
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/features/guidance/Gazeguidance.java
git commit -m "fix: GazeConfig UpdateConfigC2SPacket 添加 OP 权限检查，防止任意客户端覆盖服务端配置"
```

---

### 任务 3：修复镜像视图矩阵位置偏移

**文件：**
- 修改：`src/client/java/com/kuilunfuzhe/monvhua/features/mirror/MirrorViewportRenderer.java:103`

**背景：** 第 86 行通过反射设置了 Camera 的位置 `pos`，但第 103 行构造视图矩阵时 `lookAt(0,0,0,...)` 将相机定在原点。虽然 Camera 对象用于视锥体裁剪，但实际视图变换基于原点，导致镜子渲染的画面来自世界原点而非镜子实际位置。

- [ ] **步骤 1：修改视图矩阵构造**

```java
// 修改前（第 103 行）：
Matrix4f view = new Matrix4f().lookAt(0, 0, 0, (float) forward.x, (float) forward.y, (float) forward.z, 0, 1, 0);

// 修改后：
Matrix4f view = new Matrix4f().lookAt(
    (float) pos.x, (float) pos.y, (float) pos.z,
    (float) (pos.x + forward.x), (float) (pos.y + forward.y), (float) (pos.z + forward.z),
    0, 1, 0
);
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add src/client/java/com/kuilunfuzhe/monvhua/features/mirror/MirrorViewportRenderer.java
git commit -m "fix: 修复镜面视口视图矩阵缺少相机位置平移，导致渲染画面来自世界原点"
```

---

## Batch 2：Null 安全 + 防御性编程（P1）

### 任务 4：BodyPartManager targetDisplay null 检查

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/features/block/body/BodyPartManager.java:205`

**背景：** `world.getEntity(displayUuid)` 返回的 `targetDisplay` 未做 null 检查就直接用于 `instanceof` 和 `.getItemStack()`。虽然 `instanceof null` 返回 false，但后续代码假设 entity 存在。

- [ ] **步骤 1：添加 null 检查**

```java
// 修改前（第 205 行后）：
Entity targetDisplay = world.getEntity(displayUuid);

if (player.isSneaking()) {
    if (targetDisplay instanceof ItemDisplayEntity display) {

// 修改后：
Entity targetDisplay = world.getEntity(displayUuid);
if (targetDisplay == null) return ActionResult.PASS;

if (player.isSneaking()) {
    if (targetDisplay instanceof ItemDisplayEntity display) {
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/features/block/body/BodyPartManager.java
git commit -m "fix: BodyPartManager 添加 targetDisplay null 检查，防止实体不存在时的异常行为"
```

---

### 任务 5：ClientTickHandler client.world null 检查

**文件：**
- 修改：`src/client/java/com/kuilunfuzhe/monvhua/event/ClientTickHandler.java:50`

**背景：** 第 50 行检查了 `client.player == null` 但未检查 `client.world`。玩家有效时 world 仍可能为 null（世界加载中的短暂窗口），导致第 71 行 `client.world.getEntities()` 抛出 NPE。

- [ ] **步骤 1：添加 world null 检查**

```java
// 修改前（第 50-51 行）：
if (client.player == null) return;
if (KeyBindingHandler.configKey.wasPressed() && client.player.isCreative()) {

// 修改后：
if (client.player == null || client.world == null) return;
if (KeyBindingHandler.configKey.wasPressed() && client.player.isCreative()) {
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 3：Commit**

```bash
git add src/client/java/com/kuilunfuzhe/monvhua/event/ClientTickHandler.java
git commit -m "fix: ClientTickHandler 添加 client.world null 检查，防止世界加载中的 NPE"
```

---

### 任务 6：MonvhuaMod EFFECTS Map 防御性访问

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/MonvhuaMod.java:475`

**背景：** `EFFECTS.get(role).get(stage)` 如果 role 不在 EFFECTS key 集合中会 NPE。虽然当前所有枚举值都已注册，但未来新增角色时可能遗漏。

- [ ] **步骤 1：添加防御性检查**

先读取第 475 行附近上下文确认完整逻辑。

```java
// 修改前（第 475 行）：
RegistryEntry<StatusEffect> desired = EFFECTS.get(role).get(stage);

// 修改后：
var roleEffects = EFFECTS.get(role);
if (roleEffects == null) {
    LOGGER.warn("No effects registered for role: {}", role);
    continue;
}
RegistryEntry<StatusEffect> desired = roleEffects.get(stage);
if (desired == null) {
    LOGGER.warn("No effect registered for role: {} stage: {}", role, stage);
    continue;
}
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/MonvhuaMod.java
git commit -m "fix: EFFECTS Map 添加防御性 getOrDefault 检查，防止未注册 role/stage 导致的 NPE"
```

---

## Batch 3：高影响代码去重（P2）

### 任务 7：MirrorDataStore 改为 ConcurrentHashMap

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/command/mirror/MirrorDataStore.java:60`

**背景：** `cache` 是普通 `HashMap`，但多个非同步方法直接操作它。在 Minecraft 单线程模型下实际风险低，但改为 `ConcurrentHashMap` 是最低成本的防御措施。

- [ ] **步骤 1：替换 HashMap 为 ConcurrentHashMap**

```java
// 修改前（第 60 行）：
private static final Map<UUID, PlayerData> cache = new HashMap<>();

// 修改后：
private static final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
```

需要添加 import：
```java
import java.util.concurrent.ConcurrentHashMap;
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/command/mirror/MirrorDataStore.java
git commit -m "fix: MirrorDataStore cache 改为 ConcurrentHashMap 防止并发访问风险"
```

---

### 任务 8：MonvhuaMod 断线/死亡清理逻辑提取公共方法

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/MonvhuaMod.java:530-556`

**背景：** DISCONNECT 回调（第 530-544 行）和 AFTER_DEATH 回调（第 547-556 行）有 5 行完全相同的清理代码。

- [ ] **步骤 1：提取公共清理方法**

在 MonvhuaMod 类中添加私有方法（放在第 556 行之后）：

```java
/**
 * 统一的玩家清理逻辑：移除效果缓存、取消腐化消息队列、清空飘浮状态、
 * 清理镜像数据和隐秘状态。
 */
private static void cleanupPlayerState(ServerPlayerEntity player) {
    UUID uuid = player.getUuid();
    lastEffect.remove(uuid);
    cancelPendingTainted(uuid);
    floatingPlayers.remove(uuid);
    MirrorCommand.cleanup(uuid);
    SecrecyItem.exitSecrecy(player);
}
```

- [ ] **步骤 2：重构 DISCONNECT 回调**

```java
// 修改前（第 530-544 行）：
ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
    ServerPlayerEntity player = handler.getPlayer();
    UUID uuid = player.getUuid();
    lastEffect.remove(uuid);
    cancelPendingTainted(uuid);
    floatingPlayers.remove(uuid);
    CameraWatchManager.stopWatching(player, server);
    Evil_Eyes.forceStopWatching(player, server);
    Evil_Eyes.clearPlayerMarks(uuid);
    VIEWING_MAP.remove(player);
    VIEWING_MAP.values().removeIf(v -> v == player);
    VIEW_MODE_PREFERENCE.remove(uuid);
    MirrorCommand.cleanup(uuid);
    SecrecyItem.exitSecrecy(player);
});

// 修改后：
ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
    ServerPlayerEntity player = handler.getPlayer();
    UUID uuid = player.getUuid();
    cleanupPlayerState(player);
    CameraWatchManager.stopWatching(player, server);
    Evil_Eyes.forceStopWatching(player, server);
    Evil_Eyes.clearPlayerMarks(uuid);
    VIEWING_MAP.remove(player);
    VIEWING_MAP.values().removeIf(v -> v == player);
    VIEW_MODE_PREFERENCE.remove(uuid);
});
```

- [ ] **步骤 3：重构 AFTER_DEATH 回调**

```java
// 修改前（第 547-556 行）：
ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
    if (entity instanceof ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        lastEffect.remove(uuid);
        cancelPendingTainted(uuid);
        floatingPlayers.remove(uuid);
        MirrorCommand.cleanup(uuid);
        SecrecyItem.exitSecrecy(player);
    }
});

// 修改后：
ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
    if (entity instanceof ServerPlayerEntity player) {
        cleanupPlayerState(player);
    }
});
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/MonvhuaMod.java
git commit -m "refactor: 提取 cleanupPlayerState 公共方法，消除断线/死亡清理重复代码"
```

---

### 任务 9：MirrorCommand usesLeft 计算逻辑提取

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/command/mirror/MirrorCommand.java:190-194`

**背景：** 完全相同的 5 行 usesLeft 计算出现在 `toggleViewport()`、`startCharging()`、`tickCharging()` 三个方法中。

- [ ] **步骤 1：提取工具方法**

在 MirrorCommand 类中添加私有方法：

```java
/**
 * 计算当前玩家剩余的镜面观察次数。
 * 如果玩家阶段发生变化，次数重置为当前阶段的最大值。
 */
private static int computeUsesLeft(ServerPlayerEntity player, UUID uuid) {
    int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
    MirrorConfig config = MirrorConfig.getInstance();
    int maxUses = config.getViewCount(stage);
    int storedStage = VIEWPORT_STAGE.getOrDefault(uuid, stage);
    return storedStage == stage
        ? Math.min(VIEWPORT_USES_LEFT.getOrDefault(uuid, maxUses), maxUses)
        : maxUses;
}
```

- [ ] **步骤 2：替换 toggleViewport 中的旧代码**

```java
// 修改前（第 190-194 行）：
int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
MirrorConfig config = MirrorConfig.getInstance();
int maxUses = config.getViewCount(stage);
int storedStage = VIEWPORT_STAGE.getOrDefault(uuid, stage);
int usesLeft = storedStage == stage ? Math.min(VIEWPORT_USES_LEFT.getOrDefault(uuid, maxUses), maxUses) : maxUses;

// 修改后：
int usesLeft = computeUsesLeft(player, uuid);
```

- [ ] **步骤 3：同样替换 startCharging 和 tickCharging 中的旧代码**

找到 `startCharging()`（约第 262-266 行）和 `tickCharging()`（约第 332-334 行）中相同的 5 行计算，替换为 `int usesLeft = computeUsesLeft(player, uuid);`。

- [ ] **步骤 4：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/command/mirror/MirrorCommand.java
git commit -m "refactor: 提取 computeUsesLeft 方法，消除 MirrorCommand 中 3 处重复计算"
```

---

### 任务 10：BodyPartManager 肢体创建逻辑提取

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/features/block/body/BodyPartManager.java`

**背景：** UseItemCallback（第 267-319 行）和 UseBlockCallback（第 322-372 行）中约 50 行肢体展示实体创建代码完全重复，仅放置位置来源不同。

- [ ] **步骤 1：提取公共方法**

在 BodyPartManager 类中添加私有方法：

```java
/**
 * 在指定位置创建肢体展示实体（ItemDisplayEntity + InteractionEntity），
 * 读取物品 NBT 中的背包数据，注册映射关系。
 *
 * @param world        服务端世界
 * @param pos          放置位置
 * @param partStack    肢体物品堆
 * @param player       放置玩家
 * @return 创建的 ItemDisplayEntity 的 UUID，失败返回 null
 */
@Nullable
private static UUID spawnPartDisplay(ServerWorld world, Vec3d pos, ItemStack partStack, ServerPlayerEntity player) {
    // 创建 ItemDisplayEntity
    ItemDisplayEntity display = new ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
    display.setPosition(pos);
    display.setItemStack(partStack.copy());
    world.spawnEntity(display);

    // 读取 NBT 中的背包数据
    DefaultedList<ItemStack> inv = DefaultedList.ofSize(9, ItemStack.EMPTY);
    NbtCompound nbt = partStack.getNbt();
    if (nbt != null && nbt.contains("Items")) {
        NbtList itemsList = nbt.getList("Items", 10);
        for (int i = 0; i < itemsList.size(); i++) {
            NbtCompound tag = itemsList.getCompound(i);
            int slot = tag.getByte("Slot") & 255;
            if (slot < inv.size()) {
                inv.set(slot, ItemStack.fromNbt(tag));
            }
        }
    }
    BODY_PART_DISPLAY_INVENTORIES.put(display.getUuid(), inv);

    // 创建 InteractionEntity
    InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
    interaction.setPosition(pos);
    interaction.setInteractionWidth(0.75f);
    interaction.setInteractionHeight(0.75f);
    world.spawnEntity(interaction);

    // 注册双向映射
    INTERACTION_TO_DISPLAY.put(interaction.getUuid(), display.getUuid());
    DISPLAY_TO_INTERACTION.put(display.getUuid(), interaction.getUuid());

    return display.getUuid();
}
```

- [ ] **步骤 2：重构 UseItemCallback 和 UseBlockCallback**

将两处约 50 行重复代码替换为对 `spawnPartDisplay()` 的调用。

**UseItemCallback 处（约第 267-319 行）替换为：**
```java
Vec3d placePos = player.getPos().add(player.getRotationVec(1.0f).multiply(2.0)).add(0, 1, 0);
UUID displayUuid = spawnPartDisplay((ServerWorld) world, placePos, player.getMainHandStack(), player);
if (displayUuid != null) {
    player.getMainHandStack().decrement(1);
}
// 后续给 PART_OWNERS、PART_ITEMS 赋值的代码保留
```

**UseBlockCallback 处（约第 322-372 行）替换为：**
```java
Vec3d placePos = blockPos.toCenterPos().add(0, 0, 0); // 保持原有的位置计算方式
UUID displayUuid = spawnPartDisplay((ServerWorld) world, placePos, player.getMainHandStack(), player);
if (displayUuid != null) {
    player.getMainHandStack().decrement(1);
}
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 4：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/features/block/body/BodyPartManager.java
git commit -m "refactor: 提取 spawnPartDisplay 方法，消除 BodyPartManager 中 ~50 行重复代码"
```

---

### 任务 11：CarryManager 恢复被搬运实体属性代码提取

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/features/carryentity/CarryManager.java`

**背景：** `releaseCarried`（第 90-94 行）、`tickCarried`（第 146-150 行）、`cleanupForDisconnect`（第 232-236 行）中恢复 `flying`/`allowFlying`/`invulnerable` 的代码完全一致。

- [ ] **步骤 1：提取公共方法**

在 CarryManager 类中添加：

```java
/**
 * 恢复被搬运实体的原始能力状态（飞行、无敌等）。
 */
private static void restoreCarriedAbilities(ServerPlayerEntity carried, boolean originalFlying,
                                             boolean originalAllowFlying, boolean originalInvulnerable) {
    carried.getAbilities().flying = originalFlying;
    carried.getAbilities().allowFlying = originalAllowFlying;
    carried.getAbilities().invulnerable = originalInvulnerable;
    carried.setNoGravity(false);
    carried.sendAbilitiesUpdate();
}
```

- [ ] **步骤 2：替换 3 处重复代码**

在 `releaseCarried()`、`tickCarried()`、`cleanupForDisconnect()` 中，将 4 行恢复代码替换为 `restoreCarriedAbilities(carried, originalFlying, originalAllowFlying, originalInvulnerable);`。

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 4：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/features/carryentity/CarryManager.java
git commit -m "refactor: 提取 restoreCarriedAbilities 方法，消除 CarryManager 中 3 处重复代码"
```

---

## Batch 4：配置类合并 + UI 去重（P3）

### 任务 12：提取 StagedConfig 抽象基类

**文件：**
- 创建：`src/main/java/com/kuilunfuzhe/monvhua/item/config/StagedConfig.java`
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/item/config/GazeConfig.java`
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/item/config/MirrorConfig.java`
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/item/config/SecrecyConfig.java`

**背景：** 三个配置类共享 60% 模板代码（7-stage 数组、单例、JSON 序列化、文件路径、load/save）。

- [ ] **步骤 1：编写 StagedConfig 抽象基类**

```java
package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;

/**
 * 阶段化配置的抽象基类，提供 JSON 序列化、文件持久化和单例模式的通用实现。
 * 子类只需定义 StageConfig 内部类和默认值。
 *
 * @param <T> 具体的 StageConfig 类型
 */
public abstract class StagedConfig<T> {

    protected static final int STAGES = 7;
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Path configPath;

    /** 阶段配置数组，索引 1-7 对应 WitchStage */
    protected final T[] stages;

    @SuppressWarnings("unchecked")
    protected StagedConfig(String filename) {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(filename);
        this.stages = (T[]) new Object[STAGES + 1]; // 索引 0 不使用
    }

    /** 子类实现：创建默认配置 */
    protected abstract T[] createDefaults();

    /** 子类实现：从 JSON 反序列化配置数组 */
    protected abstract T[] fromJsonArray(String json);

    /** 子类实现：将配置数组序列化为 JSON */
    protected abstract String toJsonArray();

    public void load() {
        File file = configPath.toFile();
        if (!file.exists()) {
            resetToDefault();
            save();
            return;
        }
        try (Reader reader = new FileReader(file)) {
            T[] loaded = fromJsonArray(new String(java.nio.file.Files.readAllBytes(file.toPath())));
            if (loaded != null) {
                System.arraycopy(loaded, 0, stages, 0, Math.min(loaded.length, stages.length));
            }
        } catch (IOException e) {
            logger.error("Failed to load config from {}", configPath, e);
            resetToDefault();
        }
    }

    public void save() {
        try {
            File file = configPath.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (Writer writer = new FileWriter(file)) {
                writer.write(toJsonArray());
            }
        } catch (IOException e) {
            logger.error("Failed to save config to {}", configPath, e);
        }
    }

    public void resetToDefault() {
        T[] defaults = createDefaults();
        System.arraycopy(defaults, 0, stages, 0, defaults.length);
    }

    public T getStage(int stage) {
        if (stage < 1 || stage >= stages.length) {
            logger.warn("Invalid stage index: {}, returning stage 1", stage);
            return stages[1];
        }
        return stages[stage];
    }
}
```

- [ ] **步骤 2：重构 GazeConfig 继承 StagedConfig**

将 GazeConfig 中的 `load`、`save`、`toJson`、`fromJson`、`createDefault`、`getStageConfig` 方法替换为继承自 `StagedConfig` 的实现。保留特有的字段和 getter/setter。

- [ ] **步骤 3：同样重构 MirrorConfig 和 SecrecyConfig**

- [ ] **步骤 4：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/item/config/
git commit -m "refactor: 提取 StagedConfig 抽象基类，消除 GazeConfig/MirrorConfig/SecrecyConfig 60% 模板重复"
```

---

## Batch 5：命名规范 + 死代码清理 + 魔法数字（P4）

### 任务 13：删除空类和未使用代码

**文件：**
- 删除：`src/main/java/com/kuilunfuzhe/monvhua/item/Signed_Item.java`
- 删除：`src/client/java/com/kuilunfuzhe/monvhua/event/EntityInteractionHandler.java`
- 修改：`src/client/java/com/kuilunfuzhe/monvhua/ClairvoyanceDataGenerator.java`（删除空方法体或整个文件）
- 修改：`src/client/java/com/kuilunfuzhe/monvhua/features/evil_eyes/Evil_EyesClient.java`

**背景：** 3 个空类/空方法占用代码空间。`Evil_EyesClient.initialize()` 方法体为空。

- [ ] **步骤 1：删除空文件**

```bash
rm src/main/java/com/kuilunfuzhe/monvhua/item/Signed_Item.java
rm src/client/java/com/kuilunfuzhe/monvhua/event/EntityInteractionHandler.java
```

- [ ] **步骤 2：检查 Signed_Item 的引用并清理**

```bash
grep -r "Signed_Item" src/ --include="*.java"
```
如果无引用（空类通常没有），直接删除。如有引用，将引用替换为合适的替代类或移除。

- [ ] **步骤 3：删除 Evil_EyesClient.initialize() 空方法体**

将第 150-152 行的：
```java
public static void initialize() {
    // 所有接收器已移至 ClairvoyanceClient
}
```
改为删除整个方法，同时在 `MonvhuaModClient.onInitializeClient()` 中删除对应的调用。

- [ ] **步骤 4：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 5：Commit**

```bash
git add -A
git commit -m "chore: 删除空类 Signed_Item、EntityInteractionHandler 和 Evil_EyesClient 空初始化方法"
```

---

### 任务 14：修复蛇形命名类名（部分低风险重命名）

**文件（仅修改风险低的）：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/renderer/Font_Render.java` → `FontRender.java`

**背景：** 项目中有多个蛇形命名类名，但重命名会影响多处引用。本次只重命名影响范围小、引用少的类。其余类名（如 `Evil_Eyes`、`Evil_eyesScreen`、`mirrorHUD` 等）因引用较多且涉及包名变更，留待专项重构。

- [ ] **步骤 1：重命名 Font_Render → FontRender**

使用 IDE 的 Rename 重构功能（或手动修改）：
- 重命名文件：`Font_Render.java` → `FontRender.java`
- 修改类声明：`public class Font_Render` → `public class FontRender`
- 更新所有 `import` 引用

- [ ] **步骤 2：查找所有引用并更新**

```bash
grep -r "Font_Render" src/ --include="*.java"
```
逐个更新引用为 `FontRender`。

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 4：Commit**

```bash
git add -A
git commit -m "style: 重命名 Font_Render → FontRender，符合 Java CamelCase 命名规范"
```

---

### 任务 15：提取关键魔法数字为常量

**文件：**
- 修改：`src/main/java/com/kuilunfuzhe/monvhua/features/evil_eyes/Evil_Eyes.java`

**背景：** `30.0`（锚点范围）在文件中出现至少 5 次，`20`（tick/秒）出现多次，`400`（标记过期 tick）等散布在代码中。

- [ ] **步骤 1：在 Evil_Eyes 类顶部添加常量定义**

```java
// 常量定义（添加到类顶部，字段声明区域）
private static final double ANCHOR_RANGE = 30.0;
private static final double ANCHOR_RANGE_SQ = ANCHOR_RANGE * ANCHOR_RANGE;
private static final int TICKS_PER_SECOND = 20;
private static final int MARK_EXPIRE_TICKS = 400;          // 20 秒
private static final int ANCHOR_TIMEOUT_TICKS = 3600;      // 180 秒
private static final int INITIAL_MARK_TICKS = 40;           // 2 秒
private static final int COOLDOWN_TICKS = 100;              // 5 秒
private static final double PARTICLE_BROADCAST_RANGE_SQ = 64.0 * 64.0;
```

- [ ] **步骤 2：替换文件中的魔法数字**

逐个替换文件中所有出现上述数字字面量的位置。

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileJava
```

- [ ] **步骤 4：Commit**

```bash
git add src/main/java/com/kuilunfuzhe/monvhua/features/evil_eyes/Evil_Eyes.java
git commit -m "refactor: 提取 Evil_Eyes 中魔法数字为命名常量，提升可读性"
```

---

## 自检

### 1. 规格覆盖度

| 审查维度 | 发现问题数 | 计划覆盖 | 未覆盖说明 |
|----------|-----------|---------|-----------|
| CRITICAL 安全 | 2 | ✅ 任务 1、2 | — |
| HIGH 安全 | 1 | ⚠️ 未覆盖 | CarryEvents 无敌状态属于设计决策，需用户确认是否修改 |
| 正确性-高 | 3 | ✅ 任务 3、7、8 | — |
| 正确性-中 | 6 | ✅ 任务 4、5、6 | DataTracker、isSecrecyActive、PIXEL_CACHE 留待后续 |
| 性能-P0 | 3 | ⚠️ 未覆盖 | Point 泛滥、Evil_Eyes tick、GPU 纹理拷贝需要较大重构，另开专项 |
| 可维护性-重复代码 | 14 | ✅ 任务 8-12 | CombinedConfigScreen、RaycastHelper 等留待后续 |
| 简洁性 | 19 | 部分覆盖 | CombinedConfigScreen UI 去重留待后续 |
| 命名规范 | 8 | ✅ 任务 14 | 大范围重命名（Evil_Eyes 等）需要专项 |
| 死代码 | 3 | ✅ 任务 13 | — |
| 魔法数字 | 20+ | ✅ 任务 15 | 仅覆盖 Evil_Eyes，其他文件留待后续 |

### 2. 占位符扫描

- [x] 无 "TODO"、"待定"、"后续实现"
- [x] 无 "添加适当的错误处理"
- [x] 每个代码步骤都有实际代码块
- [x] 所有文件路径已验证存在

### 3. 类型一致性

- [x] `cleanupPlayerState(ServerPlayerEntity)` 在任务 8 定义，仅在该任务内使用
- [x] `computeUsesLeft(ServerPlayerEntity, UUID)` 在任务 9 定义，在该任务 3 处使用
- [x] `spawnPartDisplay(ServerWorld, Vec3d, ItemStack, ServerPlayerEntity)` 在任务 10 定义，在该任务 2 处使用
- [x] `restoreCarriedAbilities(...)` 在任务 11 定义，在该任务 3 处使用
- [x] `StagedConfig<T>` 泛型参数在任务 12 中一致

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-05-30-code-quality-optimization.md`。共 **5 批次 15 个任务**。

**推荐执行顺序：**
1. **Batch 1**（3 个任务，~20 行）— 立即执行，风险最低
2. **Batch 2**（3 个任务，~30 行）— 紧接着执行
3. **Batch 3**（5 个任务，~200 行）— 确认 Batch 1-2 通过后再执行
4. **Batch 4-5** — 根据时间和需求决定是否执行

**两种执行方式：**

1. **子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查
2. **内联执行** - 在当前会话中逐任务执行，每批完成后设检查点

请审核计划内容。确认后告诉我从哪一批开始执行。
