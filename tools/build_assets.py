#!/usr/bin/env python3
"""Regenerate original Minecraft pixel card art, flat models and note-block music.

Use --visuals-only to regenerate art, models and item definitions while preserving
audio/music; --art-only redraws only PNGs and the preview.
Dependencies: Python 3, Pillow, PyYAML and Noto CJK fonts.
Imported voices are preserved in every mode; rebuild them with import_doudizhu_voices.py.
All drawings and the melody are authored here; no reference image is sampled.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
import yaml

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "craftengine" / "cardtable"
ASSETS = PACK / "resourcepack" / "assets" / "doudizhu"
LATIN = Path("/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf")
CHINESE = Path("/usr/share/fonts/google-noto-sans-cjk-fonts/NotoSansCJK-Bold.ttc")
RANKS = ["3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k", "a", "2"]
SUITS = {"spade": "黑桃", "heart": "红桃", "club": "梅花", "diamond": "方块"}
SKINS = {
    "classic": {"name": "经典", "paper": "#fff9ea", "ink": "#223348", "red": "#b92f3c", "accent": "#b8b4a6", "back": "#243b58"},
    "jade": {"name": "青玉", "paper": "#edf7ee", "ink": "#174b40", "red": "#b92f3c", "accent": "#a4b8aa", "back": "#184b44"},
}
BUTTONS = {
    "ready": ("准备", "#218c65", "check"),
    "play": ("出牌", "#cb9b36", "cards"),
    "pass": ("不出", "#6d587e", "pass"),
    "hint": ("提示", "#367aa0", "bulb"),
    "leave": ("离桌", "#a85755", "exit"),
    "bid_0": ("不叫", "#647080", "0"),
    "bid_1": ("叫一分", "#428772", "1"),
    "bid_2": ("叫两分", "#417b99", "2"),
    "bid_3": ("叫三分", "#ba873d", "3"),
    "music": ("音乐", "#397788", "music"),
    "skin": ("牌面", "#508069", "cards"),
    "table": ("牌桌", "#80683e", "table"),
}


# Native low-resolution assets. No resampling, vector curves, or blended edges.
CARD_SIZE = (32, 48)
BUTTON_SIZE = (16, 16)
GLYPHS = {
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "10000", "11110", "00001", "00001", "11110"],
    "6": ["01110", "10000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00001", "01110"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01111", "10000", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00010", "00010", "00010", "10010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "10101", "01010"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    " ": ["00000"] * 7,
}
SUIT_PIXELS = {
    "spade": ["000010000", "000111000", "001111100", "011111110", "111111111", "111111111", "011111110", "000111000", "001111100"],
    "heart": ["011000110", "111101111", "111111111", "111111111", "011111110", "001111100", "000111000", "000010000", "000000000"],
    "club": ["000111000", "001111100", "001111100", "110111011", "111111111", "111111111", "011111110", "000111000", "001111100"],
    "diamond": ["000010000", "000111000", "001111100", "011111110", "111111111", "011111110", "001111100", "000111000", "000010000"],
}
# Compact symbols keep the corner inside the exposed half of a card.
MINI_GLYPHS = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["1", "1", "1", "1", "1"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "001", "001", "001"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
    "A": ["010", "101", "111", "101", "101"],
    "J": ["111", "001", "001", "101", "111"],
    "Q": ["01110", "10001", "10101", "10010", "01101"],
    "K": ["101", "101", "110", "101", "101"],
    "+": ["000", "010", "111", "010", "000"],
    "R": ["110", "101", "110", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"],
    "G": ["111", "100", "101", "101", "111"],
    "B": ["110", "101", "110", "101", "110"],
    "W": ["101", "101", "101", "111", "101"],
}
MINI_SUITS = {
    "spade": ["00100", "01110", "11111", "00100", "01110"],
    "heart": ["01010", "11111", "11111", "01110", "00100"],
    "club": ["00100", "01110", "10101", "11111", "00100"],
    "diamond": ["00100", "01110", "11111", "01110", "00100"],
}


def bitmap(draw, x, y, pixels, color, scale=1):
    for row, line in enumerate(pixels):
        for col, bit in enumerate(line):
            if bit == "1":
                left, top = x + col * scale, y + row * scale
                draw.rectangle((left, top, left + scale - 1, top + scale - 1), fill=color)


def pixel_text(draw, x, y, text, color, scale=1, centered=True):
    text = text.upper()
    width = (len(text) * 6 - 1) * scale
    if centered:
        x -= width // 2
    for index, letter in enumerate(text):
        bitmap(draw, x + index * 6 * scale, y, GLYPHS[letter], color, scale)


def mini_text(draw, x, y, text, color, scale=1, centered=False):
    symbols = [MINI_GLYPHS[letter] for letter in text.upper()]
    width = (sum(len(symbol[0]) for symbol in symbols) + len(symbols) - 1) * scale
    if centered:
        x -= width // 2
    for symbol in symbols:
        bitmap(draw, x, y, symbol, color, scale)
        x += (len(symbol[0]) + 1) * scale


def font(size: int, cn: bool = False):
    return ImageFont.truetype(str(CHINESE if cn else LATIN), size)


def label(draw, xy, text, size, fill, cn=False, anchor="mm"):
    # Pillow's 1-bit text mask turns font rasterization into solid native pixels.
    draw.fontmode = "1"
    draw.text(xy, text, font=font(size, cn), fill=fill, anchor=anchor)


def suit(draw, name, cx, cy, scale, fill):
    bitmap(draw, cx - 9 * scale // 2, cy - 9 * scale // 2, SUIT_PIXELS[name], fill, scale)


def base_card(skin):
    image = Image.new("RGBA", CARD_SIZE)
    d = ImageDraw.Draw(image)
    d.rectangle((1, 0, 30, 47), fill=skin["accent"])
    d.rectangle((0, 1, 31, 46), fill=skin["accent"])
    d.rectangle((1, 1, 30, 46), fill=skin["paper"])
    return image


def corners(image, rank, name, color):
    d = ImageDraw.Draw(image)
    mini_text(d, 2, 3, rank, color, 2)
    bitmap(d, 2, 16, MINI_SUITS[name], color, 2)


def draw_card(skin_name, name, rank):
    skin = SKINS[skin_name]
    image = base_card(skin)
    d = ImageDraw.Draw(image)
    color = skin["red"] if name in ("heart", "diamond") else skin["ink"]
    corners(image, rank, name, color)
    # A second large rank replaces dense pip patterns and identical court crowns.
    if rank == "10":
        mini_text(d, 21, 23, rank, color, 3, centered=True)
    else:
        pixel_text(d, 22, 19, rank, color, 3)
    bitmap(d, 20, 41, MINI_SUITS[name], color)
    return image


def draw_joker(skin_name, big):
    skin = SKINS[skin_name]
    image = base_card(skin)
    d = ImageDraw.Draw(image)
    ink = skin["red"] if big else skin["ink"]
    mini_text(d, 2, 3, "R" if big else "B", ink, 2)
    bitmap(d, 2, 16, ["00100", "01110", "11111", "01110", "00100"], ink, 2)
    # Red king and blue jester have different silhouettes as well as colours.
    if big:
        bitmap(d, 12, 21, ["100010001", "110111011", "111111111", "011111110", "011111110"], ink, 2)
        d.rectangle((16, 34, 25, 41), fill=ink)
        d.rectangle((18, 36, 19, 37), fill=skin["paper"])
        d.rectangle((22, 36, 23, 37), fill=skin["paper"])
        d.rectangle((19, 40, 22, 40), fill=skin["paper"])
    else:
        bitmap(d, 12, 21, ["001000100", "011101110", "111111111", "100111001", "000111000"], ink, 2)
        d.rectangle((16, 34, 25, 41), fill=ink)
        d.rectangle((18, 36, 19, 37), fill=skin["paper"])
        d.rectangle((22, 36, 23, 37), fill=skin["paper"])
        d.rectangle((18, 39, 18, 39), fill=skin["paper"])
        d.rectangle((19, 40, 22, 40), fill=skin["paper"])
        d.rectangle((23, 39, 23, 39), fill=skin["paper"])
    return image


def draw_back(skin_name):
    skin = SKINS[skin_name]
    image = base_card(skin)
    d = ImageDraw.Draw(image)
    d.rectangle((2, 2, 29, 45), fill=skin["back"])
    for y in range(7, 43, 6):
        for x in range(6 + (3 if y % 12 else 0), 28, 6):
            d.rectangle((x, y, x, y), fill=skin["accent"])
    d.rectangle((9, 17, 22, 30), fill=skin["back"])
    bitmap(d, 11, 19, MINI_SUITS["spade"], skin["paper"], 2)
    return image


def draw_button(text, color, kind):
    image = Image.new("RGBA", BUTTON_SIZE, "#20252b")
    d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 14, 14), fill=color)
    d.line((1, 1, 14, 1), fill="#ced5ce")
    d.line((1, 1, 1, 14), fill="#ced5ce")
    d.line((2, 14, 14, 14), fill="#384442")
    d.line((14, 2, 14, 14), fill="#384442")
    symbols = {
        "check": ["000000001", "000000011", "000000110", "100001100", "110011000", "011110000", "001100000"],
        "cards": ["111111000", "100001000", "100111111", "100100001", "100100001", "100100001", "111100001", "000111111"],
        "pass": ["00111100", "01000010", "10000101", "10001001", "10010001", "10100001", "01000010", "00111100"],
        "bulb": ["00111100", "01100110", "01000010", "01000010", "01100110", "00100100", "00111100", "00100100", "00111100"],
        "music": ["00111111", "00100001", "00100001", "00100001", "00100001", "00100111", "11100111", "11100010", "11000000"],
        "exit": ["111100000", "100100100", "100100110", "100111111", "100100110", "100100100", "111100000"],
        "table": ["1111111111", "1000000001", "1111111111", "0100000010", "0100000010", "0100000010"],
    }
    if kind in symbols:
        pixels = symbols[kind]
        bitmap(d, (16 - len(pixels[0])) // 2, (16 - len(pixels)) // 2, pixels, "#fff9ed")
    else:
        mini_text(d, 8, 3, kind, "#fff9ed", 2, centered=True)
    return image


def draw_table():
    image = Image.new("RGBA", (64, 64), "#3c2c22")
    d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 62, 62), fill="#79583c")
    d.rectangle((2, 2, 61, 61), outline="#98724f")
    d.rectangle((4, 4, 59, 59), fill="#123f34")
    for y in range(6, 59, 4):
        for x in range(6 + (y % 3), 59, 7):
            d.point((x, y), fill="#154438")
    return image


def save_json(path, obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")


def card_model(asset_id, back_id, button=False):
    # North is the face, south is the reverse. Fixed transform is identity.
    w,h=(16,16) if button else (10,15)
    return {
        "gui_light":"front", "ambientocclusion":False,
        "textures":{"front":f"doudizhu:item/{asset_id}","back":f"doudizhu:item/{back_id}","edge":"doudizhu:item/card_edge","particle":f"doudizhu:item/{asset_id}"},
        "elements":[{"from":[8-w/2,8-h/2,7.97],"to":[8+w/2,8+h/2,8.03],"shade":False,
            "faces":{
                "north":{"uv":[0,0,16,16],"texture":"#front"},
                "south":{"uv":[0,0,16,16],"texture":"#back"},
                "up":{"uv":[0,0,16,1],"texture":"#edge"},
                "down":{"uv":[0,0,16,1],"texture":"#edge"},
                "east":{"uv":[0,0,1,16],"texture":"#edge"},
                "west":{"uv":[0,0,1,16],"texture":"#edge"}}}],
        "display":{
            "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},
            "gui":{"rotation":[0,180,0],"translation":[0,0,0],"scale":[1,1,1]},
            "ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[.5,.5,.5]},
            "thirdperson_righthand":{"rotation":[0,180,0],"translation":[0,2,1],"scale":[.5,.5,.5]},
            "firstperson_righthand":{"rotation":[0,180,0],"translation":[0,0,0],"scale":[.7,.7,.7]}}}


def music_assets():
    """A 16-beat original pentatonic miniature, 100 BPM, looping at tick 192."""
    melody=[(6,.5),(10,.5),(13,1),(10,.5),(8,.5),(6,1),(3,.5),(6,.5),(8,1),(10,1),(6,1),
            (13,.5),(15,.5),(18,1),(15,.5),(13,.5),(10,1),(8,.5),(10,.5),(6,1),(3,1),(6,1)]
    bass=[(6,2),(13,2),(3,2),(13,2),(6,2),(13,2),(3,2),(6,2)]
    events=[]
    lines=["@title 青玉牌桌", "@tempo 100"]
    for instrument,sequence,gain in [("harp",melody,.5),("bass",bass,.25)]:
        beat=0
        tokens=[]
        for note,length in sequence:
            events.append({"tick":round(beat*12),"instrument":instrument,"note":note,"volume":gain})
            beat+=length
            tokens.append(f"{note}:{length:g}")
        lines.extend([f"@track {instrument} {instrument} step=1 gain={gain}"," ".join(tokens),""])
    score="\n".join(lines)
    (ROOT/"tools"/"table_music.score").write_text(score,encoding="utf-8")
    music={"title":"青玉牌桌","author":"Doudizhu original","tempo":100,"length-ticks":192,"events":sorted(events,key=lambda e:e["tick"])}
    (PACK/"music.yml").write_text(yaml.safe_dump(music,allow_unicode=True,sort_keys=False),encoding="utf-8")


def preview(images):
    image = Image.new("RGB", (1920, 1075), "#14231f")
    d = ImageDraw.Draw(image)
    label(d, (50, 42), "Minecraft 像素牌 · 薄纸边、大牌点与清晰花色", 32, "#f2eedf", True, "lm")
    label(d, (50, 87), "世界牌 32 × 48 原生像素 · 桌面操作图标 16 × 16 · 以下放大均为最近邻", 22, "#aebfb1", True, "lm")
    samples = ["spade_a", "heart_10", "club_k", "diamond_q", "spade_j", "heart_2", "joker_small", "joker_big", "back"]
    for row, skin in enumerate(SKINS):
        y = 125 + row * 300
        label(d, (50, y), SKINS[skin]["name"], 22, "#f2eedf", True, "lm")
        for index, asset in enumerate(samples):
            x = 50 + index * 205
            thumb = images[f"{skin}_{asset}"].resize((160, 240), Image.Resampling.NEAREST)
            image.paste(thumb, (x, y + 23), thumb)
    label(d, (50, 747), "桌面方形操作图标 · 中文动作交给桌面标签", 22, "#aebfb1", True, "lm")
    for index, asset in enumerate(BUTTONS):
        x = 50 + (index % 12) * 152
        tile = images[asset]
        image.paste(tile.resize((80, 80), Image.Resampling.NEAREST), (x, 783))
        label(d, (x + 40, 888), BUTTONS[asset][0], 16, "#e2e7da", True)
    table = images["tabletop"].resize((128, 128), Image.Resampling.NEAREST)
    image.paste(table, (50, 925))
    label(d, (205, 955), "沉静深绿毡面与细木边，去掉桌心文字；牌面、按钮均只用整数像素。", 21, "#aebfb1", True, "lm")
    label(d, (205, 1000), "图标像素尺寸来自实际输出贴图；客户端朝向、遮挡与点击仍需在游戏内验收。", 20, "#aebfb1", True, "lm")
    preview_dir = ROOT / "build" / "previews"
    preview_dir.mkdir(parents=True, exist_ok=True)
    image.save(preview_dir / "assets-preview.png")


def art_assets():
    """Rewrite only original PNGs and the preview; keep models/config/audio intact."""
    textures = ASSETS / "textures" / "item"
    textures.mkdir(parents=True, exist_ok=True)
    images = {}
    for skin in SKINS:
        images[skin + "_back"] = draw_back(skin)
        for name in SUITS:
            for rank in RANKS:
                images[skin + "_" + name + "_" + rank] = draw_card(skin, name, rank)
        for big in (False, True):
            images[skin + "_joker_" + ("big" if big else "small")] = draw_joker(skin, big)
    for asset, (text, color, kind) in BUTTONS.items():
        images[asset] = draw_button(text, color, kind)
    images["tabletop"] = draw_table()
    images["card_edge"] = Image.new("RGBA", (16, 16), "#fff6dc")
    images["table_edge"] = Image.new("RGBA", (16, 16), "#714c30")
    for asset, image in images.items():
        image.save(textures / (asset + ".png"))
    preview(images)
    print(f"Rendered {len(images)} native pixel textures; models, items, sounds and music preserved.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--art-only", action="store_true", help="Redraw native pixel PNGs and preview only; preserve models/config/audio/music")
    mode.add_argument("--visuals-only", action="store_true", help="Regenerate art, models and item definitions; preserve voices, subtitles and music")
    args = parser.parse_args()
    if args.art_only:
        art_assets()
        return
    for directory in (ASSETS/"textures"/"item",ASSETS/"models"/"item",PACK/"configuration"):
        directory.mkdir(parents=True,exist_ok=True)
    images={}
    items={}
    def item(asset,image,display_name,back=None,button=False):
        images[asset]=image
        image.save(ASSETS/"textures"/"item"/(asset+".png"))
        save_json(ASSETS/"models"/"item"/(asset+".json"),card_model(asset,back or asset,button))
        items["doudizhu:"+asset]={"material":"paper","data":{"item_name":"<!i><white>"+display_name},"model":{"type":"minecraft:model","path":f"doudizhu:item/{asset}"}}
    for skin in SKINS:
        item(skin+"_back",draw_back(skin),SKINS[skin]["name"]+"牌背")
        for suit_name,cn in SUITS.items():
            for rank in RANKS:
                item(skin+"_"+suit_name+"_"+rank,draw_card(skin,suit_name,rank),cn+rank.upper(),skin+"_back")
        for big in (False,True):
            item(skin+"_joker_"+("big" if big else "small"),draw_joker(skin,big),"大王" if big else "小王",skin+"_back")
    for asset,(text,color,kind) in BUTTONS.items():
        item(asset,draw_button(text,color,kind),text,button=True)
    Image.new("RGBA",(16,16),"#fff6dc").save(ASSETS/"textures"/"item"/"card_edge.png")
    table=draw_table()
    images["tabletop"] = table
    table.save(ASSETS/"textures"/"item"/"tabletop.png")
    Image.new("RGBA",(16,16),"#714c30").save(ASSETS/"textures"/"item"/"table_edge.png")
    table_model={"gui_light":"front","ambientocclusion":False,
        "textures":{"top":"doudizhu:item/tabletop","side":"doudizhu:item/table_edge","particle":"doudizhu:item/tabletop"},
        "elements":[{"from":[-8,7.5,-8],"to":[24,8.5,24],"faces":{face:{"uv":[0,0,16,16],"texture":"#top" if face in ("up","down") else "#side"} for face in ("up","down","north","south","east","west")}}],
        "display":{"fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},"gui":{"rotation":[30,45,0],"translation":[0,0,0],"scale":[.5,.5,.5]}}}
    save_json(ASSETS/"models"/"item"/"tabletop.json",table_model)
    items["doudizhu:tabletop"]={"material":"paper","data":{"item_name":"<!i><green>青玉牌桌"},"model":{"type":"minecraft:model","path":"doudizhu:item/tabletop"}}
    (PACK/"configuration"/"doudizhu.yml").write_text(yaml.safe_dump({"items":items},allow_unicode=True,sort_keys=False),encoding="utf-8")
    # The shared pack.yml is maintained with the combined pack, not by either card generator.
    existing_metadata=json.loads((PACK/"metadata"/"doudizhu.json").read_text(encoding="utf-8"))
    voices=existing_metadata["voices"]
    if not args.visuals_only:
        music_assets()
    save_json(PACK/"metadata"/"doudizhu.json",{"item_ids":list(items),"skins":list(SKINS),"voices":voices,"voice_source":existing_metadata["voice_source"],
        "card_size_pixels":[32,48],"button_size_pixels":[16,16],"tabletop_size_pixels":[64,64],
        "corner_geometry":{"rank_and_suit_rightmost_pixel":11,"visible_width_fraction":.48,"ten_rank_rightmost_pixel":11},
        "card_geometry":{"front":"north / local -Z","width_blocks":.625,"height_blocks":.9375,"thickness_blocks":.00375,"fixed_transform":"identity"},
        "button_geometry":{"width_blocks":1,"height_blocks":1,"thickness_blocks":.00375,"fixed_transform":"identity"},
        "tabletop_geometry":{"width_blocks":2,"depth_blocks":2,"thickness_blocks":.0625}})
    preview(images)
    if args.visuals_only:
        print(f"Generated {len(items)} CE visual items; existing audio and music preserved.")
    else:
        print(f"Generated {len(items)} CE items and original melody; {len(voices)} imported voice events preserved.")


if __name__ == "__main__":
    main()
