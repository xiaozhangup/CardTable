# 原创牌类游戏资源

把本目录复制为 `plugins/CraftEngine/resources/doudizhu/`，执行 `ce reload all`，并让客户端接受更新后的资源包。`configuration/items.yml` 使用 CraftEngine 26.10 的 `minecraft:select` 与 `minecraft:display_context`：菜单中的 `gui` 场景选用独立图标，其他场景使用薄纸牌模型。两类模型引用预先生成的 JSON，菜单图标使用原版 `minecraft:item/generated`。

包含两套各 54 张的标准扑克与牌背：`classic` 为暖白纸面、海军蓝墨色与牌背，`jade` 为轻微青白纸面、深青墨色与牌背；红色花色保持同一辨识色。卡牌通用，可用于后续其他牌类游戏。世界卡牌和牌背为原生 **32 × 48** 像素，独立菜单图标与方形按钮为 **16 × 16** 像素，桌面为 **64 × 64** 像素，边缘材质为 **16 × 16** 像素。

所有素材按 Minecraft 像素风逐格绘制：角标使用紧凑 bitmap 字形，以 3 × 5 为主，Q 使用 5 列轮廓与斜尾；中央牌点使用清晰的大字，J/Q/K 不再共用皇冠。角标点数与花色完整落在牌面左侧第 2..11 列，在两张牌重叠后露出约 48% 宽度的布局中仍可识别。大王为红色王冠、小王为蓝色小丑，牌面去掉英文说明。按钮使用原版界面风格的方形图标，中文动作由插件的桌面标签、物品名称和 lore 提供。桌面只有深绿毡面与细木边。

世界牌、菜单图标均是分别生成的原生像素图，菜单图标没有从整张纸牌缩小采样。绘图只用整数像素，不使用抗锯齿、渐变或外部图片。Noto 字体仅用于预览图中的说明文字，不打包到游戏资源。`assets-preview.png` 同时显示世界牌、真实 16 × 16 菜单图标与最近邻放大图；这些是静态美术产物。

## 物品 ID

- 牌：`doudizhu:{classic|jade}_{spade|heart|club|diamond}_{3|4|5|6|7|8|9|10|j|q|k|a|2}`。
- 王：`doudizhu:{classic|jade}_joker_small`、`doudizhu:{classic|jade}_joker_big`。
- 牌背：`doudizhu:classic_back`、`doudizhu:jade_back`。
- 按钮：`ready`、`play`、`pass`、`hint`、`leave`、`bid_0`、`bid_1`、`bid_2`、`bid_3`、`music`、`skin`、`table`，均使用 `doudizhu:` 前缀。
- 真实水平桌面模型：`doudizhu:tabletop`。`table` 是建桌菜单按钮，两者用途不同。

所有 123 个物品 ID 与尺寸在 `metadata.json` 中列出。每个卡牌或按钮额外提供 `_gui.png` 和 `_gui.json`，它们是同一物品的菜单表现，不增加物品 ID；水平桌面仍使用原来的专用模型。

## 牌与桌面朝向

牌使用薄长方体，局部 X 为宽、Y 为高，局部 -Z（north）是正面，+Z（south）是对应皮肤的牌背。FIXED 变换保持单位变换。默认牌宽 0.625 方块、高 0.9375 方块、厚 0.00375 方块；方形按钮宽高均为 1 方块，厚度相同。ItemDisplay 的旋转、缩放与选牌抬升由插件设置。世界物品模型设置 `shade: false`，避免方向阴影掩盖牌点与花色。

`tabletop` 为水平 XZ 平面的木框绿绒牌桌，宽深各 2 方块，厚 0.0625 方块，几何中心在 ItemDisplay 原点；配置实体缩放即可调整桌子大小。桌腿由插件布局决定。

## 声音

`resourcepack/assets/doudizhu/sounds.json` 定义 `doudizhu:voice.{event}`，声音可直接用 Bukkit 自定义 Sound 字符串播放，不需要调用 CraftEngine API。资源包必须已加载到客户端。

提供 24 个游戏提示：`single`、`pair`、`triple`、`triple_single`、`triple_pair`、`straight`、`pair_straight`、`airplane`、`airplane_single`、`airplane_pair`、`four_single`、`four_pair`、`bomb`、`rocket`、`pass`、`bid_0..3`、`start`、`win`、`lose`、`turn`、`wild`。另外提供 `rank_3..10`、`rank_j`、`rank_q`、`rank_k`、`rank_a`、`rank_2`、`rank_joker_small`、`rank_joker_big`。

共 39 个真实音频文件，均为单声道 22.05 kHz Vorbis OGG，带中文字幕。语音由本机 eSpeak NG 的 `cmn` 声线合成，经 FFmpeg 归一化处理，声音有明显合成音色；不是卢姥爷真人录音。可以替换相同文件名或添加其他声音事件。

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
cp /tmp/doudizhu-music-preview/preview.wav craftengine/doudizhu/music-preview.wav
```

## 重生成

重生成当前视觉资源时，从项目根目录运行 `python3 tools/build_assets.py --visuals-only`。需要 Pillow、PyYAML 与系统 Noto CJK 字体；工具重建原创 PNG、世界模型、菜单图标模型、物品配置、元数据与 `assets-preview.png`，保留 OGG、声音事件、语言文件与音乐 YAML，不执行语音合成。

`python3 tools/build_assets.py --art-only` 仅重绘 PNG 与预览，不更新模型或配置；修改模型尺寸或菜单选择方式后应使用 `--visuals-only`。

完整重生成使用 `python3 tools/build_assets.py`，另外需要 eSpeak NG 和 FFmpeg，会重写生成的物品、纹理、模型、声音与旋律 YAML。自定义皮肤请使用新的 skin 名称和独立配置，避免直接覆盖后再运行生成工具。

这次交付只生成视觉资源、阅读源码和查看静态预览，没有运行测试、构建、客户端或服务器验证。CE 菜单选择格式依据本地 `SelectItemModel`、`DisplayContextSelectProperty` 与 `BaseItemModel` 源码；实际资源包加载、菜单渲染、桌面遮挡、点击和声音播放仍需在 Minecraft 客户端中验收。
