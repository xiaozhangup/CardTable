# 扑克与斗地主语音资源

本节说明统一 [CardTable 资源包](../README.md) 内的扑克、通用按钮与音效。部署时复制整个 `craftengine/cardtable/` 到 `plugins/CraftEngine/resources/cardtable/`，执行 `ce reload all`，并让客户端接受更新后的资源包。下文文件路径均相对统一包根目录。

`configuration/doudizhu.yml` 直接使用 `minecraft:model` 引用薄纸牌模型。插件菜单使用原版物品，独立 GUI 贴图与模型已移除。通过 CE 获取卡牌物品时，物品栏沿用同一模型及其 `display.gui` 变换。

包含两套各 54 张的标准扑克与牌背：`classic` 为暖白纸面、海军蓝墨色与牌背，`jade` 为轻微青白纸面、深青墨色与牌背；红色花色保持同一辨识色。卡牌通用，可用于后续其他牌类游戏。世界卡牌和牌背为原生 **32 × 48** 像素，方形按钮为 **16 × 16** 像素，桌面为 **64 × 64** 像素，边缘材质为 **16 × 16** 像素。

视觉素材按 Minecraft 像素风逐格绘制：角标使用紧凑 bitmap 字形，以 3 × 5 为主，Q 使用 5 列轮廓与斜尾；中央牌点使用清晰的大字，J/Q/K 不再共用皇冠。角标点数与花色完整落在牌面左侧第 2..11 列，在两张牌重叠后露出约 48% 宽度的布局中仍可识别。大王为红色王冠、小王为蓝色小丑，牌面去掉英文说明。按钮使用原版界面风格的方形图标，中文动作由插件的桌面标签、物品名称和 lore 提供。桌面只有深绿毡面与细木边。

世界牌使用原生像素图。绘图只用整数像素，不使用抗锯齿、渐变或外部图片。Noto 字体仅用于预览图中的说明文字，不打包到游戏资源。运行生成脚本后，项目的 `build/previews/assets-preview.png` 会显示世界牌、按钮与最近邻放大图；这些是静态美术产物。

## 物品 ID

- 牌：`doudizhu:{classic|jade}_{spade|heart|club|diamond}_{3|4|5|6|7|8|9|10|j|q|k|a|2}`。
- 王：`doudizhu:{classic|jade}_joker_small`、`doudizhu:{classic|jade}_joker_big`。
- 牌背：`doudizhu:classic_back`、`doudizhu:jade_back`。
- 按钮：`ready`、`play`、`pass`、`hint`、`leave`、`bid_0`、`bid_1`、`bid_2`、`bid_3`、`music`、`skin`、`table`，均使用 `doudizhu:` 前缀。
- 旧版水平桌面模型：`doudizhu:tabletop`，保留 ID 与资源兼容。`table` 是建桌菜单按钮，两者用途不同；当前整桌外观使用 `cardtable:table_6` 等家具模型。

所有 123 个物品 ID 与尺寸在 `metadata/doudizhu.json` 中列出。共 123 个模型和 125 张 PNG；不再提供 `_gui.png` 或 `_gui.json`，旧水平桌面的专用模型仍保留。

## 牌与桌面朝向

牌使用薄长方体，局部 X 为宽、Y 为高，局部 -Z（north）是正面，+Z（south）是对应皮肤的牌背。FIXED 变换保持单位变换。默认牌宽 0.625 方块、高 0.9375 方块、厚 0.00375 方块；方形按钮宽高均为 1 方块，厚度相同。ItemDisplay 的旋转、缩放与选牌抬升由插件设置。世界物品模型设置 `shade: false`，避免方向阴影掩盖牌点与花色。

