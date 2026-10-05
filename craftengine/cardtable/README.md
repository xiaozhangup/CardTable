# CardTable 统一资源包

斗地主、UNO、桌椅和音效统一放在本目录。只需将整个 `cardtable/` 复制到 `plugins/CraftEngine/resources/cardtable/`，随后让 CraftEngine 重新加载并生成资源包。

从旧版升级时，部署完整的统一包后，移除 CE `resources` 下旧的 `doudizhu/`、`uno/` 包目录，避免重复注册同一批物品。本项目中的 `craftengine/` 已完成合并，只有 `cardtable/` 一个顶层资源包。

## 文件与命名空间

| 内容 | 物品配置 | 资源命名空间 | 说明与元数据 |
| --- | --- | --- | --- |
| 扑克、按钮、旧桌面与语音 | `configuration/doudizhu.yml` | `assets/doudizhu/` | [扑克与音效说明](docs/doudizhu.md)，`metadata/doudizhu.json` |
| UNO 牌与按钮 | `configuration/uno.yml` | `assets/uno/` | [UNO 素材说明](docs/uno.md)，`metadata/uno.json` |
| 整体牌桌与无靠背凳 | `configuration/furniture.yml` | `assets/cardtable/` | 下方模型与尺寸约定 |

表中的 `assets/` 均位于 `resourcepack/` 下。物品 ID、模型和贴图保留原命名空间：扑克使用 `doudizhu:*`，UNO 物品继续使用 `doudizhu:{classic|jade}_uno_*` 并引用 `uno:*` 资源，家具使用 `cardtable:*`。当前斗地主语音事件为 `doudizhu:voice.{female|male}.{event}`，已替换旧合成语音事件。

本包只有一个 `pack.yml`，共注册 257 个物品（123 个扑克与通用物品、128 个 UNO 物品、6 个家具物品）。各配置采用不同文件名，元数据也按来源分开保存。共 255 张 PNG，已移除独立 GUI 贴图和模型，CE 物品直接引用世界模型。原创音符盒音乐 `music.yml` 和试听 `music-preview.wav` 位于包根目录。

## 生成与维护

从项目根目录运行 `python3 tools/build_assets.py --visuals-only` 更新扑克视觉资源，运行 `python3 tools/build_uno_assets.py` 更新 UNO 资源。两者只写入本统一包中各自的资源命名空间、配置和元数据，不会重新创建旧顶层目录，也不会覆盖另一个游戏或家具的文件。

生成器只生成世界贴图与模型，不再生成 `_gui.png`、`_gui.json` 或 GUI 场景选择分支。普通模型中的物品栏显示变换保留。

共享的 `pack.yml` 单独维护，牌面生成器不重写它。完整运行 `build_assets.py` 还会更新 `music.yml`，所有模式均保留已导入的语音；`--art-only` 只更新扑克贴图与预览。家具继续通过 Blockbench 工程维护。

语音通过 `python3 tools/import_doudizhu_voices.py` 独立重建。来源、固定版本、逐文件 SHA-256 与转换参数见 `metadata/doudizhu-voices.json`；108 个原始 MP3 位于 `sources/fight-the-landlord/`，不发送到客户端。客户端只接收转码后的 OGG，以及 `resourcepack/licenses/fight-the-landlord/` 内的上游 GPL 全文、来源说明与清单。上游仓库使用 GPL-3.0；原始录音作者及音频单独授权尚未确认，详见 [语音来源说明](licenses/fight-the-landlord/NOTICE.txt)。

## 家具呈现

本包注册普通 `paper` 自定义模型物品，不配置 CE 家具行为、碰撞箱或座椅。插件为整张牌桌创建一个 `ItemDisplay`，每把无靠背凳各创建一个 `ItemDisplay`；入座区域、玩家坐姿、手牌与出牌交互仍由 CardTable 管理。

## 模型与贴图

- 物品 ID：`cardtable:table_6`、`cardtable:table_7`、`cardtable:table_8`、`cardtable:table_9`、`cardtable:table_10`、`cardtable:stool`。
- 模型路径：`resourcepack/assets/cardtable/models/item/furniture/<模型名>.json`。
- 共用的 128 × 128 像素图集：`resourcepack/assets/cardtable/textures/item/furniture/furniture.png`，材质引用为 `cardtable:item/furniture/furniture`。

模型以 `[8, 8, 8]` 为原点，按世界尺寸的一半编码，底部位于模型 `Y=8`。模型自身的 `display.fixed` 使用单位变换，插件的 `ItemDisplay` 使用统一的 `[2, 2, 2]` 缩放。CE 物品配置不再追加缩放。

这种编码使最大的十人桌也位于 Java 模型的 `[-16, 32]` 坐标范围内。各桌使用独立模型保留尺寸，不依靠横向拉伸一个模型适应不同人数。桌椅的几何、UV 与贴图由 Blockbench 工程维护。

## 尺寸与布局

尺寸来源为插件的 `TableLayout`。设席位数为 `n`，座位半径为 `r = max(2.8, n × 0.46)`，桌面半宽为 `h = min(r - 1.25, (r - 0.60) / √2)`。桌面宽、深均为 `2h`，实际顶面距地高度保留旧桌板的 `0.66 + max(0.04, h / 32)`。

| 物品 ID | 席位数 | 桌面宽、深（格） | 顶面高度（格） |
| --- | --- | --- | --- |
| `cardtable:table_6` | 2～6 | 3.1000 | 0.7084 |
| `cardtable:table_7` | 7 | 3.7052 | 0.7179 |
| `cardtable:table_8` | 8 | 4.3558 | 0.7281 |
| `cardtable:table_9` | 9 | 5.0063 | 0.7382 |
| `cardtable:table_10` | 10 | 5.6569 | 0.7484 |

`table_6` 供 2～6 席共用，其中包括三人斗地主与默认四人 UNO；UNO 的 7～10 席分别选择同名尺寸模型。表内数字仅用于阅读，模型尺寸按上述公式计算，不以四舍五入值替代。

凳面宽、深均为 0.70 格，座面距地 0.60 格。凳子按原有 `seatLocation` 环绕牌桌放置，保留原座位位置、朝向和通道空间。新增模型只负责家具外观，原玩家姓名、牌数、倒计时、方向符号与牌桌操作由插件继续绘制。

家具配置与资源分别位于统一包的 `configuration/furniture.yml` 和 `resourcepack/assets/cardtable/`，两套牌面生成器不会覆盖它们。
