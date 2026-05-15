# 魔女化状态效果 (Monvhua Effect)

[![Release](https://img.shields.io/github/v/release/l4nternnn/monvhua-effect?include_prereleases&label=Release)](https://github.com/l4nternnn/monvhua-effect/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-DBB69B)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

一个用于 **Minecraft 1.21.8 Fabric** 的服务端驱动 Mod。它把外部数据包维护的「魔女化进度」计分板，按玩家身上的 **角色 tag** 映射成 14 × 8 = 112 个**纯显示用途**的自定义状态效果，让每个角色在背包效果栏中能看到自己专属的当前阶段图标，跨阶段或换角色时在聊天栏推送描述。

> 状态效果**不附带任何 buff/debuff**，不修改玩家任何属性，只是一个由计分板 + 角色 tag 驱动的可视化标记。

📦 **下载**：前往 [Releases 页面](https://github.com/l4nternnn/monvhua-effect/releases) 获取最新 jar。

---

## 特性

- **14 个角色 × 8 个阶段 = 112 个独立 `MobEffect`**，每个角色一套独立图标和翻译键
- 阶段判定基于名为 `monvhua` 的 `dummy` 计分板数值（取不超过当前数值的最大阈值）
- 角色识别基于玩家 scoreboard tag，14 个角色 id 之一：`ema` / `cero` / `nnk` / `margo` / `leiya` / `milya` / `sherry` / `yalisa` / `noa` / `anan` / `yuki` / `mll` / `coco` / `hanna`
- 服务端每秒（20 tick）轮询一次：
  - 玩家身上无角色 tag → 清掉效果，跳过
  - 跨阶段 / 换角色 → 移除旧效果 → 添加新效果（`INFINITE_DURATION`，无图标抖动）
  - 在聊天栏推送 `◆ 魔女化阶段——<阶段名>` + 完整描述（首次进入游戏不显示「阶段变化」字样）
- 同一效果内反复设分 / 反复打同一 tag **不刷屏、不闪烁**
- 玩家断开 / 死亡时清理服务端缓存，重生后能正确重新触发
- 阶段类别按倾向分别使用 `BENEFICIAL` / `NEUTRAL` / `HARMFUL`，背包列表边框颜色随之变化

## 阶段表

| 阈值 | 内部 ID | 显示名称 | 类别 | 颜色 |
|------|---------|----------|------|------|
| 0  | `sane`        | 神智清醒   | BENEFICIAL | `#55FF55` |
| 10 | `tainted`     | 略染污浊   | BENEFICIAL | `#00AA00` |
| 25 | `light`       | 轻度魔女化 | NEUTRAL    | `#FFFF55` |
| 45 | `medium`      | 中度魔女化 | NEUTRAL    | `#FFAA00` |
| 60 | `high`        | 高度魔女化 | HARMFUL    | `#FF5555` |
| 70 | `severe`      | 重度魔女化 | HARMFUL    | `#AA0000` |
| 80 | `proto_witch` | 准魔女     | HARMFUL    | `#FF55FF` |
| 90 | `witch`       | 魔女       | HARMFUL    | `#AA00AA` |

每个效果的最终注册 id 为 `monvhua:<role>_<stage>`，例如 `monvhua:ema_high`。

## 环境要求

- Minecraft `1.21.8`
- Fabric Loader `>= 0.16.0`
- Fabric API
- Java `21`

## 安装

1. 从 [Releases](https://github.com/l4nternnn/monvhua-effect/releases) 下载 `monvhua-<版本>.jar`，或自行构建
2. 放入服务端 / 客户端的 `mods/` 目录
3. 该 Mod 同时需要装在服务端和客户端（图标贴图由客户端渲染）

## 使用

在服务端或单人世界（开作弊）中创建计分板，并给玩家打上角色 tag。计分板与 tag 都应由你的数据包/命令维护：

```mcfunction
/scoreboard objectives add monvhua dummy 魔女化
/tag @s add ema                            # 角色 = ema
/scoreboard players set @s monvhua 45      # 中度魔女化
```

按 `E` 打开背包，右侧效果栏即可看到当前角色 + 阶段的图标，持续时间显示为 `**:**`（无限）。跨越阈值或换角色时聊天栏会推送描述。

切换角色：

```mcfunction
/tag @s remove ema
/tag @s add cero                           # 下一秒推送「阶段变化」
```

清除角色 tag 后，效果将被自动移除：

```mcfunction
/tag @s remove cero
```

## 构建

```bash
./gradlew build
```

产物位于 `build/libs/`。开发环境运行：

```bash
./gradlew runClient   # 启动测试客户端
./gradlew runServer   # 启动测试服务端
```

## 资源替换

112 张 32×32 的纯色占位图位于：

```
src/main/resources/assets/monvhua/textures/mob_effect/<role>_<stage>.png
```

`<role>` 取 14 个角色 id 之一，`<stage>` 取 8 个阶段 id 之一。直接覆盖同名 PNG 即可替换为正式美术资源，无需改代码。

翻译文件位于 `src/main/resources/assets/monvhua/lang/{zh_cn,en_us}.json`，每个效果一条键，形如：

```json
"effect.monvhua.ema_high": "高度魔女化"
```

## 项目结构

```
src/main/java/com/kuilunfuzhe/monvhua/
 ├── MonvhuaMod.java              主入口：注册 112 个效果、tick 轮询、聊天推送
 ├── WitchRole.java               14 个角色枚举（id + fromPlayer(ServerPlayer)）
 ├── WitchStage.java              8 阶段枚举（阈值、id、名称、描述、颜色、类别）
 └── effect/
      └── DisplayOnlyEffect.java  空逻辑 MobEffect
```

## License

Apache License 2.0，详见 [`LICENSE`](LICENSE)。
