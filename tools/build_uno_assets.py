#!/usr/bin/env python3
"""Generate original pixel colour cards, GUI symbols and CraftEngine models.

Only craftengine/uno and uno-assets-preview.png are written. Art is drawn from
integer pixel shapes and bitmap glyphs; no existing image is sampled or edited.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw
import yaml
from build_assets import bitmap, pixel_text, mini_text, label, gui_model, context_model

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "craftengine" / "uno"
ASSETS = PACK / "resourcepack" / "assets" / "uno"
SKINS = {
    "classic": {"name": "经典", "paper": "#fff9ea", "edge": "#b8b4a6", "back": "#243b58"},
    "jade": {"name": "青玉", "paper": "#edf7ee", "edge": "#a4b8aa", "back": "#184b44"},
}
COLORS = {
    "red": {"name": "红", "letter": "R", "fill": "#bd3f46", "ink": "#fff9ea"},
    "yellow": {"name": "黄", "letter": "Y", "fill": "#e2bd39", "ink": "#352c18"},
    "green": {"name": "绿", "letter": "G", "fill": "#348253", "ink": "#fff9ea"},
    "blue": {"name": "蓝", "letter": "B", "fill": "#356fad", "ink": "#fff9ea"},
}
VALUES = [str(value) for value in range(10)] + ["skip", "reverse", "draw_two"]
VALUE_NAMES = {"skip": "跳过", "reverse": "反转", "draw_two": "加二"}
BUTTONS = {
    "draw": ("摸牌", "#356f8a"), "uno": ("喊 UNO", "#b24645"),
    "catch": ("抓漏喊", "#876294"), "challenge": ("质疑 +4", "#9d753a"),
    "accept": ("接受罚牌", "#546c82"),
    "color_red": ("选择红色", COLORS["red"]["fill"]),
    "color_yellow": ("选择黄色", COLORS["yellow"]["fill"]),
    "color_green": ("选择绿色", COLORS["green"]["fill"]),
    "color_blue": ("选择蓝色", COLORS["blue"]["fill"]),
}
WHITE = "#fff9ea"
INK = "#18222e"
SKIP = ["011110", "110011", "100101", "101001", "110011", "011110"]
REVERSE = ["00010", "11111", "00010", "00000", "01000", "11111", "01000"]


def base(skin_name, size=(32, 48), fill=None):
    skin = SKINS[skin_name]
    image = Image.new("RGBA", size, skin["edge"])
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, size[0] - 2, size[1] - 2), fill=skin["paper"])
    draw.rectangle((2, 2, size[0] - 3, size[1] - 3), fill=fill or skin["back"])
    return image


def draw_value(draw, x, y, value, color, scale=2):
    if value == "skip":
        bitmap(draw, x, y, SKIP, color, scale)
    elif value == "reverse":
        bitmap(draw, x, y, REVERSE, color, scale)
    elif value == "W":
        pixel_text(draw, x, y, "W", color, scale, centered=False)
    else:
        mini_text(draw, x, y, "+2" if value == "draw_two" else "+4" if value == "wild_draw_four" else value, color, scale)


def corner(draw, value, color):
    """All corner ink lies at x=2..13, wholly within the exposed left 48%."""
    if value in ("draw_two", "wild_draw_four"):
        bitmap(draw, 2, 7, ["00100", "00100", "11111", "00100", "00100"], color)
        mini_text(draw, 8, 4, "2" if value == "draw_two" else "4", color, 2)
    elif value == "reverse":
        bitmap(draw, 3, 3, REVERSE, color, 2)
    elif value == "skip":
        bitmap(draw, 2, 4, SKIP, color, 2)
    elif value == "W":
        pixel_text(draw, 3, 3, "W", color, 2, centered=False)
    else:
        draw_value(draw, 3, 4, value, color)


def draw_colored(skin_name, color_name, value, gui=False):
    color = COLORS[color_name]
    image = base(skin_name, (16, 16) if gui else (32, 48), color["fill"])
    draw = ImageDraw.Draw(image)
    if gui:
        if value == "reverse":
            bitmap(draw, 3, 1, REVERSE, color["ink"], 2)
        elif value == "skip":
            bitmap(draw, 2, 2, SKIP, color["ink"], 2)
        elif value == "draw_two":
            mini_text(draw, 1, 3, "+2", color["ink"], 2)
        else:
            mini_text(draw, 8, 3, value, color["ink"], 2, centered=True)
    else:
        corner(draw, value, color["ink"])
        mini_text(draw, 3, 19, color["letter"], color["ink"], 2)
        if value.isdigit():
            pixel_text(draw, 22, 28, value, color["ink"], 2)
        elif value == "draw_two":
            mini_text(draw, 14, 31, "+2", color["ink"], 2)
        elif value == "skip":
            bitmap(draw, 16, 31, SKIP, color["ink"], 2)
        else:
            bitmap(draw, 16, 28, REVERSE, color["ink"], 2)
    return image


def four_colors(draw, left, top, size):
    for index, color in enumerate(COLORS.values()):
        x = left + (index % 2) * size
        y = top + (index // 2) * size
        draw.rectangle((x, y, x + size - 1, y + size - 1), fill=color["fill"])


def draw_wild(skin_name, draw_four=False, gui=False):
    image = base(skin_name, (16, 16) if gui else (32, 48), INK)
    draw = ImageDraw.Draw(image)
    if gui:
        four_colors(draw, 2, 2, 6)
        if draw_four:
            draw.rectangle((3, 5, 12, 11), fill=INK)
            mini_text(draw, 8, 6, "+4", WHITE, centered=True)
        else:
            draw.rectangle((4, 2, 11, 13), fill=INK)
            pixel_text(draw, 8, 4, "W", WHITE, centered=True)
    else:
        corner(draw, "wild_draw_four" if draw_four else "W", WHITE)
        four_colors(draw, 3, 19, 5)
        four_colors(draw, 13, 30, 8)
    return image


def draw_back(skin_name, gui=False):
    skin = SKINS[skin_name]
    image = base(skin_name, (16, 16) if gui else (32, 48))
    draw = ImageDraw.Draw(image)
    if gui:
        four_colors(draw, 4, 4, 4)
    else:
        for y in range(7, 43, 6):
            for x in range(6 + (3 if y % 12 else 0), 28, 6):
                draw.rectangle((x, y, x, y), fill=skin["edge"])
        draw.rectangle((7, 15, 24, 32), fill=skin["back"])
        four_colors(draw, 8, 16, 8)
    return image


def draw_button(skin_name, action):
    _, color = BUTTONS[action]
    image = base(skin_name, (16, 16), color)
    draw = ImageDraw.Draw(image)
    ink = INK if action == "color_yellow" else WHITE
    if action.startswith("color_"):
        mini_text(draw, 8, 3, COLORS[action.removeprefix("color_")]["letter"], ink, 2, centered=True)
    elif action == "uno":
        mini_text(draw, 8, 3, "1", ink, 2, centered=True)
    else:
        patterns = {
            "draw": ["111111000", "100001000", "101111111", "101000001", "101000001", "101000001", "111000001", "001000001", "001111111"],
            "catch": ["011110000", "110011000", "100001000", "100001000", "110011000", "011111000", "000011100", "000001110", "000000110"],
            "challenge": ["111", "001", "011", "010", "000", "010"],
            "accept": ["000000001", "000000011", "000000110", "000001100", "110011000", "011110000", "001100000"],
        }
        scale = 2 if action == "challenge" else 1
        bitmap(draw, 5 if scale == 2 else 3, 2 if scale == 2 else 4, patterns[action], ink, scale)
    return image


def model(asset, back, button=False):
    width, height = (16, 16) if button else (10, 15)
    return {
        "gui_light": "front", "ambientocclusion": False,
        "textures": {"front": f"uno:item/{asset}", "back": f"uno:item/{back}", "edge": "uno:item/card_edge", "particle": f"uno:item/{asset}"},
        "elements": [{"from": [8 - width / 2, 8 - height / 2, 7.97], "to": [8 + width / 2, 8 + height / 2, 8.03], "shade": False,
            "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#front"}, "south": {"uv": [0, 0, 16, 16], "texture": "#back"},
                "up": {"uv": [0, 0, 16, 1], "texture": "#edge"}, "down": {"uv": [0, 0, 16, 1], "texture": "#edge"},
                "east": {"uv": [0, 0, 1, 16], "texture": "#edge"}, "west": {"uv": [0, 0, 1, 16], "texture": "#edge"}}}],
        "display": {
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [.5, .5, .5]},
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2, 1], "scale": [.5, .5, .5]},
            "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.7, .7, .7]},
        },
    }


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def preview(images):
    image = Image.new("RGB", (1920, 1390), "#14231f")
    draw = ImageDraw.Draw(image)
    label(draw, (60, 44), "UNO 像素牌 · 大色块、大牌值与完整露出的角标", 34, "#f2eedf", True, "lm")
    label(draw, (60, 90), "世界纸牌 32 × 48 · 独立菜单符号 16 × 16 · 浅纸边 · 以下放大均为最近邻", 24, "#adc9bd", True, "lm")
    samples = ["red_0", "yellow_7", "green_skip", "blue_reverse", "red_draw_two", "wild", "wild_draw_four", "back"]
    for row, skin_name in enumerate(SKINS):
        y = 132 + row * 248
        label(draw, (60, y), SKINS[skin_name]["name"], 24, "#f2eedf", True, "lm")
        for index, asset in enumerate(samples):
            thumb = images[f"{skin_name}_{asset}"].resize((128, 192), Image.Resampling.NEAREST)
            image.paste(thumb, (60 + index * 225, y + 24), thumb)
    label(draw, (60, 644), "菜单图标 · 16 × 16 方形符号", 24, "#f2eedf", True, "lm")
    for index, asset in enumerate(samples):
        thumb = images[f"classic_{asset}_gui"].resize((96, 96), Image.Resampling.NEAREST)
        image.paste(thumb, (60 + index * 225, 677), thumb)
    label(draw, (60, 810), "操作按钮 · 摸牌 / UNO / 抓漏喊 / 质疑 / 接受 / 四色选择", 24, "#f2eedf", True, "lm")
    for index, action in enumerate(BUTTONS):
        thumb = images[f"classic_button_{action}"].resize((64, 64), Image.Resampling.NEAREST)
        image.paste(thumb, (60 + index * 155, 844), thumb)
    label(draw, (60, 958), "两排各 10 张 · 遮挡约 52% 后，左侧牌值与色字仍完整露出", 24, "#f2eedf", True, "lm")
    fan_samples = ["red_0", "red_1", "red_2", "yellow_7", "yellow_draw_two", "green_skip", "green_3", "blue_reverse", "wild", "wild_draw_four"]
    for column, skin_name in enumerate(SKINS):
        for row in range(2):
            for index, asset in enumerate(fan_samples):
                thumb = images[f"{skin_name}_{asset}"].resize((96, 144), Image.Resampling.NEAREST)
                image.paste(thumb, (60 + column * 940 + index * 46, 1000 + row * 155), thumb)
    label(draw, (60, 1340), "整数像素与有限色板 · 无小英文、金框、渐变或抗锯齿 · GUI 使用独立生成模型", 23, "#adc9bd", True, "lm")
    image.save(ROOT / "uno-assets-preview.png")


def main():
    textures = ASSETS / "textures" / "item"
    models = ASSETS / "models" / "item"
    configuration = PACK / "configuration"
    for directory in (textures, models, configuration):
        directory.mkdir(parents=True, exist_ok=True)
    items, images = {}, {}

    def item(skin_name, suffix, image, gui_image, display_name, button=False):
        asset = f"{skin_name}_{suffix}"
        images[asset], images[asset + "_gui"] = image, gui_image
        image.save(textures / f"{asset}.png")
        gui_image.save(textures / f"{asset}_gui.png")
        save_json(models / f"{asset}.json", model(asset, asset if button else f"{skin_name}_back", button))
        save_json(models / f"{asset}_gui.json", gui_model("uno", asset))
        # Existing doudizhu skin aliases are retained; artwork lives in uno.
        item_id = f"doudizhu:{skin_name}_uno_{suffix}"
        items[item_id] = {"material": "paper", "data": {"item_name": "<!i><white>" + display_name},
            "model": context_model("uno", asset)}

    for skin_name in SKINS:
        for color_name, color in COLORS.items():
            for value in VALUES:
                item(skin_name, f"{color_name}_{value}", draw_colored(skin_name, color_name, value),
                    draw_colored(skin_name, color_name, value, gui=True), f"{color['name']}色 {VALUE_NAMES.get(value, value)}")
        item(skin_name, "wild", draw_wild(skin_name), draw_wild(skin_name, gui=True), "变色牌")
        item(skin_name, "wild_draw_four", draw_wild(skin_name, True), draw_wild(skin_name, True, gui=True), "变色加四")
        item(skin_name, "back", draw_back(skin_name), draw_back(skin_name, gui=True), SKINS[skin_name]["name"] + "四色牌背")
        for action, (title, _) in BUTTONS.items():
            item(skin_name, "button_" + action, draw_button(skin_name, action), draw_button(skin_name, action), title, button=True)
    Image.new("RGBA", (16, 16), SKINS["classic"]["paper"]).save(textures / "card_edge.png")
    (configuration / "items.yml").write_text(yaml.safe_dump({"items": items}, allow_unicode=True, sort_keys=False), encoding="utf-8")
    (PACK / "pack.yml").write_text(yaml.safe_dump({"author": "CardTable original pixel assets", "namespace": "uno", "description": "原创 Minecraft 像素四色牌，32x48世界牌与16x16独立菜单图标", "version": "2.0.0"}, allow_unicode=True, sort_keys=False), encoding="utf-8")
    save_json(PACK / "metadata.json", {"item_ids": list(items), "skins": list(SKINS), "colors": list(COLORS), "face_types_per_skin": 54,
        "card_size_pixels": [32, 48], "gui_size_pixels": [16, 16], "button_size_pixels": [16, 16],
        "card_geometry": {"front": "north / local -Z", "width_blocks": .625, "height_blocks": .9375, "thickness_blocks": .00375, "fixed_transform": "identity", "corner_max_x_pixel": 13, "corner_order": "value above color letter"},
        "button_geometry": {"width_blocks": 1, "height_blocks": 1}, "gui_model": "minecraft:item/generated, selected by minecraft:display_context == gui",
        "art": "Original integer pixel shapes and bitmap glyphs; no sampled images, commercial card artwork or UNO logo"})
    preview(images)
    print(f"Generated {len(items)} CE items, {len(images) + 1} original pixel textures and {len(items) * 2} models.")


if __name__ == "__main__":
    main()
