"""External editor for the Monvhua command panel configuration.

The editor keeps a full multi-panel document in py_test/command_panel_config.json
and maps that document to the game's local config file, retaining a top-level
active-page projection for older clients. It never executes commands.
"""

from __future__ import annotations

import copy
import json
import math
import os
import tempfile
import tkinter as tk
import uuid
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any


APP_TITLE = "Monvhua 命令面板配置编辑器"
FULL_CONFIG_VERSION = 3
DEFAULT_PREVIEW = (854, 480)


def _new_id() -> str:
    return str(uuid.uuid4())


def _safe_float(value: Any, default: float) -> float:
    try:
        result = float(value)
        return result if math.isfinite(result) else default
    except (TypeError, ValueError):
        return default


def normalize_popup(source: Any) -> dict[str, Any]:
    """Return a clean popup while preserving command text exactly."""
    source = source if isinstance(source, dict) else {}
    commands = source.get("commands")
    if not isinstance(commands, list):
        legacy = source.get("command", "")
        commands = [{"command": legacy, "delay": 2}] if legacy else []

    clean_commands: list[dict[str, Any]] = []
    for command in commands:
        if isinstance(command, str):
            command = {"command": command, "delay": 2}
        if not isinstance(command, dict):
            continue
        delay = command.get("delay", 2)
        try:
            delay = max(0, int(delay))
        except (TypeError, ValueError):
            delay = 2
        clean_commands.append({
            "id": str(command.get("id") or _new_id()),
            "command": str(command.get("command") or ""),
            "delay": delay,
        })
    if not clean_commands:
        clean_commands.append({"id": _new_id(), "command": "", "delay": 2})

    return {
        "id": str(source.get("id") or _new_id()),
        "name": str(source.get("name") or "新建弹窗"),
        "commands": clean_commands,
        "x": _safe_float(source.get("x"), 0.0),
        "y": _safe_float(source.get("y"), 0.0),
        "width": max(20.0, _safe_float(source.get("width"), 140.0)),
        "height": max(20.0, _safe_float(source.get("height"), 48.0)),
        "rotation": _safe_float(source.get("rotation"), 0.0),
    }


def normalize_config(raw: Any) -> dict[str, Any]:
    """Load v3 multi-panel data or migrate a v2/list config in memory."""
    if isinstance(raw, list):
        legacy_popups = raw
        panels_source: list[Any] = []
    elif isinstance(raw, dict):
        legacy_popups = raw.get("popups", [])
        panels_source = raw.get("panels", []) if isinstance(raw.get("panels"), list) else []
    else:
        raise ValueError("配置根节点必须是 JSON 对象或旧版弹窗数组。")

    panels: list[dict[str, Any]] = []
    for source in panels_source:
        if not isinstance(source, dict):
            continue
        popups = source.get("popups", [])
        panels.append({
            "id": str(source.get("id") or _new_id()),
            "name": str(source.get("name") or f"面板 {len(panels) + 1}"),
            "popups": [normalize_popup(popup) for popup in popups] if isinstance(popups, list) else [],
        })

    if not panels:
        panels = [{
            "id": _new_id(),
            "name": "面板 1",
            "popups": [normalize_popup(popup) for popup in legacy_popups] if isinstance(legacy_popups, list) else [],
        }]

    panel_ids = {panel["id"] for panel in panels}
    active_id = raw.get("activePanelId") if isinstance(raw, dict) else None
    if active_id not in panel_ids:
        active_id = panels[0]["id"]

    preview = raw.get("preview", {}) if isinstance(raw, dict) else {}
    if not isinstance(preview, dict):
        preview = {}
    try:
        revision = max(0, int(raw.get("revision", 0))) if isinstance(raw, dict) else 0
    except (TypeError, ValueError):
        revision = 0
    return {
        "version": FULL_CONFIG_VERSION,
        "revision": revision,
        "activePanelId": active_id,
        "preview": {
            "width": max(160, int(_safe_float(preview.get("width"), DEFAULT_PREVIEW[0]))),
            "height": max(120, int(_safe_float(preview.get("height"), DEFAULT_PREVIEW[1]))),
        },
        "panels": panels,
    }


def add_active_page_projection(config: dict[str, Any]) -> dict[str, Any]:
    """Add the legacy root-level popup list without dropping the page data."""
    document = copy.deepcopy(config)
    panels = document["panels"]
    active = next((panel for panel in panels if panel["id"] == document["activePanelId"]), panels[0])
    document["popups"] = copy.deepcopy(active["popups"])
    return document


def atomic_write_json(path: Path, data: Any) -> None:
    """Write UTF-8 JSON atomically, with a recoverable .bak copy."""
    path = path.expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    temp_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", newline="\n", dir=path.parent,
                                         prefix=path.name + ".", suffix=".tmp", delete=False) as temp:
            temp.write(encoded)
            temp.flush()
            os.fsync(temp.fileno())
            temp_name = temp.name
        if path.exists():
            backup = path.with_suffix(path.suffix + ".bak")
            try:
                backup.write_bytes(path.read_bytes())
            except OSError:
                pass
        os.replace(temp_name, path)
    finally:
        if temp_name and os.path.exists(temp_name):
            os.unlink(temp_name)


