# 原创四色像素牌

本节说明统一 [CardTable 资源包](../README.md) 内的 UNO 牌面。全部牌面由整数像素、方形色块和自绘 bitmap 字形生成，没有采样商业牌面、截图或 UNO 商标。部署时复制整个 `craftengine/cardtable/` 到 `plugins/CraftEngine/resources/cardtable/`，扑克、音效和家具已包含在同一包内；下文文件路径均相对统一包根目录。

世界卡牌为原生 **32 × 48** 像素，使用浅纸边和大面积红、黄、绿、蓝色底。左角先显示牌值或动作符号，下面再显示 `R`、`Y`、`G`、`B` 色字。角标全部位于横向第 `2..13` 像素，手牌按宽 0.4125 格、间距 0.20 格叠放时，露出的左侧约 48% 足以保留完整牌值和颜色。下半部另有较大的牌值或符号，用于查看完整牌。跳过为禁止符号，反转为两个反向箭头，罚抽为 `+2` 或 `+4`，万能牌为 `W` 与四色方块。

插件菜单使用原版物品，不再提供独立 GUI 贴图和模型。CE 物品直接引用原薄片模型，物品 ID 保持不变；通过 CE 获取卡牌物品时，物品栏沿用模型的 `display.gui` 变换。

`classic` 使用奶白纸边和深蓝牌背，`jade` 使用浅玉纸边和深绿牌背，两主题使用相同的四色识别色。每套 54 种牌面：四色各 `0..9`、`skip`、`reverse`、`draw_two`，另有 `wild` 与 `wild_draw_four`；重复牌共享牌面素材，实体牌编号由游戏管理。每套还有一张牌背、九个操作按钮：摸牌、喊 UNO、抓漏喊、质疑加四、接受罚牌和四色选择。

按钮改为原生 **16 × 16** 像素方形图，以象形符号表示操作，具体中文名称由菜单与世界文字展示。全套保持 **128 个 CE 物品**，生成 128 个世界模型和 129 张 PNG（包含一张 16 × 16 边缘贴图）。贴图无抗锯齿、渐变、小英文说明或复杂金框。

## ID 与模型

贴图与模型在 `uno:` 命名空间，CE 物品 ID 保持兼容 CardTable 的皮肤前缀：

- 有色牌：`doudizhu:{classic|jade}_uno_{red|yellow|green|blue}_{0..9|skip|reverse|draw_two}`。
- 万能牌：`doudizhu:{classic|jade}_uno_wild`、`doudizhu:{classic|jade}_uno_wild_draw_four`。
- 牌背：`doudizhu:{classic|jade}_uno_back`。
- 按钮：`doudizhu:{classic|jade}_uno_button_{draw|uno|catch|challenge|accept|color_red|color_yellow|color_green|color_blue}`。

UNO 提供者的 `CardFace.asset` 仍使用 `uno_red_0` 等后缀，展示层添加 `doudizhu:classic_` 或 `doudizhu:jade_`，没有新增 CE 物品别名。薄片模型的局部 -Z（north）为正面，+Z（south）为牌背，FIXED 为单位变换；原始模型宽 0.625 格、高 0.9375 格、厚 0.00375 格，世界实际尺寸由展示层缩放。按钮原始模型宽高均为 1 格。

## 重新生成

从项目根目录运行 `python3 tools/build_uno_assets.py`，需要 Python 3、Pillow、PyYAML 和系统 Noto CJK 字体，字体只用于预览标题。工具只更新统一包的 `resourcepack/assets/uno/`、`configuration/uno.yml`、`metadata/uno.json` 和项目的 `build/previews/uno-assets-preview.png`，不会重写 `pack.yml`、斗地主或家具资源。预览展示世界牌、按钮和牌面重叠示意，均使用最近邻放大。

预览属于素材的静态设计展示，实际 Minecraft 中的朝向、光照与操作仍以客户端为准。本资源不新增语音或音乐，UNO 沿用桌面音符盒音乐。
