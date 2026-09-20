"""Tkinter layout editor. Run this file to edit; save layout.json for migration."""
from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "preview.png"
WIDTH, HEIGHT = 1000, 640
SAFE = 36
AVATAR_BOX = (72, 76, 390, 330)
NAME_BOX = (60, 420, 410, 500)
MC_NAME_POS = (235, 505)
WITCH_VALUE_POS = (520, 110)
TAG_VALUE_POS = (520, 215)
HEALTH_TITLE_POS = (520, 275)
HEART_BOX = (520, 350, 930, 415)
HEALTH_CELL_SIZE = 24
HEALTH_CELL_GAP = 8
HEALTH_COLUMNS = 10
INTRO_BOX = (520, 425, 930, 590)
BG = (4, 6, 12, 255)
GRID = (18, 37, 72, 255)
TEXT = (218, 218, 255, 255)
MUTED = (157, 164, 202, 255)
PINK = (255, 157, 207, 255)
GOLD = (255, 211, 102, 255)
RED = (239, 91, 116, 255)
EMPTY_HEART = (72, 76, 105, 255)


def font(size: int, bold: bool = False):
    for name in (("msyhbd.ttc" if bold else "msyh.ttc"), "simhei.ttf", "arial.ttf"):
        path = Path("C:/Windows/Fonts") / name
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def read_texts() -> dict[str, str]:
    raw = (ROOT / "text.json").read_bytes()
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            value = json.loads(raw.decode(encoding))
            if isinstance(value, dict):
                return {str(k): str(v) for k, v in value.items()}
        except (UnicodeDecodeError, json.JSONDecodeError):
            pass
    return {}


