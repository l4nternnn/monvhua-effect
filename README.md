# 魔女化 (Monvhua)

[![Release](https://img.shields.io/github/v/release/l4nternnn/monvhua-effect?include_prereleases&label=Release)](https://github.com/l4nternnn/monvhua-effect/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-DBB69B)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Minecraft 1.21.8 Fabric** 综合 Mod，由「魔女化状态效果」与「千里眼」两个 Mod 合并而成。基于 `monvhua` 计分板驱动，提供魔女化阶段可视化 + 实体标记/视角追踪/视线诱导/身体部件/镜像/搬运等全套辅助功能。

📦 **下载**：[Releases 页面](https://github.com/l4nternnn/monvhua-effect/releases)

---

## 功能总览

### 一、魔女化状态效果系统

| 特性 | 说明 |
|------|------|
| 14 角色 × 8 阶段 | 112 个独立状态效果，每个角色一套图标和翻译 |
| 计分板驱动 | 基于 `monvhua` dummy 计分板数值判定阶段 |
| 角色识别 | 玩家 scoreboard tag：`ema` `cero` `nnk` `mago` `leiya` `milya` `sherry` `yalisa` `noa` `anan` `yuki` `mll` `coco` `hanna` |
| Tainted 三段叙事 | ≥10 分时触发，三段文案随机排列，3-5 分钟内陆续推送 |
| 全角色专属文案 | 14 个角色全部实装 tainted 三段变体 + 6 阶段定制对话 |
| 飞行能力 | 同时持有 `Floating` + `MonvhuaFull` tag 获得创造飞行 + 免疫摔落 |

### 二、千里眼（实体标记 / 视角追踪）

- **实体标记**：按 G 键标记看向的实体，或通过锚点（鹦鹉盔甲架）自动标记
- **视角追踪**：选择标记实体后进入观看模式，视角跟随目标移动
- **7 阶段配置**：基于 `monvhua` 分数（0-100）决定标记数、每日次数等限制
- **双模式**：legacy（客户端盔甲架相机）/ modern（服务端 CameraWatch）
- **GUI 管理**：右键千里眼物品打开标记列表，U 键打开配置面板

### 三、视线诱导

- **能量聚焦**：标记实体后长按右键激活，被标记实体看向焦点位置
- **能量系统**：消耗/回复机制，屏幕 HUD 显示能量条
- **自定义粒子环**：三叶草形状粒子效果

### 四、身体部件系统

- **死亡掉落**：持有 `dead_part` / `dead_body` tag 的玩家死亡后掉落身体部件
- **部件交互**：右键拾取或打开死亡背包（3×3）
- **合并/替换**：命令合并散落部件为完整身体，支持内置皮肤替换
- **6 种方块实体**：头部/躯干/左臂/右臂/左腿/右腿，带自定义 Geo 模型

### 五、镜像系统

- 两个可设置的镜位点，开启后屏幕右上/右下显示第三人称镜像视口
- `/clairvoyance-mirror set <1|2> [x y z]` 设置镜位，右键昔今之镜物品切换

### 六、搬运实体

- 潜行 + 右键抱起玩家/生物，被抱者可挣扎挣脱
- 负重减速（持身体部件时），掉落伤害分摊

### 七、其他功能

- **查看背包**：持有 `kaibao` tag 可打开附近玩家背包
- **锚点系统**：锚点自动标记看向它的实体，180 秒过期
- **测试假人**：`/placemannequin` 放置测试用实体

---

## 阶段表

| 阈值 | ID | 显示名称 | 类别 | 颜色 |
|------|-----|----------|------|------|
| 0  | `1` | 神智清醒   | BENEFICIAL | `#55FF55` |
| 10 | `2` | 略染污浊   | BENEFICIAL | `#00AA00` |
| 25 | `3` | 轻度魔女化 | NEUTRAL    | `#FFFF55` |
| 45 | `4` | 中度魔女化 | NEUTRAL    | `#FFAA00` |
| 60 | `5` | 高度魔女化 | HARMFUL    | `#FF5555` |
| 70 | `6` | 重度魔女化 | HARMFUL    | `#AA0000` |
| 80 | `7` | 准魔女     | HARMFUL    | `#FF55FF` |
| 90 | `8` | 魔女       | HARMFUL    | `#AA00AA` |

每个效果注册 ID：`monvhua:<role>_<stage>`，例如 `monvhua:ema_5`。

---

## 命令参考

### 魔女化系统

```mcfunction
/scoreboard objectives add monvhua dummy 魔女化
/tag @s add ema                        # 设置角色为 EMA
/scoreboard players set @s monvhua 45  # 中度魔女化
/tag @s remove ema                     # 清除角色（移除效果）
```

### 千里眼

| 命令 | 说明 |
|------|------|
| `/clairvoyance clearanchors_清除千里眼锚点 [玩家]` | 清除锚点 |
| `/clairvoyance viewmode <legacy\|modern>` | 切换观看模式 |
| `/watch` | 观看指定实体 |
| `/watch angle <偏航> <俯仰> <距离>` | 设置相机偏移 |

### 身体部件

| 命令 | 说明 |
|------|------|
| `/clairvoyance-肢体\|获取 <角色名> [内置皮肤名]` | 获取身体部件 |
| `/clairvoyance-肢体\|替换 localskin-内置 <角色名> <皮肤名>` | 替换皮肤 |
| `/clairvoyance-肢体\|合并` | 合并散落部件 |

### 镜像

| 命令 | 说明 |
|------|------|
| `/mirror set <1\|2> [x y z]` | 设置镜位（不填坐标则用玩家位置） |
| `/mirror remove <1\|2>` | 清除镜位 |
| `/mirror list` | 列出所有镜位 |
| `/mirror clear` | 清除全部 |

### 其他

| 命令 | 说明 |
|------|------|
| `/placemannequin` | 放置测试假人 |

---

## 物品列表

| 物品 | 获取方式 | 用途 |
|------|---------|------|
| 千里眼 | `/give @s monvhua:clairvoyance_item` | 右键打开标记列表 |
| 视线诱导棒 | `/give @s monvhua:magic_stick` | G 键标记 + 长按右键诱导 |
| 昔今之镜 | `/give @s monvhua:mirror_of_then_and_now` | 右键切换镜像视图 |

---

## 环境要求

- Minecraft `1.21.8`
- Fabric Loader `>= 0.16.0`
- Fabric API
- Java `21`

## 安装

1. 从 [Releases](https://github.com/l4nternnn/monvhua-effect/releases) 下载 `monvhua-<版本>.jar`
2. 放入服务端 / 客户端的 `mods/` 目录
3. 服务端和客户端**都需要安装**（图标、模型、GUI 由客户端渲染）

## 构建

```bash
./gradlew build          # 构建 jar → build/libs/
./gradlew runClient      # 启动测试客户端
./gradlew runServer      # 启动测试服务端
```

## 项目结构

```
src/main/java/com/kuilunfuzhe/monvhua/
├── MonvhuaMod.java           主入口：效果注册 + 全部系统初始化
├── WitchRole.java            14 个角色枚举（tag/文案/对话）
├── WitchStage.java           8 阶段枚举（阈值/名称/颜色/类别）
├── effect/                   DisplayOnlyEffect（纯显示状态效果）
├── config/                   全局/诱导配置管理
├── network/                  36+ C2S/S2C 网络包
├── clairvoyance/             千里眼核心（标记/观看/锚点）
├── gazeguidance/             视线诱导系统
├── bodypart/                 身体部件方块/BE/管理器
├── mirror/                   镜像系统（服务端命令）
├── carry/                    搬运实体系统
├── item/                     物品注册
├── command/                  所有命令
├── screen/                   屏幕处理器
├── entity/                   方块实体/实体注册
├── mixin/                    服务端 Mixin
└── util/                     工具类

src/client/java/.../monvhua/
├── MonvhuaModClient.java     客户端入口
├── features/                 客户端功能（千里眼/诱导/镜像渲染）
├── gui/                      GUI 界面（配置/标记列表/背包）
├── mixin/                    客户端 Mixin
├── model/                    身体部件模型
└── renderer/                 身体部件渲染器
```

## 资源替换

112 张 32×32 状态效果图标：
```
src/main/resources/assets/monvhua/textures/mob_effect/<role>_<stage>.png
```
直接覆盖同名 PNG 即可替换为正式美术资源。

翻译文件：`assets/monvhua/lang/{zh_cn,en_us}.json`

内置皮肤：`assets/monvhua/textures/local_skin/`（15 个角色皮肤 PNG）

## License

GNU General Public License v3.0，详见 [`LICENSE`](LICENSE)。