保留的 `tabletop` 是水平 XZ 平面的木框绿绒桌板，宽深各 2 方块，厚 0.0625 方块，几何中心在 ItemDisplay 原点。当前桌腿、桌面统一使用 [整体家具模型](../README.md#家具呈现)。

## 声音

`resourcepack/assets/doudizhu/sounds.json` 定义 `doudizhu:voice.{female|male}.{event}`，声音可直接用 Bukkit 自定义 Sound 字符串播放，不需要调用 CraftEngine API。资源包必须已加载到客户端。插件配置 `doudizhu-voice: female` 默认使用女声，可改为 `male`；`voice: false` 关闭语音。

男女两套各包含 53 个声音事件、54 个音频文件：

- `single_3..17` 播报单张；3～10 保持原值，11/12/13/14/15/16/17 依次表示 J/Q/K/A/2/小王/大王。
- `pair_3..15` 与 `triple_3..15` 播报对应点数的对子和三张。
- `triple_single`、`triple_pair`、`straight`、`pair_straight`、`airplane`、`four_single`、`four_pair`、`bomb`、`rocket` 播报牌型。飞机、飞机带单牌、飞机带对子共用 `airplane`。
- `bid_0` 为不叫，`bid_call` 为叫地主。原音频没有一分、两分、三分台词，正分叫分统一使用叫地主，不将抢地主的多个录音变体当作分数。
- `pass` 从“不要”和“过”两段声音中随机选择，字幕统一写“不出”，避免把主动不出误称为要不起。

没有开局和结算的人声录音，因此这两处停播；胜出者最后一次出牌仍正常播报。未接入闲聊、催促、剩余手牌提醒或上游背景音乐。字幕由上游声音映射及原中文文件名核对，描述游戏事件，不作为逐字听写。

共 108 个 OGG，均为单声道 44.1 kHz Vorbis。转码只进行单声道混合、固定增益调整（峰值目标 -2 dBFS，最多提升 6 dB）、重采样与编码，不添加滤波、降噪或动态压缩。旧的 39 个 eSpeak 合成音频及其事件已移除。

录音来自 [palemoky/fight-the-landlord](https://github.com/palemoky/fight-the-landlord)，固定提交 `6a2b07bd71a21e0c564277a235846e7f3cf589b1`。上游仓库附带 GPL-3.0 全文，但未确认原始录音作者或音频单独授权；本包不将这些录音标为原创。详见 [来源说明](../licenses/fight-the-landlord/NOTICE.txt) 与 [完整许可证](../licenses/fight-the-landlord/LICENSE.txt)。

逐文件上游路径、原 MP3 SHA-256、语义字幕和转换参数位于 `metadata/doudizhu-voices.json`。未修改的原 MP3 保留在 `sources/fight-the-landlord/internal/sound/gaming/voices/`，随 CE 分发包提供，不进入客户端资源包。客户端资源包另带 `licenses/fight-the-landlord/` 下的许可证、说明和来源清单。

## 原创桌面音乐

`music.yml` 为原创 16 拍五声音阶小曲《青玉牌桌》，100 BPM，9.6 秒循环。播放器应每个游戏 tick 检查事件，达到 `length-ticks: 192` 后从 0 重新循环：

```yaml
title: 青玉牌桌
tempo: 100
length-ticks: 192
events:
  - tick: 0
    instrument: harp
    note: 6
    volume: 0.5
```

`instrument` 仅有 `harp` 与 `bass`，对应 Bukkit `Sound.BLOCK_NOTE_BLOCK_HARP`、`Sound.BLOCK_NOTE_BLOCK_BASS`。`note` 是原版音符盒 0..24，实际播放 pitch 为 `2 ** ((note - 12) / 12)`。音符时间在全部声部间共用。`music-preview.wav` 为使用原版音符盒采样制作的试听，保留自然衰减；不把试听 WAV 加入游戏包。

原始谱面为项目 `tools/table_music.score`，试听通过 noteblock-music 技能生成。需要重新生成试听时：

```sh
python3 /home/xiaozhangup/.codex/skills/noteblock-music/scripts/noteblock_music.py build tools/table_music.score --out /tmp/doudizhu-music-preview --overwrite
cp /tmp/doudizhu-music-preview/preview.wav craftengine/cardtable/music-preview.wav
```

## 重生成

重生成当前视觉资源时，从项目根目录运行 `python3 tools/build_assets.py --visuals-only`。需要 Pillow、PyYAML 与系统 Noto CJK 字体；工具重建原创 PNG、世界模型、菜单图标模型、物品配置、元数据与项目 `build/previews/assets-preview.png`，保留 OGG、声音事件、语言文件与音乐 YAML，不执行语音合成。

`python3 tools/build_assets.py --art-only` 仅重绘 PNG 与预览，不更新模型或配置；修改模型尺寸或菜单选择方式后应使用 `--visuals-only`。

完整重生成使用 `python3 tools/build_assets.py`，会重写生成的扑克物品、纹理、模型与旋律 YAML，并保留现有语音、字幕和来源元数据。输出统一位于 `craftengine/cardtable/`，配置为 `configuration/doudizhu.yml`，元数据为 `metadata/doudizhu.json`；不会重写 `pack.yml`、UNO 或家具资源。自定义皮肤请使用新的 skin 名称和独立配置，避免直接覆盖后再运行生成工具。

语音单独运行 `python3 tools/import_doudizhu_voices.py` 重建，需要 FFmpeg。脚本先使用随包附带的原 MP3，缺失时从清单固定提交下载；所有输入必须通过清单的 SHA-256 校验，然后生成 OGG、声音事件、字幕与许可副本。该脚本管理整个 `doudizhu/sounds/voice/`，自定义语音应使用自己的目录和声音命名空间。

CE 菜单选择格式依据本地 `SelectItemModel`、`DisplayContextSelectProperty` 与 `BaseItemModel` 源码；静态资源检查不代替 Minecraft 客户端中的实际加载、视觉和声音验收。
