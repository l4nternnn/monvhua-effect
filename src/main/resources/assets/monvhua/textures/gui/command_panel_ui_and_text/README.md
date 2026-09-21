# 面板 UI 调试与游戏接入

`test.py` 与游戏读取同一份 `layout.json`。坐标原点在左上角，默认逻辑画布为 1000 × 640；整张画布贴在原来的黄色屏幕平面上，不改变物品姿态、旋转轴或屏幕边框。

## 布局和内容

- `canvas`：逻辑分辨率。保持 1000:640 的比例可避免拉伸。
- `render_scale`：输出纹理倍率，当前为 2，实际纹理为 2000 × 1280；不会改变模型上的物理尺寸。可设为 1～4。
- `regions`：保留现有区域名称；`x/y` 是位置，`width/height` 是区域大小，`size` 是文字或心形大小。
- `profile_frame` 区域绘制 `profile_frame.png`，层级为背景 → 立绘底框 → 立绘 → 死亡覆盖 → 文字。
- 普通文字、介绍在区域内换行和裁剪。角色名字按共同基线排列，允许放大字超出区域，和调试器一致。
- `charactor_name.json`：按玩家 tag 映射名字；`char_scales` 使用从 1 开始的字序号。缺省首字为 1.5 倍，空对象表示全部原大小。`first_color` 控制首字，`color` 控制其余字。
- `text.json`：角色介绍，支持换行。
- `charactor/<tag的小写形式>.png`：立绘。tag 本身仍区分大小写，例如 `Sunday` 使用 `sunday.png`。
- `death.png`：玩家死亡时覆盖在立绘区域；`name_background.png` 单独绘制，名字文字没有黑色底框。

游戏忽略调试器的示例 `role`、玩家名字和数值。使用服务器确认的玩家 tag、真实名字、`monvhua` 计分板分数、生命上限、当前生命、吸收生命及死亡状态。没有角色或分数时显示缺省提示，不显示示例值。角色优先使用现有 WitchRole 判定，再匹配其他 tag。

## 继续调试

1. 用 `test.py` 调整并保存布局。
2. 开发环境修改源码资源后执行 `./gradlew.bat processResources`，然后在游戏按 F3+T 重载。资源包内的修改直接 F3+T 即可。
3. 可以在 `layout.json` 顶层添加 `"debug_guides": true` 查看区域边框。无效布局会写入日志并保留上一份有效布局。

资源包可覆盖同名文件，路径为 `assets/monvhua/textures/gui/command_panel_ui_and_text/`。

可选顶层参数 `font_family` 默认 `Microsoft YaHei`，`name_font_family` 默认 `STZhongsong`，对应当前调试器字体。缺少本机字体可能回退并产生字形差异。可用 `font_resource` 指定资源包中的 TTF，例如 `monvhua:font/panel.ttf`，该字体会同时用于名字和其他文字。调试器暂不读取这些可选字体参数。

当前通过 Java2D 离屏排字并生成动态纹理，文字仍来自 JSON 和实时数据。尚未接入 Modern UI 字体管线，换行及字体度量与 Tkinter 可能略有差异。

游戏血量使用资源包覆盖后的原版心形 PNG，支持半颗心、吸收生命、中毒、凋零、冰冻和极限模式。受伤闪烁、低血量抖动及恢复跳动为近似表现，不是原版 HUD 的完整逐 tick 实现；不支持心形 PNG 的 `.mcmeta` 动画。死亡覆盖只在物品屏幕实际可见时显示，死亡界面可能隐藏手持物品。

## 网络边界

新增状态包只下发持有者本人的展示数据，不上传或覆盖本地指令配置，也不替代已有手动同步与确认流程。首次更新需要同时更新并重启服务端和客户端；后续仅调资源不需要重启。