def fit_image(image: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
    x0, y0, x1, y1 = box
    image = image.convert("RGBA")
    image.thumbnail((x1 - x0, y1 - y0), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((canvas.width - image.width) // 2, (canvas.height - image.height) // 2))
    return canvas


def wrap_text(draw, text: str, used_font, width: int) -> list[str]:
    lines, current = [], ""
    for char in text:
        candidate = current + char
        if not current or draw.textbbox((0, 0), candidate, font=used_font)[2] <= width:
            current = candidate
        else:
            lines.append(current)
            current = char
    if current:
        lines.append(current)
    return lines


def draw_hearts(draw, box, current: float, maximum: float, absorption: float) -> None:
    # Layout placeholders only: one square represents two health points.
    x0, y0, x1, y1 = box
    step = HEALTH_CELL_SIZE + HEALTH_CELL_GAP
    columns = max(1, min(HEALTH_COLUMNS, (x1 - x0 + HEALTH_CELL_GAP) // step))
    count = math.ceil(maximum / 2) + math.ceil(absorption / 2)
    for index in range(count):
        x = x0 + (index % columns) * step
        y = y0 + (index // columns) * step
        if x + HEALTH_CELL_SIZE > x1 or y + HEALTH_CELL_SIZE > y1:
            break
        draw.rectangle((x, y, x + HEALTH_CELL_SIZE, y + HEALTH_CELL_SIZE), outline=TEXT, width=2)


def main() -> None:
    image = Image.new("RGBA", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(image)
    for x in range(0, WIDTH, 100):
        draw.line((x, 0, x, HEIGHT), fill=GRID, width=1)
    for y in range(0, HEIGHT, 64):
        draw.line((0, y, WIDTH, y), fill=GRID, width=1)
    draw.rectangle((SAFE, SAFE, WIDTH - SAFE, HEIGHT - SAFE), outline=(36, 53, 92), width=2)

    avatar_path = ROOT / "charactor" / "ema.png"
    if avatar_path.exists():
        image.alpha_composite(fit_image(Image.open(avatar_path), AVATAR_BOX), AVATAR_BOX[:2])
    draw = ImageDraw.Draw(image)
    name_path = ROOT / "name_background.png"
    if name_path.exists():
        image.alpha_composite(fit_image(Image.open(name_path), NAME_BOX), NAME_BOX[:2])
    draw = ImageDraw.Draw(image)
    draw.text((125, 430), "樱羽艾玛", font=font(38, True), fill=PINK, stroke_width=1, stroke_fill=(30, 18, 45))
    draw.text(MC_NAME_POS, "Player629", font=font(20), fill=MUTED, anchor="ma")
    draw.text(WITCH_VALUE_POS, "魔女化数值", font=font(29, True), fill=TEXT)
    draw.text((WITCH_VALUE_POS[0], WITCH_VALUE_POS[1] + 42), "125", font=font(42, True), fill=GOLD)
    draw.text(TAG_VALUE_POS, "标签面板数值： 80", font=font(21), fill=MUTED)
    draw.text(HEALTH_TITLE_POS, "健康状态", font=font(28, True), fill=TEXT)
    draw.text((HEALTH_TITLE_POS[0], HEALTH_TITLE_POS[1] + 40), "生命值 17 / 20 + 4", font=font(20), fill=MUTED)
    draw_hearts(draw, HEART_BOX, current=17, maximum=20, absorption=4)
    body_font = font(17)
    description = read_texts().get("ema", "这里显示角色介绍文本。")
    y = INTRO_BOX[1]
    for line in wrap_text(draw, description, body_font, INTRO_BOX[2] - INTRO_BOX[0])[:8]:
        draw.text((INTRO_BOX[0], y), line, font=body_font, fill=TEXT)
        y += 21
    image.convert("RGB").save(OUTPUT)
    print(f"wrote {OUTPUT} ({WIDTH}x{HEIGHT})")


def open_editor():
    """Interactive editor; coordinates stay in the 1000x640 logical space."""
    import tkinter as tk
    from tkinter import ttk, messagebox
    from PIL import ImageTk
    import copy

    layout_path = ROOT / "layout.json"
    defaults = {
        "立绘": ["avatar", 72, 60, 318, 330, 20, ""],
        "名字背景": ["background", 60, 410, 350, 100, 20, ""],
        "角色名字": ["text", 125, 435, 270, 55, 38, "樱羽艾玛"],
        "MC名字": ["text", 170, 520, 230, 35, 20, "Player629"],
        "魔女化标题": ["text", 520, 100, 400, 45, 29, "魔女化数值"],
        "魔女化数值": ["text", 520, 155, 400, 60, 42, "125"],
        "健康标题": ["text", 520, 255, 400, 45, 28, "健康状态"],
        "血量方框": ["health", 520, 310, 410, 75, 24, ""],
        "角色介绍": ["intro", 520, 420, 410, 175, 17, ""],
    }
    keys = ("kind", "x", "y", "width", "height", "size", "text")
    defaults = {name: dict(zip(keys, values)) for name, values in defaults.items()}
    data = copy.deepcopy(defaults)
    descriptions = read_texts()
    root = tk.Tk()
    root.title("命令面板布局调试 · 1000 × 640")
    root.geometry("1320x760")
    selected = tk.StringVar(value="立绘")
    role = tk.StringVar(value="ema")
    guides = tk.BooleanVar(value=True)
    status = tk.StringVar(value="拖动区域调整位置；右侧编辑参数后按 Enter；保存布局供后续迁移。")
    fields = {key: tk.StringVar() for key in keys[1:]}
    photos, sources = [], {}
    viewport = [1.0, 0.0, 0.0]
    drag = []
    if layout_path.exists():
        try:
            saved = json.loads(layout_path.read_text(encoding="utf-8"))
            for name, values in saved["regions"].items():
                if name in data:
                    data[name].update(values)
            role.set(saved.get("role", "ema"))
        except (ValueError, KeyError, TypeError) as exc:
            messagebox.showwarning("布局读取失败", str(exc))

    sidebar = ttk.Frame(root, padding=12)
    sidebar.pack(side="right", fill="y")
    ttk.Label(root, textvariable=status, wraplength=900).pack(side="bottom", fill="x")
    canvas = tk.Canvas(root, background="#252525", highlightthickness=0)
    canvas.pack(fill="both", expand=True)

    def redraw(_=None):
        canvas.delete("all")
        photos.clear()
        scale = max(0.05, min(canvas.winfo_width()/WIDTH, canvas.winfo_height()/HEIGHT))
        ox, oy = (canvas.winfo_width()-WIDTH*scale)/2, (canvas.winfo_height()-HEIGHT*scale)/2
        viewport[:] = [scale, ox, oy]
        def point(x, y):
            return ox+x*scale, oy+y*scale
        canvas.create_rectangle(*point(0, 0), *point(WIDTH, HEIGHT), fill="#060810", outline="")
        if guides.get():
            for x in range(0, WIDTH, 100):
                canvas.create_line(*point(x, 0), *point(x, HEIGHT), fill="#263044")
            for y in range(0, HEIGHT, 64):
                canvas.create_line(*point(0, y), *point(WIDTH, y), fill="#263044")
            canvas.create_rectangle(*point(36, 36), *point(964, 604), outline="#666666", dash=(4, 4))
        for name, item in data.items():
            kind, x, y, w, h, size, text = (item[k] for k in keys)
            if kind in ("avatar", "background"):
                path = ROOT / (f"charactor/{role.get()}.png" if kind == "avatar" else "name_background.png")
                if path.exists():
                    if path not in sources:
                        with Image.open(path) as source:
                            sources[path] = source.convert("RGBA")
                    picture = fit_image(sources[path], (0, 0, w, h))
                    picture = picture.resize((max(1, round(w*scale)), max(1, round(h*scale))), Image.Resampling.LANCZOS)
                    photo = ImageTk.PhotoImage(picture, master=root)
                    photos.append(photo)
                    canvas.create_image(*point(x,y), image=photo, anchor="nw")
                else:
                    canvas.create_text(*point(x+5,y+5), text="暂无立绘", anchor="nw", fill="white")
            elif kind == "health":
                step = size+8
                columns = max(1, min(10, (w+8)//step))
                for i in range(12):
                    dx, dy = (i % columns)*step, (i//columns)*step
                    if dx+size <= w and dy+size <= h:
                        canvas.create_rectangle(*point(x+dx,y+dy), *point(x+dx+size,y+dy+size), outline="white")
            else:
                # Child canvas clips text to the editable region; events are
                # forwarded to the main canvas so text regions remain draggable.
                content = descriptions.get(role.get(), "暂无介绍") if kind == "intro" else text
                clip = tk.Canvas(canvas, width=max(1,round(w*scale)), height=max(1,round(h*scale)), background="#060810", highlightthickness=0)
                clip.create_text(0, 0, text=content, anchor="nw", width=max(1,round(w*scale)), font=("Microsoft YaHei", -max(1,round(size*scale))), fill="#dddddd")
                canvas.create_window(*point(x,y), window=clip, anchor="nw")
                for event_name in ("<Button-1>", "<B1-Motion>", "<ButtonRelease-1>"):
                    clip.bind(event_name, lambda event, seq=event_name: canvas.event_generate(seq, x=event.x_root-canvas.winfo_rootx(), y=event.y_root-canvas.winfo_rooty()))
            if guides.get():
                canvas.create_rectangle(*point(x,y), *point(x+w,y+h), outline="#ffd166" if name == selected.get() else "#536078", dash=(3,3))

    # Destroy old child canvases when rebuilding (delete removes only windows).
    draw_regions = redraw
    def redraw(_=None):
        for child in canvas.winfo_children():
            child.destroy()
        draw_regions()

    def select(_=None):
        for key, var in fields.items():
            var.set(str(data[selected.get()][key]))
        redraw()

    def apply(_=None):
        try:
            values = {key: int(fields[key].get()) for key in keys[1:-1]}
            if any(values[key] <= 0 for key in ("width", "height", "size")):
                raise ValueError("宽度、高度、字号必须大于 0")
            data[selected.get()].update(values, text=fields["text"].get())
            redraw()
            return True
        except ValueError as exc:
            messagebox.showerror("参数错误", str(exc))
            return False

    def save():
        if apply():
            layout_path.write_text(json.dumps(dict(canvas=[WIDTH,HEIGHT], role=role.get(), regions=data), ensure_ascii=False, indent=2), encoding="utf-8")
            status.set(f"已保存布局：{layout_path.name}")

    def reset():
        data.clear()
        data.update(copy.deepcopy(defaults))
        select()

    def change_role(_=None):
        data["角色名字"]["text"] = "樱羽艾玛" if role.get() == "ema" else role.get()
        select()

    def press(event):
        s, ox, oy = viewport
        x, y = (event.x-ox)/s, (event.y-oy)/s
        drag.clear()
        for name, item in reversed(list(data.items())):
            if item["x"] <= x <= item["x"]+item["width"] and item["y"] <= y <= item["y"]+item["height"]:
                selected.set(name)
                drag.extend((x-item["x"], y-item["y"]))
                select()
                canvas.grab_set()
                break

    def motion(event):
        if drag:
            s, ox, oy = viewport
            item = data[selected.get()]
            item["x"], item["y"] = round((event.x-ox)/s-drag[0]), round((event.y-oy)/s-drag[1])
            select()

    ttk.Label(sidebar, text="角色 tag").pack(anchor="w")
    choices = ttk.Combobox(sidebar, textvariable=role, values=sorted(descriptions), state="readonly")
    choices.pack(fill="x")
    choices.bind("<<ComboboxSelected>>", change_role)
    ttk.Label(sidebar, text="编辑区域").pack(anchor="w", pady=(12,0))
    choices = ttk.Combobox(sidebar, textvariable=selected, values=list(data), state="readonly")
    choices.pack(fill="x")
    choices.bind("<<ComboboxSelected>>", select)
    for key, label in zip(keys[1:], ("X", "Y", "宽度", "高度", "字号 / 方框大小", "文本")):
        ttk.Label(sidebar, text=label).pack(anchor="w", pady=(8,0))
        entry = ttk.Entry(sidebar, textvariable=fields[key])
        entry.pack(fill="x")
        entry.bind("<Return>", apply)
    ttk.Button(sidebar, text="应用参数", command=apply).pack(fill="x", pady=10)
    ttk.Checkbutton(sidebar, text="显示网格和边界", variable=guides, command=redraw).pack()
    ttk.Button(sidebar, text="保存布局 JSON", command=save).pack(fill="x", pady=8)
    ttk.Button(sidebar, text="恢复默认布局", command=reset).pack(fill="x")
    ttk.Label(sidebar, text="窗口等比缩放，坐标保持 1000×640。\n介绍读取 text.json，超出区域会裁剪。\n方框仅用于调试位置。", wraplength=250).pack(pady=12)
    canvas.bind("<Configure>", redraw)
    canvas.bind("<Button-1>", press)
    canvas.bind("<B1-Motion>", motion)
    def release(_):
        drag.clear()
        canvas.grab_release()
    canvas.bind("<ButtonRelease-1>", release)
    select()
    root.mainloop()


if __name__ == "__main__":
    open_editor()
