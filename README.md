# 魔女化状态效果 (Monvhua Effect)

一个用于 **Minecraft 1.21.8 Fabric** 的服务端驱动 Mod。它把外部数据包维护的「魔女化进度」计分板，映射成 8 个**纯显示用途**的自定义状态效果，让玩家在背包效果栏中能看到自己当前所处的阶段，同时在跨阶段时通过聊天栏推送描述。

> 状态效果**不附带任何 buff/debuff**，不修改玩家任何属性，只是一个由计分板驱动的可视化标记。

---

## 特性

- 8 个独立 `MobEffect`：神智清醒 → 略染污浊 → 轻度魔女化 → 中度魔女化 → 高度魔女化 → 重度魔女化 → 准魔女 → 魔女
- 阶段判定基于名为 `monvhua` 的 `dummy` 计分板数值（取不超过当前数值的最大阈值）
- 服务端每秒（20 tick）轮询一次，跨阈值时：
  - 移除旧效果 → 添加新效果（`INFINITE_DURATION`，无图标抖动）
  - 在聊天栏推送阶段名 + 完整描述（首次进入游戏不显示「阶段变化」字样）
- 同一阶段内反复设分**不刷屏、不闪烁**
- 玩家断开 / 死亡时清理服务端缓存，使重生后能正确重新触发
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

## 环境要求

- Minecraft `1.21.8`
- Fabric Loader `>= 0.16.0`
- Fabric API
- Java `21`

## 安装

1. 从 [Releases](https://github.com/l4nternnn/monvhua-effect/releases) 下载或自行构建 jar
2. 放入服务端 / 客户端的 `mods/` 目录
3. 该 Mod 同时需要装在服务端和客户端（图标贴图由客户端渲染）

## 使用

在服务端或单人世界（开作弊）中创建计分板，由你的数据包或命令维护数值：

```mcfunction
/scoreboard objectives add monvhua dummy 魔女化
/scoreboard players set @s monvhua 0    # 神智清醒
/scoreboard players set @s monvhua 45   # 中度魔女化
/scoreboard players set @s monvhua 90   # 魔女
```

按 `E` 打开背包，右侧效果栏即可看到当前阶段的图标，持续时间显示为 `**:**`（无限）。跨越阈值时聊天栏会推送阶段描述。

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

8 张 32×32 的纯色占位图位于：

```
src/main/resources/assets/monvhua/textures/mob_effect/{sane,tainted,light,medium,high,severe,proto_witch,witch}.png
```

直接覆盖同名 PNG 即可替换为正式美术资源，无需改代码。

翻译文件位于 `src/main/resources/assets/monvhua/lang/{zh_cn,en_us}.json`。

## 项目结构

```
src/main/java/com/kuilunfuzhe/monvhua/
 ├── MonvhuaMod.java              主入口：注册效果、tick 轮询、聊天推送
 ├── WitchStage.java              8 阶段枚举（阈值、id、名称、描述、颜色、类别）
 └── effect/
      └── DisplayOnlyEffect.java  空逻辑 MobEffect
```

## License

Apache License 2.0，详见 [`LICENSE`](LICENSE)。
