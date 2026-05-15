# AGENTS.md

本仓库的 AI 编程代理（Claude Code、Codex、Cursor 等）在改动代码前请先读完本文。
人类贡献者也欢迎阅读。

---

## 项目一句话

Minecraft **1.21.8 Fabric** mod，把外部数据包维护的 `monvhua` 计分板映射成 **8 个纯显示用途的 MobEffect**，让玩家在背包效果栏看到当前的「魔女化阶段」，并在跨阶段时聊天栏推送描述。

「纯显示」是底线 —— **不要给效果加任何 buff / debuff / 属性修改**。

---

## 环境

- **Java**：21（`build.gradle` 锁死了 release 21）
- **Mappings**：**Mojang Official Mappings**（不是 Yarn）。所有类名按 Mojmap 来：
  `MobEffect` / `MobEffectCategory` / `MobEffectInstance` / `Holder<MobEffect>` /
  `ResourceLocation` / `BuiltInRegistries.MOB_EFFECT` / `ServerPlayer` /
  `Component` / `ChatFormatting` / `Scoreboard` / `Objective` / `ReadOnlyScoreInfo`
- **Loader**：Fabric Loader ≥ 0.16，Fabric API 必装
- **源集**：`splitEnvironmentSourceSets()`，但目前 `src/client/` 是空的，**这次需求不要往里加 Java 文件**。状态效果显示和聊天消息全部走原版机制自动同步。

## 构建 / 验证

```bash
./gradlew build         # 全量构建（包含 compileJava + remapJar）
./gradlew compileJava   # 仅快速类型检查
./gradlew runClient     # 起测试客户端
```

改完代码至少要让 `./gradlew build` 跑通再提交。

---

## 代码地图

```
src/main/java/com/kuilunfuzhe/monvhua/
 ├── MonvhuaMod.java              ModInitializer：注册 8 个效果、ServerTickEvents 轮询、聊天推送、断开/死亡清缓存
 ├── WitchStage.java              8 阶段枚举（阈值、id、名称、描述、颜色、类别、聊天色）+ fromScore()
 └── effect/
      └── DisplayOnlyEffect.java  空逻辑 MobEffect（继承自 MobEffect，不重写任何 tick 方法）

src/main/resources/
 ├── fabric.mod.json
 └── assets/monvhua/
      ├── lang/{zh_cn,en_us}.json
      └── textures/mob_effect/<id>.png   (8 张 32×32)
```

---

## 不可变量（请勿打破）

1. **效果零逻辑**：`DisplayOnlyEffect` 不要重写 `applyEffectTick` / `shouldApplyEffectTickThisTick`。如果必须重写，让它直接 `return true` 或干脆不写。
2. **持续时间永远是 `MobEffectInstance.INFINITE_DURATION`**。不要改成有限时长 —— 否则背包里会看到秒数跳动。
3. **`showParticles = false`、`showIcon = true`**。粒子开了会满屏，图标关了就看不见。
4. **仅在跨阶段时 `addEffect`**。同阶段什么都不做（不刷新效果、不发消息）。这是防闪烁和防刷屏的关键。
5. **缓存语义**：`lastStage` 是 `Map<UUID, WitchStage>`。
   - 玩家断开 → 移除
   - 玩家死亡 → 移除（让下一秒的 tick 视为"首次进入"重新添加效果并推送描述）
   - 首次进入 / 缓存被清空时：**只发图标 + 描述两行，不发「阶段变化」**
6. **轮询频率**：每 20 tick 一次，使用 `ServerTickEvents.END_SERVER_TICK` + 内部计数器。不要换成 `START_WORLD_TICK` 之类（会按维度多次执行）。
7. **计分板取数**：`scoreboard.getObjective("monvhua")`，objective 为 null 直接 `continue`，**不要自动创建 objective**（由数据包负责）。`getPlayerScoreInfo` 返回 null 视为 0。

---

## 阶段定义改动指南

如果要调整阈值 / 名称 / 描述 / 颜色：

- 改 `WitchStage.java` 枚举常量
- 同步改 `assets/monvhua/lang/{zh_cn,en_us}.json` 翻译键 `effect.monvhua.<id>`
- 如果改了 `id`，同步重命名 `textures/mob_effect/<id>.png`

新增 / 删除阶段时务必同时改：枚举、翻译、纹理。**不要**改 `EFFECTS.put` 那段循环逻辑 —— 它已经覆盖所有 `values()`。

`fromScore` 算法：取**不超过**当前数值的最大阈值阶段。`SANE.threshold` 必须保持为最小（当前为 0），否则负分玩家会拿不到任何阶段。

---

## 不要做的事

- ❌ 加 HUD / 自定义渲染 / Mixin / 网络包 / 配置文件
- ❌ 在 `src/client/` 里加 Java
- ❌ 把效果做成"会真的影响玩家"的形式
- ❌ 自动维护 `monvhua` 计分板的数值（这是数据包的职责）
- ❌ 在 tick 里写日志（每秒每玩家一条 = 噪声）
- ❌ 给 `WitchStage` 加 setter，所有字段都应 `final`

---

## 提交风格

- commit message 用中文，开头加 type 前缀：`feat:` / `fix:` / `docs:` / `chore:` / `refactor:`
- 改了代码就 `./gradlew build` 跑一下再提
- 仓库默认分支 `main`，推送到 `origin/main`
- 用户已授权"每次改动自动 add/commit/push"，但**重大变更**（删除文件、改包名、改 license、改构建配置）仍需要先和用户确认

---

## 验收

完成后应能通过 README 「使用」一节的命令完整复现：在背包看到对应颜色的效果图标、持续时间显示 `**:**`、跨阈值时聊天栏出现「阶段变化」+ 描述、同阶段内反复设分不刷屏、死亡重生后 1 秒内重新获得效果。
