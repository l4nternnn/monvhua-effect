# 魔女化 (Monvhua)

[![Release](https://img.shields.io/github/v/release/l4nternnn/monvhua-effect?include_prereleases&label=Release)](https://github.com/l4nternnn/monvhua-effect/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-DBB69B)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

魔女化是一个 Minecraft 1.21.8 Fabric 模组，围绕 `monvhua` 计分板驱动的魔女化阶段，提供状态效果展示、千里眼、模仿、视线诱导、身体部件、镜像、搬运、漂浮、动作、重力和绘画等系统。模组包含服务端逻辑和客户端渲染，客户端与服务端都需要安装。

## 下载

从 [Releases](https://github.com/l4nternnn/monvhua-effect/releases) 下载 `monvhua-<版本>.jar`，放入客户端和服务端的 `mods/` 目录。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | `1.21.8` |
| Fabric Loader | `>= 0.16.0` |
| Fabric API | `0.136.1+1.21.8` |
| Java | `21` |
| GeckoLib | `5.2.2` |
| Mod 版本 | `0.7.1-Beta` |

## 功能总览

| 系统 | 说明 |
| --- | --- |
| 魔女化状态效果 | 14 个角色、8 个阶段，共 112 个显示用状态效果。每 20 tick 根据 `monvhua` 计分板与角色 tag 切换效果。 |
| 千里眼 | 标记实体、锚点自动标记、观看目标、legacy/modern 双观看模式和阶段限制。 |
| 模仿 | 角色扮演、聊天显示名覆盖、沉默能力和冷却管理。 |
| 视线诱导 | 用魔法棒标记目标并引导目标视线，带能量消耗、恢复和 HUD 显示。 |
| 身体部件 | 玩家死亡掉落身体部件，可拾取、放置、合并、替换皮肤，并支持骨架部件与姿态编辑。 |
| 镜像 | 设置镜面发生点、映射点和触发半径，使用昔今之镜开启镜像视角。 |
| 搬运 | 潜行右键抱起玩家或生物，被抱者可挣脱，客户端包含抱姿、相机和渲染兼容逻辑。 |
| 漂浮 | 双击跳跃漂浮，满魔女状态可创造飞行，并显示能量条。 |
| 动作系统 | JSON 驱动动作、触发器和时间线，可在动作编辑器中调整。 |
| 重力 | 重力魔杖与倒置区域，支持倒置方块支撑、相机/视角修正和区域边界渲染。 |
| 绘画与涂鸦 | 覆盖画笔、橡皮、画纸和油漆桶，可在方块面与身体模型上绘制，也可导入图片生成画纸。 |
| 黑洞 | 自定义着色器方块，提供引力透镜视觉效果。 |
| 保密 | 隐身和客户端音量衰减。 |

## 魔女化计分板

创建 `monvhua` 计分板后，玩家分数决定魔女化阶段，玩家 tag 决定角色。示例：

```mcfunction
/scoreboard objectives add monvhua dummy 魔女化
/tag @s add ema
/scoreboard players set @s monvhua 45
```

角色 tag：

```text
ema, cero, nnk, mago, leiya, milya, sherry, yalisa, noa, anan, yuki, mll, coco, hanna
```

阶段阈值：

| 阈值 | ID | 显示名称 | 类别 | 颜色 |
| --- | --- | --- | --- | --- |
| 0 | `1` | 神智清醒 | BENEFICIAL | `#55FF55` |
| 10 | `2` | 略染污浊 | BENEFICIAL | `#00AA00` |
| 25 | `3` | 轻度魔女化 | NEUTRAL | `#FFFF55` |
| 45 | `4` | 中度魔女化 | NEUTRAL | `#FFAA00` |
| 60 | `5` | 高度魔女化 | HARMFUL | `#FF5555` |
| 70 | `6` | 重度魔女化 | HARMFUL | `#AA0000` |
| 80 | `7` | 准魔女 | HARMFUL | `#FF55FF` |
| 90 | `8` | 魔女 | HARMFUL | `#AA00AA` |

状态效果注册 ID 为 `monvhua:<role>_<stage>`，例如 `monvhua:ema_5`。

## 常用物品

| 物品 | ID | 用途 |
| --- | --- | --- |
| 千里眼 | `monvhua:clairvoyance_item` | 打开标记列表与千里眼管理界面。 |
| 模仿之书 | `monvhua:imitate_book` | 打开模仿相关界面。 |
| 视线诱导魔法棒 | `monvhua:magic_stick` | 标记目标并发动视线诱导。 |
| 昔今之镜 | `monvhua:mirror_of_then_and_now` | 切换镜像视角。 |
| 重力魔杖 | `monvhua:gravity_wand` | 选择并施放重力魔法。 |
| 覆盖画笔 | `monvhua:paint_brush` | 在方块面或支持的模型表面绘制。 |
| 橡皮 | `monvhua:eraser` | 擦除已绘制内容。 |
| 画纸 | `monvhua:paint_paper` | 保存、释放或承载导入图片生成的绘画数据。 |
| 油漆桶 | `monvhua:paint_bucket` | 绘画系统使用的方块和物品。 |
| 黑洞 | `monvhua:block_hole` | 放置黑洞视觉方块。 |

## 常用按键

| 按键 | 功能 |
| --- | --- |
| `V` | 标记实体。 |
| `U` | 打开配置菜单，仅创造模式可用。 |
| `B` | 打开身体姿态编辑器。 |
| `P` | 打开身体姿态世界预览，仅创造模式可用。 |
| `J` | 打开动作编辑器，仅创造模式可用。 |

## 命令速查

下表覆盖 `src/main/java/com/kuilunfuzhe/monvhua/command` 中注册的指令。`OP` 表示需要 2 级权限；未标注 `OP` 的指令普通玩家可执行，但多数仍要求执行者是玩家实体。

### 千里眼与观看

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/clairvoyance clearanchors_清除千里眼锚点` | 玩家 | 清除执行者自己的千里眼锚点。 |
| `/clairvoyance clearanchors_清除千里眼锚点 <player>` | OP | 清除指定在线玩家的千里眼锚点。 |
| `/clairvoyance clearanchors_清除千里眼锚点 all` | OP | 清除所有千里眼锚点，移除对应盔甲架并触发清除效果。 |
| `/clairvoyance viewmode_切换相机系统` | 玩家 | 查看当前千里眼观看模式。 |
| `/clairvoyance viewmode_切换相机系统 legacy——旧版客户端` | 玩家 | 切换到旧版客户端盔甲架观看模式，并停止当前观看。 |
| `/clairvoyance viewmode_切换相机系统 modern——新版服务端` | 玩家 | 切换到新版服务端 CameraWatch 观看模式，并停止当前观看。 |
| `/clairvoyance viewmode_切换相机系统 viewport` | 玩家 | 切换到 Viewport 界面预览观看模式，并停止当前观看。 |
| `/clairvoyance viewmode` | 玩家 | 查看当前千里眼观看模式。 |
| `/clairvoyance viewmode <legacy\|modern\|viewport>` | 玩家 | 使用英文短参数切换千里眼观看模式。 |
| `/watch` | 玩家 | 未观看时观看准星 50 格内命中的实体；已观看时停止观看。 |
| `/watch <target>` | 玩家 | 按玩家名或实体自定义名查找并开始观看目标实体。 |
| `/watch angle <yaw> <pitch> <distance>` | 玩家 | 设置服务端观看相机偏移，`distance` 范围为 0.5 到 10。 |
| `/watch angle <yaw> <pitch> reset` | 玩家 | 重置观看相机偏移；当前注册路径仍需要 `yaw`、`pitch` 两个占位参数。 |

### 身体部件与骨骼

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/clairvoyance-肢体\|获取 <target> <source> all [slim]` | OP | 将来源玩家的头、躯干、四肢物品给予目标玩家；`all` 还会额外给予原版玩家头颅。 |
| `/clairvoyance-肢体\|获取 <target> <source> <part> [slim]` | OP | 将来源玩家的指定肢体物品给予目标玩家。`part` 可为 `head`、`torso`、`left_arm`、`right_arm`、`left_leg`、`right_leg`；玩家来源的 `head` 不带 `slim` 分支。 |
| `/clairvoyance-肢体\|获取 <target> localskin <skinName> all [slim]` | OP | 使用内置皮肤生成全套肢体物品并给予目标玩家。 |
| `/clairvoyance-肢体\|获取 <target> localskin <skinName> <part> [slim]` | OP | 使用内置皮肤生成指定肢体物品并给予目标玩家，`skinName` 会从内置皮肤列表补全。 |
| `/clairvoyance-肢体\|替换 localskin-内置 <skinName> [slim]` | OP | 将执行者周围 3 格内的肢体展示实体替换为指定内置皮肤。 |
| `/clairvoyance-肢体\|替换 player-玩家 <playerName> [slim]` | OP | 将执行者周围 3 格内的肢体展示实体替换为指定在线玩家皮肤。 |
| `/clairvoyance-肢体\|替换 split-分离` | OP | 将执行者附近的整体肢体展示实体拆分成单独的头、躯干和四肢展示实体。 |
| `/monvhua-skeletal-pose <pos> <pitch> <yaw> <roll>` | OP | 设置指定骨骼肢体方块的关节姿态，三个角度范围均为 -180 到 180。 |

### 镜像

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/clairvoyance-mirror set <slot> hsPos-设定点 <pos>` | OP | 为执行者设置镜面槽位 `1` 或 `2` 的触发点。 |
| `/clairvoyance-mirror set <slot> mapPos-映射点 <pos>` | OP | 为执行者设置镜面槽位 `1` 或 `2` 的映射点。 |
| `/clairvoyance-mirror set <slot> radius-半径 <radius>` | OP | 设置镜面触发半径，范围为 1 到 200。 |
| `/clairvoyance-mirror remove-移除 <slot>` | OP | 清除执行者指定镜面槽位。 |
| `/clairvoyance-mirror list-列出所有` | OP | 列出执行者两个镜面槽位、镜像视图状态和剩余使用次数。 |
| `/clairvoyance-mirror clear-清除` | OP | 清除执行者的所有镜面配置。 |

### 动作（已废弃，但代码未注释）

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/monvhua-action\|动作 reload\|重载` | OP | 重新加载动作 JSON 配置。 |
| `/monvhua-action\|动作 list\|列表` | OP | 列出已注册动作、启用状态和动作类型。 |
| `/monvhua-action\|动作 trigger\|触发 <id>` | OP | 对执行者手动触发指定动作；控制台执行时需要显式指定玩家。 |
| `/monvhua-action\|动作 trigger\|触发 <id> <player>` | OP | 对指定玩家手动触发指定动作。 |
| `/monvhua-action\|动作 enable\|启用 <id>` | OP | 在配置中启用指定动作。 |
| `/monvhua-action\|动作 disable\|禁用 <id>` | OP | 在配置中禁用指定动作。 |

### 重力

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/monvhua-gravity area create <radius> <time>` | OP | 以执行者脚下方块为中心创建区域重力，半径范围 1 到 256，高度使用默认值。 |
| `/monvhua-gravity area create <radius> <height> <time>` | OP | 创建指定半径和高度的区域重力，高度范围 1 到 256。 |
| `/monvhua-gravity area clear nearest` | OP | 清除当前维度中距离执行者最近的区域重力。 |
| `/monvhua-gravity area clear all` | OP | 清除当前维度中的所有区域重力。 |

`time` 使用秒数，或使用 `wuxian` 表示无限时间。

### 绘画与涂鸦

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/monvhua-graffiti folder` | OP | 创建并显示游戏目录下的 `graffiti/` 图片导入文件夹。 |
| `/monvhua-graffiti import <file>` | OP | 从 `graffiti/` 文件夹异步导入图片，按画纸网格拆分后给予执行者一个或多个画纸物品。 |
| `/monvhua-graffiti import_scaled <scale> <file>` | OP | 按指定倍率导入图片，`scale` 范围为 0.05 到 8.0；最多生成 25x25 张画纸。 |
| `/monvhua-graffiti clear grid <radius-半径> [center]` | OP | 按方块坐标立方体半径清除绘画面，半径范围 0 到 2048；未指定中心时使用执行者位置。 |
| `/monvhua-graffiti clear block-方块 <radius-半径> [center]` | OP | 与 `grid` 相同，是方块清除路径的中文别名。 |
| `/monvhua-graffiti clear chunk <radius> [center]` | OP | 按区块半径清除绘画面，半径范围 0 到 128；未指定中心时使用执行者位置所在区块。 |

### 其他

| 指令 | 权限 | 效果 |
| --- | --- | --- |
| `/placemannequin` | 玩家 | 在执行者当前位置生成一个测试模型实体，用于验证模型渲染效果。 |

## 配置与数据

配置文件位于 Fabric 游戏目录的 `config/monvhua/` 下，使用 Gson pretty-print 保存。主要配置包括：

| 文件 | 内容 |
| --- | --- |
| `clairvoyance_global.json` | 千里眼各阶段限制。 |
| `actions.json` | 动作定义、触发器和时间线。 |
| `imitate.json` | 模仿系统参数。 |
| `mirror.json` | 镜像系统参数。 |

绘画导入图片放在游戏目录的 `graffiti/` 文件夹中。

## 开发

常用命令：

```bash
./gradlew build
./gradlew compileJava
./gradlew runClient
./gradlew runServer
```

源码结构：

```text
src/main/java/com/kuilunfuzhe/monvhua/
  MonvhuaMod.java              服务端与通用入口
  WitchRole.java               14 个角色枚举
  WitchStage.java              8 个阶段枚举
  command/                     千里眼、肢体、镜像、动作、重力、涂鸦等命令
  config/                      JSON 配置管理
  effect/                      纯显示状态效果
  features/                    各功能系统
  item/                        物品和方块注册
  network/                     Fabric CustomPayload 网络包
  mixin/                       服务端和通用 mixin

src/client/java/com/kuilunfuzhe/monvhua/
  MonvhuaModClient.java        客户端入口
  event/                       按键、tick、世界渲染处理
  features/                    客户端功能状态与渲染
  gui/                         配置、动作、身体姿态等界面
  mixin/                       客户端渲染、相机、输入和兼容 mixin
  model/                       身体部件模型数据
  renderer/                    方块实体、身体部件、骨架等渲染器
```

网络包统一使用 Fabric `CustomPayload` + `PayloadTypeRegistry`，优先保持 `record` + `PacketCodec` 模式。客户端发包优先使用 `SafeClientNetworking.send()`。

## 资源替换

状态效果图标：

```text
src/main/resources/assets/monvhua/textures/mob_effect/<role>_<stage>.png
```

翻译文件：

```text
src/main/resources/assets/monvhua/lang/zh_cn.json
src/main/resources/assets/monvhua/lang/en_us.json
```

内置皮肤：

```text
src/main/resources/assets/monvhua/textures/local_skin/
```

## License

本项目使用 [GNU General Public License v3.0](LICENSE)。