class CommandPanelEditor(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("1450x900")
        self.minsize(1120, 680)

        project_root = Path(__file__).resolve().parents[1]
        self.full_config_path = tk.StringVar(value=str(project_root / "py_test" / "command_panel_config.json"))
        self.game_config_path = tk.StringVar(value=str(project_root / "run" / "config" / "monvhua_command_panel.json"))
        self.preview_width = tk.IntVar(value=DEFAULT_PREVIEW[0])
        self.preview_height = tk.IntVar(value=DEFAULT_PREVIEW[1])
        self.status = tk.StringVar(value="就绪。外部完整配置与游戏当前面板配置分开保存。")

        self.config_data = normalize_config({})
        self.dirty = False
        self.selected_popup_id: str | None = None
        self.selected_command_index: int | None = None
        self._loading_controls = False
        self._canvas_items: dict[int, str] = {}
        self._canvas_scale = 1.0
        self._canvas_origin = (0.0, 0.0)
        self._drag_mode: str | None = None
        self._drag_offset = (0.0, 0.0)

        self._build_ui()
        self._load_initial()
        self.protocol("WM_DELETE_WINDOW", self._close)

    @property
    def panels(self) -> list[dict[str, Any]]:
        return self.config_data["panels"]

    def active_panel(self) -> dict[str, Any]:
        active = self.config_data["activePanelId"]
        return next((panel for panel in self.panels if panel["id"] == active), self.panels[0])

    def active_popups(self) -> list[dict[str, Any]]:
        return self.active_panel()["popups"]

    def selected_popup(self) -> dict[str, Any] | None:
        return next((popup for popup in self.active_popups() if popup["id"] == self.selected_popup_id), None)

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(2, weight=1)

        paths = ttk.Frame(self, padding=(8, 8, 8, 4))
        paths.grid(row=0, column=0, sticky="ew")
        paths.columnconfigure(1, weight=1)
        ttk.Label(paths, text="完整多面板配置:").grid(row=0, column=0, sticky="w", padx=(0, 6), pady=3)
        ttk.Entry(paths, textvariable=self.full_config_path).grid(row=0, column=1, sticky="ew", pady=3)
        ttk.Button(paths, text="浏览…", command=self._browse_full_config).grid(row=0, column=2, padx=4)
        ttk.Button(paths, text="重新加载", command=self.reload_full_config).grid(row=0, column=3, padx=4)

        ttk.Label(paths, text="游戏本地配置文件:").grid(row=1, column=0, sticky="w", padx=(0, 6), pady=3)
        ttk.Entry(paths, textvariable=self.game_config_path).grid(row=1, column=1, sticky="ew", pady=3)
        ttk.Button(paths, text="浏览…", command=self._browse_game_config).grid(row=1, column=2, padx=4)
        ttk.Button(paths, text="从游戏文件导入", command=self.import_game_config).grid(row=1, column=3, padx=4)

        toolbar = ttk.Frame(self, padding=(8, 2, 8, 8))
        toolbar.grid(row=1, column=0, sticky="ew")
        ttk.Label(toolbar, text="预览分辨率:").pack(side="left")
        ttk.Spinbox(toolbar, from_=160, to=8192, width=7, textvariable=self.preview_width,
                    command=self._resolution_changed).pack(side="left", padx=(4, 2))
        ttk.Label(toolbar, text="×").pack(side="left")
        ttk.Spinbox(toolbar, from_=120, to=8192, width=7, textvariable=self.preview_height,
                    command=self._resolution_changed).pack(side="left", padx=(2, 12))
        ttk.Button(toolbar, text="应用分辨率", command=self._resolution_changed).pack(side="left")
        ttk.Button(toolbar, text="导入完整配置…", command=self.import_full_config).pack(side="left", padx=(10, 3))
        ttk.Button(toolbar, text="导出完整配置…", command=self.export_full_config).pack(side="left", padx=3)
        ttk.Label(toolbar, text="拖动弹窗可改位置；拖右下角方块可改大小。预览缩放不会写入游戏坐标。",
                  foreground="#555555").pack(side="left", padx=12)
        ttk.Button(toolbar, text="只保存完整配置", command=self.save_full_config).pack(side="right", padx=(6, 0))
        ttk.Button(toolbar, text="保存并同步完整配置到游戏", command=self.save_and_sync_game).pack(side="right")

        body = ttk.Panedwindow(self, orient="horizontal")
        body.grid(row=2, column=0, sticky="nsew", padx=8, pady=(0, 6))
        self.left_frame = ttk.Frame(body, padding=8, width=205)
        self.preview_frame = ttk.Frame(body, padding=8)
        self.detail_frame = ttk.Frame(body, padding=8, width=390)
        body.add(self.left_frame, weight=0)
        body.add(self.preview_frame, weight=1)
        body.add(self.detail_frame, weight=0)
        self._build_left()
        self._build_preview()
        self._build_details()

        status = ttk.Label(self, textvariable=self.status, anchor="w", relief="sunken", padding=(8, 4))
        status.grid(row=3, column=0, sticky="ew")

    def _build_left(self) -> None:
        f = self.left_frame
        f.columnconfigure(0, weight=1)
        f.rowconfigure(6, weight=1)
        ttk.Label(f, text="面板", font=("Microsoft YaHei UI", 11, "bold")).grid(row=0, column=0, sticky="w")

        nav = ttk.Frame(f)
        nav.grid(row=1, column=0, sticky="ew", pady=(6, 4))
        ttk.Button(nav, text="◀", width=3, command=lambda: self._step_panel(-1)).pack(side="left")
        self.panel_label = ttk.Label(nav, text="", anchor="center")
        self.panel_label.pack(side="left", fill="x", expand=True, padx=4)
        ttk.Button(nav, text="▶", width=3, command=lambda: self._step_panel(1)).pack(side="left")

        actions = ttk.Frame(f)
        actions.grid(row=2, column=0, sticky="ew", pady=(0, 8))
        ttk.Button(actions, text="＋ 新建面板", command=self.add_panel).pack(side="left", fill="x", expand=True)
        ttk.Button(actions, text="删除面板", command=self.delete_panel).pack(side="left", fill="x", expand=True, padx=(5, 0))

        self.panel_name = tk.StringVar()
        name_line = ttk.Frame(f)
        name_line.grid(row=3, column=0, sticky="ew", pady=(0, 10))
        ttk.Label(name_line, text="名称").pack(side="left")
        panel_name_field = ttk.Entry(name_line, textvariable=self.panel_name)
        panel_name_field.pack(side="left", fill="x", expand=True, padx=(6, 0))
        self.panel_name.trace_add("write", self._panel_name_changed)

        ttk.Separator(f).grid(row=4, column=0, sticky="new")
        popups_label = ttk.Frame(f)
        popups_label.grid(row=5, column=0, sticky="ew", pady=(7, 4))
        ttk.Label(popups_label, text="本面板弹窗", font=("Microsoft YaHei UI", 10, "bold")).pack(side="left")
        ttk.Button(popups_label, text="＋ 新建弹窗", command=self.add_popup).pack(side="right")

        list_frame = ttk.Frame(f)
        list_frame.grid(row=6, column=0, sticky="nsew")
        list_frame.rowconfigure(0, weight=1)
        list_frame.columnconfigure(0, weight=1)
        self.popup_list = tk.Listbox(list_frame, exportselection=False, activestyle="dotbox")
        self.popup_list.grid(row=0, column=0, sticky="nsew")
        scroll = ttk.Scrollbar(list_frame, orient="vertical", command=self.popup_list.yview)
        scroll.grid(row=0, column=1, sticky="ns")
        self.popup_list.configure(yscrollcommand=scroll.set)
        self.popup_list.bind("<<ListboxSelect>>", self._popup_selected)
        self.popup_list.bind("<Double-Button-1>", lambda _event: self.popup_name_entry.focus_set())

        ttk.Button(f, text="删除选中弹窗…", command=self.delete_popup).grid(row=7, column=0, sticky="ew", pady=(6, 0))
        ttk.Label(f, text="面板之间的弹窗独立保存。至少保留一个面板。", wraplength=190,
                  foreground="#555555").grid(row=8, column=0, sticky="w", pady=(10, 0))

    def _build_preview(self) -> None:
        f = self.preview_frame
        f.columnconfigure(0, weight=1)
        f.rowconfigure(1, weight=1)
        ttk.Label(f, text="界面位置预览", font=("Microsoft YaHei UI", 11, "bold")).grid(row=0, column=0, sticky="w", pady=(0, 6))
        self.canvas = tk.Canvas(f, background="#24262b", highlightthickness=1, highlightbackground="#777777")
        self.canvas.grid(row=1, column=0, sticky="nsew")
        self.canvas.bind("<Configure>", lambda _event: self.redraw_canvas())
        self.canvas.bind("<Button-1>", self._canvas_down)
        self.canvas.bind("<B1-Motion>", self._canvas_drag)
        self.canvas.bind("<ButtonRelease-1>", self._canvas_up)
        ttk.Label(f, text="棋盘代表 Minecraft GUI 坐标；保存的 x/y/宽/高均为 GUI 单位，与 Windows 缩放及预览大小无关。",
                  foreground="#555555", wraplength=650).grid(row=2, column=0, sticky="w", pady=(6, 0))

    def _build_details(self) -> None:
        f = self.detail_frame
        f.columnconfigure(1, weight=1)
        f.columnconfigure(2, weight=0)
        f.rowconfigure(8, weight=1)
        ttk.Label(f, text="选中弹窗", font=("Microsoft YaHei UI", 11, "bold")).grid(row=0, column=0, columnspan=2, sticky="w")

        ttk.Label(f, text="显示名字").grid(row=1, column=0, sticky="w", pady=(8, 2))
        self.popup_name = tk.StringVar()
        self.popup_name_entry = ttk.Entry(f, textvariable=self.popup_name)
        self.popup_name_entry.grid(row=1, column=1, sticky="ew", pady=(8, 2))
        self.popup_delete_button = ttk.Button(f, text="×", width=3, command=self.delete_popup)
        self.popup_delete_button.grid(row=1, column=2, sticky="e", padx=(4, 0), pady=(8, 2))
        self.popup_name.trace_add("write", self._popup_name_changed)

        props = ttk.LabelFrame(f, text="位置与尺寸（GUI 坐标）", padding=6)
        props.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(8, 8))
        for col in range(4):
            props.columnconfigure(col, weight=1)
        self.prop_vars = {key: tk.StringVar() for key in ("x", "y", "width", "height", "rotation")}
        for col, key, label in ((0, "x", "X"), (2, "y", "Y"), (0, "width", "宽"), (2, "height", "高"), (0, "rotation", "旋转°")):
            row = 0 if key in ("x", "y") else 1 if key in ("width", "height") else 2
            ttk.Label(props, text=label).grid(row=row, column=col, sticky="w", padx=(0, 4), pady=2)
            entry = ttk.Entry(props, textvariable=self.prop_vars[key], width=9)
            entry.grid(row=row, column=col + 1, sticky="ew", pady=2)
            entry.bind("<Return>", self._apply_properties)
            entry.bind("<FocusOut>", self._apply_properties)

        ttk.Separator(f).grid(row=3, column=0, columnspan=2, sticky="ew", pady=4)
        command_header = ttk.Frame(f)
        command_header.grid(row=4, column=0, columnspan=2, sticky="ew")
        ttk.Label(command_header, text="指令行（从上到下执行）", font=("Microsoft YaHei UI", 10, "bold")).pack(side="left")
        ttk.Label(command_header, text="每行可单独设置间隔 tick").pack(side="right")

        self.command_list = tk.Listbox(f, height=7, exportselection=False)
        self.command_list.grid(row=5, column=0, columnspan=2, sticky="ew", pady=(5, 3))
        self.command_list.bind("<<ListboxSelect>>", self._command_selected)
        command_actions = ttk.Frame(f)
        command_actions.grid(row=6, column=0, columnspan=2, sticky="ew")
        ttk.Button(command_actions, text="＋ 添加指令行", command=self.add_command).pack(side="left")
        ttk.Button(command_actions, text="删除", command=self.delete_command).pack(side="left", padx=4)
        ttk.Button(command_actions, text="↑", width=3, command=lambda: self.move_command(-1)).pack(side="left", padx=(8, 2))
        ttk.Button(command_actions, text="↓", width=3, command=lambda: self.move_command(1)).pack(side="left")

        ttk.Label(f, text="当前指令").grid(row=7, column=0, sticky="w", pady=(7, 2))
        ttk.Label(f, text="延迟").grid(row=7, column=1, sticky="e", pady=(7, 2))
        editor_row = ttk.Frame(f)
        editor_row.grid(row=8, column=0, columnspan=3, sticky="nsew")
        editor_row.rowconfigure(0, weight=1)
        editor_row.columnconfigure(0, weight=1)
        self.command_text = tk.Text(editor_row, height=9, wrap="none", undo=True, font=("Consolas", 10))
        self.command_text.grid(row=0, column=0, sticky="nsew")
        text_scroll_y = ttk.Scrollbar(editor_row, orient="vertical", command=self.command_text.yview)
        text_scroll_y.grid(row=0, column=1, sticky="ns")
        self.command_text.configure(yscrollcommand=text_scroll_y.set)
        text_scroll_x = ttk.Scrollbar(f, orient="horizontal", command=self.command_text.xview)
        text_scroll_x.grid(row=9, column=0, columnspan=3, sticky="ew")
        self.command_text.configure(xscrollcommand=text_scroll_x.set)
        self.command_text.bind("<KeyRelease>", self._command_text_changed)
        self.command_text.bind("<FocusOut>", self._capture_command_editor)

        delay_line = ttk.Frame(f)
        delay_line.grid(row=10, column=0, columnspan=3, sticky="ew", pady=(5, 2))
        ttk.Label(delay_line, text="本行执行后等待:").pack(side="left")
        self.delay_var = tk.IntVar(value=2)
        self.delay_spin = ttk.Spinbox(delay_line, from_=0, to=1000000, width=10, textvariable=self.delay_var,
                                      command=self._delay_changed)
        self.delay_spin.pack(side="left", padx=5)
        ttk.Label(delay_line, text="tick",).pack(side="left")
        self.delay_spin.bind("<Return>", self._delay_changed)
        self.delay_spin.bind("<FocusOut>", self._delay_changed)

        ttk.Label(f, text="每个指令行只放一条单行命令；换行命令请用“添加指令行”。外部文件不会截断长文本；当前游戏网络包每条命令上限为 32767 字符，超长命令需改游戏协议后才能执行。",
                  wraplength=360, foreground="#555555").grid(row=11, column=0, columnspan=3, sticky="w", pady=(7, 0))

    def _load_initial(self) -> None:
        full_path = Path(self.full_config_path.get())
        game_path = Path(self.game_config_path.get())
        try:
            if full_path.exists():
                self._read_full_file(full_path)
                self.status.set(f"已加载外部完整配置：{full_path}")
            elif game_path.exists():
                self._read_full_file(game_path)
                self.status.set("未找到外部完整配置，已从游戏配置载入面板；保存后会生成外部完整配置文件。")
            else:
                self.config_data = normalize_config({})
                self.status.set("没有找到已有配置，已创建空白面板。")
        except Exception as exc:
            messagebox.showerror("配置加载失败", f"无法读取初始配置：\n{exc}")
            self.config_data = normalize_config({})
        self._refresh_all()

    def _read_full_file(self, path: Path) -> None:
        raw = json.loads(path.read_text(encoding="utf-8-sig"))
        self.config_data = normalize_config(raw)
        self.preview_width.set(self.config_data["preview"]["width"])
        self.preview_height.set(self.config_data["preview"]["height"])
        self.selected_popup_id = None
        self.selected_command_index = None
        self.dirty = False
        self._refresh_all()

    def reload_full_config(self) -> None:
        if not self._confirm_discard():
            return
        path = Path(self.full_config_path.get()).expanduser()
        try:
            if not path.exists():
                self.config_data = normalize_config({})
                self.dirty = False
                self._refresh_all()
                self.status.set("外部配置文件不存在，已载入空白面板。")
                return
            self._read_full_file(path)
            self.status.set(f"已重新加载：{path}")
        except Exception as exc:
            messagebox.showerror("重新加载失败", str(exc))

    def import_game_config(self) -> None:
        if not self._confirm_discard():
            return
        path = Path(self.game_config_path.get()).expanduser()
        try:
            raw = json.loads(path.read_text(encoding="utf-8-sig"))
            self.config_data = normalize_config(raw)
            self.preview_width.set(self.config_data["preview"]["width"])
            self.preview_height.set(self.config_data["preview"]["height"])
            self.selected_popup_id = None
            self.selected_command_index = None
            self.dirty = True
            self._refresh_all()
            self.status.set(f"已从游戏配置导入当前单面板内容；保存后将写入外部完整配置：{path}")
        except Exception as exc:
            messagebox.showerror("导入失败", f"无法读取游戏配置：\n{exc}")

    def import_full_config(self) -> None:
        if not self._confirm_discard():
            return
        path = filedialog.askopenfilename(title="导入完整多面板配置", filetypes=(("JSON 文件", "*.json"), ("所有文件", "*.*")))
        if not path:
            return
        try:
            self._read_full_file(Path(path))
            self.dirty = True
            self._update_title()
            self.status.set(f"已导入完整配置：{path}。保存后将写入当前完整配置文件。")
        except Exception as exc:
            messagebox.showerror("导入失败", f"配置格式无效：\n{exc}")

    def export_full_config(self) -> None:
        path = filedialog.asksaveasfilename(title="导出完整多面板配置", defaultextension=".json",
                                            filetypes=(("JSON 文件", "*.json"), ("所有文件", "*.*")),
                                            initialfile="monvhua_command_panel_all.json")
        if not path:
            return
        try:
            atomic_write_json(Path(path), self._full_document())
            self.status.set(f"完整多面板配置已导出：{path}")
        except Exception as exc:
            messagebox.showerror("导出失败", f"无法导出配置：\n{exc}")

    def _full_document(self) -> dict[str, Any]:
        self._capture_command_editor()
        self.config_data["preview"] = {
            "width": max(160, int(self.preview_width.get())),
            "height": max(120, int(self.preview_height.get())),
        }
        # Keep the active-page projection so older clients can still read the
        # file as a single-panel config while newer clients retain every page.
        return add_active_page_projection(self.config_data)

    def save_full_config(self) -> bool:
        path = Path(self.full_config_path.get()).expanduser()
        try:
            document = self._full_document()
            document["revision"] = int(document.get("revision", 0)) + 1
            atomic_write_json(path, document)
            self.config_data["revision"] = document["revision"]
            self.dirty = False
            self.status.set(f"完整多面板配置已保存（备份为 .bak）：{path}")
            self._update_title()
            return True
        except Exception as exc:
            messagebox.showerror("保存失败", f"无法保存完整配置：\n{exc}")
            return False

    def save_and_sync_game(self) -> bool:
        try:
            full_path = Path(self.full_config_path.get()).expanduser().resolve()
            game_path = Path(self.game_config_path.get()).expanduser().resolve()
            if full_path == game_path:
                messagebox.showerror("配置路径冲突", "完整多面板配置和游戏兼容配置必须使用不同文件；否则旧版游戏保存时会覆盖其他面板。")
                return False
        except OSError as exc:
            messagebox.showerror("配置路径无效", str(exc))
            return False
        if not self.save_full_config():
            return False
        game_path = Path(self.game_config_path.get()).expanduser()
        document = self._full_document()
        panel = self.active_panel()
        try:
            atomic_write_json(game_path, document)
            self.status.set(
                f"已保存完整多面板配置，并将“{panel['name']}”设为游戏活动页。游戏内刷新按钮可重新载入；"
                "旧版本客户端仍能读取顶层 popups 的活动页投影。"
            )
            return True
        except Exception as exc:
            messagebox.showerror("更新游戏配置失败", f"完整配置已保存，但无法更新游戏文件：\n{exc}")
            return False

    def _browse_full_config(self) -> None:
        path = filedialog.asksaveasfilename(title="选择完整多面板配置文件", defaultextension=".json",
                                            filetypes=(("JSON 文件", "*.json"), ("所有文件", "*.*")),
                                            initialfile=Path(self.full_config_path.get()).name,
                                            initialdir=str(Path(self.full_config_path.get()).parent))
        if path:
            self.full_config_path.set(path)

    def _browse_game_config(self) -> None:
        path = filedialog.asksaveasfilename(title="选择游戏当前面板配置文件", defaultextension=".json",
                                            filetypes=(("JSON 文件", "*.json"), ("所有文件", "*.*")),
                                            initialfile=Path(self.game_config_path.get()).name,
                                            initialdir=str(Path(self.game_config_path.get()).parent))
        if path:
            self.game_config_path.set(path)

    def add_panel(self) -> None:
        self._capture_command_editor()
        panel = {"id": _new_id(), "name": f"新建面板 {len(self.panels) + 1}", "popups": []}
        self.panels.append(panel)
        self.config_data["activePanelId"] = panel["id"]
        self.selected_popup_id = None
        self.selected_command_index = None
        self._mark_dirty("已新建独立空面板。")
        self._refresh_all()

    def delete_panel(self) -> None:
        if len(self.panels) <= 1:
            messagebox.showinfo("无法删除", "至少要保留一个面板。")
            return
        panel = self.active_panel()
        if not messagebox.askyesno("确认删除面板", f"确定删除面板“{panel['name']}”及其中全部 {len(panel['popups'])} 个弹窗吗？"):
            return
        index = self.panels.index(panel)
        self.panels.remove(panel)
        new_panel = self.panels[min(index, len(self.panels) - 1)]
        self.config_data["activePanelId"] = new_panel["id"]
        self.selected_popup_id = None
        self.selected_command_index = None
        self._mark_dirty("已删除当前面板。")
        self._refresh_all()

    def _step_panel(self, step: int) -> None:
        self._capture_command_editor()
        index = next(i for i, panel in enumerate(self.panels) if panel["id"] == self.config_data["activePanelId"])
        panel = self.panels[(index + step) % len(self.panels)]
        self.config_data["activePanelId"] = panel["id"]
        self.selected_popup_id = None
        self.selected_command_index = None
        self._refresh_all()
        self._mark_dirty(f"已切换到“{panel['name']}”；切页只改变本地活动页。")

    def add_popup(self) -> None:
        width = min(140.0, float(self.preview_width.get()))
        height = min(48.0, float(self.preview_height.get()))
        popup = {
            "id": _new_id(), "name": "新建弹窗", "commands": [{"id": _new_id(), "command": "", "delay": 2}],
            "x": (float(self.preview_width.get()) - width) / 2,
            "y": (float(self.preview_height.get()) - height) / 2,
            "width": width, "height": height, "rotation": 0.0,
        }
        self.active_popups().append(popup)
        self.selected_popup_id = popup["id"]
        self.selected_command_index = None
        self._mark_dirty("新建弹窗已放在预览画布正中心。")
        self._refresh_all()

    def delete_popup(self) -> None:
        popup = self.selected_popup()
        if popup is None:
            messagebox.showinfo("未选择弹窗", "请先选择要删除的弹窗。")
            return
        if not messagebox.askyesno("确认删除弹窗", f"确定删除弹窗“{popup['name']}”吗？此操作会随配置保存。"):
            return
        self.active_popups().remove(popup)
        self.selected_popup_id = None
        self.selected_command_index = None
        self._mark_dirty("已删除选中弹窗。")
        self._refresh_all()

    def add_command(self) -> None:
        popup = self.selected_popup()
        if popup is None:
            messagebox.showinfo("未选择弹窗", "请先选择或新建一个弹窗。")
            return
        self._capture_command_editor()
        popup["commands"].append({"id": _new_id(), "command": "", "delay": 2})
        self.selected_command_index = len(popup["commands"]) - 1
        self._refresh_command_editor()
        self._mark_dirty("已在指令列表末尾添加空指令行。")

    def delete_command(self) -> None:
        popup = self.selected_popup()
        index = self.selected_command_index
        if popup is None or index is None or index >= len(popup["commands"]):
            messagebox.showinfo("未选择指令", "请先从指令列表中选择一行。")
            return
        self._capture_command_editor()
        popup["commands"].pop(index)
        self.selected_command_index = min(index, len(popup["commands"]) - 1) if popup["commands"] else None
        self._refresh_command_editor()
        self._mark_dirty("已删除所选指令行。")

    def move_command(self, step: int) -> None:
        popup = self.selected_popup()
        index = self.selected_command_index
        if popup is None or index is None:
            return
        self._capture_command_editor()
        other = index + step
        if other < 0 or other >= len(popup["commands"]):
            return
        popup["commands"][index], popup["commands"][other] = popup["commands"][other], popup["commands"][index]
        self.selected_command_index = other
        self._refresh_command_editor()
        self._mark_dirty("已调整指令执行顺序。")

    def _refresh_all(self) -> None:
        self._loading_controls = True
        try:
            panel = self.active_panel()
            self.panel_label.configure(text=f"{self.panels.index(panel) + 1} / {len(self.panels)}")
            self.panel_name.set(panel["name"])
            self.popup_list.delete(0, tk.END)
            for popup in panel["popups"]:
                self.popup_list.insert(tk.END, popup["name"])
            selected_index = next((i for i, popup in enumerate(panel["popups"]) if popup["id"] == self.selected_popup_id), None)
            if selected_index is not None:
                self.popup_list.selection_set(selected_index)
                self.popup_list.activate(selected_index)
            self._refresh_popup_editor()
            self._refresh_command_editor()
            self.redraw_canvas()
            self._update_title()
        finally:
            self._loading_controls = False

    def _refresh_popup_editor(self) -> None:
        popup = self.selected_popup()
        self.popup_name.set(popup["name"] if popup else "")
        state = "normal" if popup else "disabled"
        self.popup_name_entry.configure(state=state)
        self.popup_delete_button.configure(state=state)
        for var in self.prop_vars.values():
            var.set("")
        if popup:
            for key, var in self.prop_vars.items():
                var.set(f"{popup[key]:g}")

    def _refresh_command_editor(self) -> None:
        self._loading_controls = True
        try:
            self.command_list.delete(0, tk.END)
            popup = self.selected_popup()
            commands = popup["commands"] if popup else []
            for index, row in enumerate(commands):
                text = row.get("command", "").replace("\n", " ↵ ").strip()
                snippet = text[:52] + ("…" if len(text) > 52 else "")
                self.command_list.insert(tk.END, f"{index + 1:02d}  [{row.get('delay', 2)} tick]  {snippet or '（空指令）'}")
            if self.selected_command_index is not None and self.selected_command_index < len(commands):
                self.command_list.selection_set(self.selected_command_index)
                self.command_list.activate(self.selected_command_index)
                row = commands[self.selected_command_index]
                self.command_text.configure(state="normal")
                self.command_text.delete("1.0", tk.END)
                self.command_text.insert("1.0", row.get("command", ""))
                self.delay_var.set(row.get("delay", 2))
                self.delay_spin.configure(state="normal")
                self.command_text.configure(state="normal")
            else:
                self.command_text.configure(state="normal")
                self.command_text.delete("1.0", tk.END)
                self.command_text.configure(state="disabled")
                self.delay_var.set(2)
                self.delay_spin.configure(state="disabled")
        finally:
            self._loading_controls = False

    def _refresh_popup_list_name(self) -> None:
        popup = self.selected_popup()
        if popup:
            index = self.active_popups().index(popup)
            self.popup_list.delete(index)
            self.popup_list.insert(index, popup["name"])
            self.popup_list.selection_set(index)
        self.redraw_canvas()

    def _popup_selected(self, _event: tk.Event[Any]) -> None:
        selection = self.popup_list.curselection()
        self._capture_command_editor()
        if not selection:
            self.selected_popup_id = None
        else:
            self.selected_popup_id = self.active_popups()[selection[0]]["id"]
        self.selected_command_index = None
        self._refresh_popup_editor()
        self._refresh_command_editor()
        self.redraw_canvas()

    def _command_selected(self, _event: tk.Event[Any]) -> None:
        selection = self.command_list.curselection()
        self._capture_command_editor()
        self.selected_command_index = selection[0] if selection else None
        self._refresh_command_editor()

    def _capture_command_editor(self, _event: tk.Event[Any] | None = None) -> None:
        if self._loading_controls:
            return
        popup = self.selected_popup()
        index = self.selected_command_index
        if popup is None or index is None or index >= len(popup["commands"]):
            return
        command = self.command_text.get("1.0", "end-1c")
        try:
            delay = max(0, int(self.delay_var.get()))
        except (tk.TclError, ValueError):
            delay = 2
            self.delay_var.set(delay)
        row = popup["commands"][index]
        changed = row.get("command") != command or row.get("delay", 2) != delay
        row["command"] = command
        row["delay"] = delay
        if changed:
            self.dirty = True
            self._update_title()
            self._update_command_label(index)

    def _update_command_label(self, index: int) -> None:
        popup = self.selected_popup()
        if popup is None or index >= len(popup["commands"]):
            return
        row = popup["commands"][index]
        text = row.get("command", "").replace("\n", " ↵ ").strip()
        snippet = text[:52] + ("…" if len(text) > 52 else "")
        self.command_list.delete(index)
        self.command_list.insert(index, f"{index + 1:02d}  [{row.get('delay', 2)} tick]  {snippet or '（空指令）'}")
        self.command_list.selection_set(index)

    def _command_text_changed(self, _event: tk.Event[Any]) -> None:
        self._capture_command_editor()

    def _delay_changed(self, _event: tk.Event[Any] | None = None) -> None:
        self._capture_command_editor()

    def _panel_name_changed(self, *_args: Any) -> None:
        if self._loading_controls:
            return
        panel = self.active_panel()
        name = self.panel_name.get().strip()
        if name and panel["name"] != name:
            panel["name"] = name
            self.dirty = True
            self.panel_label.configure(text=f"{self.panels.index(panel) + 1} / {len(self.panels)} · {name}")
            self._update_title()

    def _popup_name_changed(self, *_args: Any) -> None:
        if self._loading_controls:
            return
        popup = self.selected_popup()
        if popup and popup["name"] != self.popup_name.get():
            popup["name"] = self.popup_name.get()
            self.dirty = True
            self._update_title()
            self._refresh_popup_list_name()

    def _apply_properties(self, _event: tk.Event[Any] | None = None) -> str:
        if self._loading_controls:
            return "break"
        popup = self.selected_popup()
        if popup is None:
            return "break"
        try:
            values = {key: float(var.get()) for key, var in self.prop_vars.items()}
            if not all(math.isfinite(value) for value in values.values()):
                raise ValueError
            values["width"] = max(20.0, values["width"])
            values["height"] = max(20.0, values["height"])
        except (ValueError, tk.TclError):
            self.status.set("位置/尺寸必须是有效数字；宽和高最小为 20。")
            self._refresh_popup_editor()
            return "break"
        changed = any(popup[key] != value for key, value in values.items())
        popup.update(values)
        self.redraw_canvas()
        if changed:
            self._mark_dirty("已更新弹窗位置、尺寸或旋转角度。")
        return "break"

    def _resolution_changed(self) -> None:
        try:
            w = max(160, int(self.preview_width.get()))
            h = max(120, int(self.preview_height.get()))
            self.preview_width.set(w)
            self.preview_height.set(h)
            self.config_data["preview"] = {"width": w, "height": h}
            self.redraw_canvas()
            self._mark_dirty(f"预览分辨率设为 {w} × {h}。")
        except (ValueError, tk.TclError):
            self.status.set("预览分辨率需要填写整数。")

    def redraw_canvas(self) -> None:
        if not hasattr(self, "canvas"):
            return
        self.canvas.delete("all")
        self._canvas_items.clear()
        width = max(1, self.canvas.winfo_width())
        height = max(1, self.canvas.winfo_height())
        gui_w = max(160, int(self.preview_width.get()))
        gui_h = max(120, int(self.preview_height.get()))
        margin = 26
        scale = min(max(0.05, (width - margin * 2) / gui_w), max(0.05, (height - margin * 2) / gui_h))
        origin_x = (width - gui_w * scale) / 2
        origin_y = (height - gui_h * scale) / 2
        self._canvas_scale = scale
        self._canvas_origin = (origin_x, origin_y)
        right = origin_x + gui_w * scale
        bottom = origin_y + gui_h * scale
        self.canvas.create_rectangle(origin_x, origin_y, right, bottom, fill="#14151a", outline="#d2d2d2", width=1)

        spacing = 50
        if scale * spacing >= 16:
            for x in range(spacing, gui_w, spacing):
                px = origin_x + x * scale
                self.canvas.create_line(px, origin_y, px, bottom, fill="#252830")
            for y in range(spacing, gui_h, spacing):
                py = origin_y + y * scale
                self.canvas.create_line(origin_x, py, right, py, fill="#252830")
        self.canvas.create_text(origin_x + 5, origin_y - 9, anchor="sw", fill="#eeeeee",
                                text=f"{gui_w} × {gui_h} GUI 坐标", font=("Segoe UI", 9))

        selected = self.selected_popup()
        for popup in self.active_popups():
            x, y, w, h = (popup[key] for key in ("x", "y", "width", "height"))
            cx, cy = x + w / 2, y + h / 2
            angle = math.radians(popup.get("rotation", 0.0))
            corners = []
            for px, py in ((x, y), (x + w, y), (x + w, y + h), (x, y + h)):
                dx, dy = px - cx, py - cy
                rx, ry = dx * math.cos(angle) - dy * math.sin(angle), dx * math.sin(angle) + dy * math.cos(angle)
                corners.extend((origin_x + (cx + rx) * scale, origin_y + (cy + ry) * scale))
            active = popup is selected
            fill = "#526d91" if active else "#374858"
            outline = "#ffe071" if active else "#8fa5b7"
            item = self.canvas.create_polygon(*corners, fill=fill, outline=outline, width=2 if active else 1)
            self._canvas_items[item] = popup["id"]
            self._canvas_items[self.canvas.create_text(origin_x + cx * scale, origin_y + cy * scale,
                text=popup["name"], fill="white", font=("Segoe UI", max(8, int(min(12, 12 * scale))), "bold"))] = popup["id"]
            if active:
                hx = origin_x + (x + w) * scale
                hy = origin_y + (y + h) * scale
                self.canvas.create_rectangle(hx - 5, hy - 5, hx + 5, hy + 5, fill="#ffe071", outline="#101010")
        if not self.active_popups():
            self.canvas.create_text((origin_x + right) / 2, (origin_y + bottom) / 2,
                                    text="当前面板没有弹窗\n点击左侧“新建弹窗”开始布局",
                                    fill="#aeb4bd", justify="center", font=("Segoe UI", 12))

    def _canvas_to_gui(self, x: float, y: float) -> tuple[float, float]:
        origin_x, origin_y = self._canvas_origin
        return (x - origin_x) / self._canvas_scale, (y - origin_y) / self._canvas_scale

    def _canvas_down(self, event: tk.Event[Any]) -> None:
        gx, gy = self._canvas_to_gui(event.x, event.y)
        popup = self._hit_popup(gx, gy)
        if popup is None:
            self.selected_popup_id = None
            self.selected_command_index = None
            self._refresh_popup_editor()
            self._refresh_command_editor()
            self.redraw_canvas()
            return
        self._capture_command_editor()
        self.selected_popup_id = popup["id"]
        self.selected_command_index = None
        self._refresh_popup_editor()
        self._refresh_command_editor()
        x, y, w, h = (popup[key] for key in ("x", "y", "width", "height"))
        if abs(gx - (x + w)) * self._canvas_scale <= 10 and abs(gy - (y + h)) * self._canvas_scale <= 10:
            self._drag_mode = "resize"
            self._drag_offset = (w - gx, h - gy)
        else:
            self._drag_mode = "move"
            self._drag_offset = (gx - x, gy - y)
        self._refresh_all()

    def _canvas_drag(self, event: tk.Event[Any]) -> None:
        popup = self.selected_popup()
        if popup is None or self._drag_mode is None:
            return
        gx, gy = self._canvas_to_gui(event.x, event.y)
        if self._drag_mode == "move":
            popup["x"] = gx - self._drag_offset[0]
            popup["y"] = gy - self._drag_offset[1]
        else:
            popup["width"] = max(20.0, gx + self._drag_offset[0] - popup["x"])
            popup["height"] = max(20.0, gy + self._drag_offset[1] - popup["y"])
        self._loading_controls = True
        try:
            for key, var in self.prop_vars.items():
                var.set(f"{popup[key]:g}")
        finally:
            self._loading_controls = False
        self.redraw_canvas()

    def _canvas_up(self, _event: tk.Event[Any]) -> None:
        if self._drag_mode:
            self._drag_mode = None
            self._mark_dirty("画布布局已修改。")

    def _hit_popup(self, x: float, y: float) -> dict[str, Any] | None:
        for popup in reversed(self.active_popups()):
            px, py, w, h = (popup[key] for key in ("x", "y", "width", "height"))
            cx, cy = px + w / 2, py + h / 2
            angle = -math.radians(popup.get("rotation", 0.0))
            dx, dy = x - cx, y - cy
            rx = dx * math.cos(angle) - dy * math.sin(angle) + cx
            ry = dx * math.sin(angle) + dy * math.cos(angle) + cy
            if px <= rx <= px + w and py <= ry <= py + h:
                return popup
        return None

    def _mark_dirty(self, message: str) -> None:
        self.dirty = True
        self.status.set(message)
        self._update_title()

    def _update_title(self) -> None:
        self.title(APP_TITLE + (" *" if self.dirty else ""))

    def _confirm_discard(self) -> bool:
        if not self.dirty:
            return True
        return messagebox.askyesno("未保存更改", "当前有未保存修改，仍要丢弃并继续吗？")

    def _close(self) -> None:
        if not self.dirty:
            self.destroy()
            return
        choice = messagebox.askyesnocancel("保存修改", "要保存完整配置并更新游戏当前面板吗？")
        if choice is None:
            return
        if choice:
            if not self.save_and_sync_game():
                return
            self.destroy()
            return
        self.destroy()


if __name__ == "__main__":
    app = CommandPanelEditor()
    app.mainloop()
