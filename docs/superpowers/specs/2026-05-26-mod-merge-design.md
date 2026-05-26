# 合并设计：monvhua-effect + clairvoyance-template

**日期**: 2026-05-26
**目标**: 将 clairvoyance-template-1.21.8 合并到 monvhua-effect，统一为一个 Minecraft 1.21.8 Fabric mod。

## 关键决策

| 决策 | 选项 |
|------|------|
| Mappings | **Mojang Official**（clairvoyance 的 Yarn 全部转换） |
| Mod ID | **monvhua**（沿用） |
| 包名 | **com.kuilunfuzhe.monvhua** |
| 合并范围 | **全部功能**（千里眼、视线诱导、身体部件、镜像、搬运、背包查看、锚点） |
| 合并后版本 | **0.5.0-Beta** |

## 构建配置

以 monvhua 配置为基础：
- Minecraft 1.21.8, Fabric Loader 0.19.2, Loom 1.16-SNAPSHOT
- Fabric API 0.136.1+1.21.8
- Mojang Official Mappings, Java 21
- `splitEnvironmentSourceSets()` 保留

## 包结构

```
com.kuilunfuzhe.monvhua/
├── MonvhuaMod.java              ← 统一入口
├── MonvhuaModClient.java        ← 客户端入口（原 ClairvoyanceClient）
├── WitchRole.java
├── WitchStage.java
├── effect/
│   └── DisplayOnlyEffect.java
├── config/                      ← GlobalConfigManager, GazeConfig
├── network/                     ← 所有网络包 + ModNetworking + ModPackets
├── clairvoyance/                ← EvilEyes, CameraWatchManager, ViewingModeBlocker
│   ├── mark/
│   └── server/
├── gazeguidance/                ← Gazeguidance + 网络包
├── bodypart/                    ← 身体部件：方块、BE、Manager
│   ├── block/
│   └── screen/
├── mirror/                      ← 镜像系统
├── carry/                       ← CarryEvents, CarryManager
├── item/                        ← 物品注册
├── command/                     ← 所有命令
├── screen/                      ← 屏幕处理器
├── entity/                      ← ModEntities, ModBlockEntities
├── mixin/                       ← 服务端 mixins
└── util/                        ← ImplementedInventory, RaycastHelper
```

客户端 (`src/client/java/`) 保持对称结构。

## 入口点合并

`MonvhuaMod.onInitialize()`:
1. 注册 112 个 DisplayOnlyEffect（现有）
2. 注册网络包（clairvoyance）
3. 注册配置管理器
4. 注册命令
5. 注册物品、方块、方块实体
6. 注册屏幕处理器
7. 初始化千里眼、视线诱导、镜像、身体部件等系统
8. 注册事件（tick、死亡、断线等）

## Mappings 转换

clairvoyance 所有 Yarn 名转为 Mojang Official：
- 类名：`ServerPlayerEntity` → `ServerPlayer`, `MinecraftClient` → `Minecraft`
- 方法/字段名按 Mojang 名替换
- Mixin `@Mixin` 和 `@At` 目标同步更新
- 策略：复制代码 → 系统性转换 → `./gradlew compileJava` 迭代修复

## 资源合并

所有 `assets/clairvoyance/` 迁移到 `assets/monvhua/`：
- 翻译文件合并（zh_cn.json, en_us.json）
- 纹理迁移（block/, item/, gui/, local_skin/）
- 模型迁移（blockstates/, models/block/, models/item/, models/geo/）
- fabric.mod.json 合并为单一入口
- mixin 配置重命名为 `monvhua.mixins.json` 和 `monvhua.client.mixins.json`
- 所有 `ResourceLocation("clairvoyance", ...)` → `ResourceLocation("monvhua", ...)`

## 风险点

1. **Mappings 转换遗漏** — clairvoyance 代码量大，可能漏改某些引用，需通过编译迭代修复
2. **GeckoLib 依赖** — clairvoyance 使用 Geo 模型，需确认是否依赖 GeckoLib
3. **网络包协议兼容** — 合并后 mod 版本变化，需确认不存在协议冲突
4. **事件冲突** — 两个 mod 都注册了 tick 事件，需确保不互相干扰
