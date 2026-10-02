# 游戏扩展 API

公共层负责牌桌位置、加入距离与权限、实体显示、菜单、皮肤、座椅、声音和资金托管。内置斗地主和 UNO 的手牌、轮次、操作、处罚和胜负完全由各自的 `GameSession` 管理。UNO 通过同一个 `GameProvider` 注册，只依赖 `GameContext`，没有强转主插件实例；新增游戏无需修改既有玩法。

扩展插件声明 `depend: [CrabKotlin, CardTable]`，编译依赖 `me.xiaozhangup.cardtable:CardTable:1.3.10:api`，另外使用 CrabKotlin 和 Paper 的 compileOnly API。运行时不要将 CardTable API 或 Kotlin 打包进扩展插件。

在扩展插件的 `enable()` 中，通过 Bukkit 服务取得 `GameRegistry`，注册自己的 `GameProvider`，并传入插件实例作为 owner：

```kotlin
val registry = Bukkit.getServicesManager().load(GameRegistry::class.java)!!
registry.register(myProvider, this)
```

注册后 `/ct games` 会显示该游戏；用 `/ct create <桌名> <游戏id>` 建桌，或对空桌用 `/ct game` 切换。扩展通常在 `onEnable` 注册，CardTable 在下一 tick 的 active 阶段加载持久牌桌，因此启动时能识别扩展。缺失 provider 的牌桌配置保留；安装或注册 provider 后可通过 `/ct reload` 重新加载。扩展插件关闭时自动注销它拥有的 provider，关闭对应 session、移除实体/座椅并退款；保存的桌子配置保留。主动调用 `unregister(id)` 有相同语义。

| 接口 | 职责 |
| --- | --- |
| `GameProvider` | 唯一 id、中文名称、validate 配置、为每张桌 create 独立 session |
| `GameSession` | seatCount、participants、active、join/leave/act、view、tick、close |
| `TableCardPile` | 可选接口，tableCards 返回按旧到新排列的公开桌面牌堆；最后一张位于顶层 |
| `TableTurnOrder` / `TurnDirection` | 可选接口，turnDirection 返回从桌面正上方观察的规则方向；等待或无方向时返回 null |
| `TablePlayEvents` / `TablePlay` | 可选接口，lastTablePlay 返回最近一次成功公开出牌的 sequence、物理 seat 与 cards |
| `TableSkipEvents` / `TableSkip` | 可选接口，lastTableSkip 返回实际禁手跳过事件的 sequence 与物理 seat |
| `TableDrawEvents` / `TableDraw` | 可选接口，lastTableDraw 只返回实际摸牌事件的 sequence、物理 seat 与非零 count |
| `GameContext` | plugin、共享 GameEconomy、封顶值，以及 changed/broadcast/voice 回调 |
| `GameView` | 公共状态、玩家/角色/张数、当前观察者手牌、公开牌、底牌、控件与时间 |
| `GameView.backAsset / leaveDescription` | 本游戏的牌背后缀、玩家离桌按钮说明 |
| `GameControl` | action、argument、全息标题和说明；点击原样传给 act，icon 字段保留兼容 |
| `CardFace` | asset 后缀、名称、token、selected；点击选牌传 `select` + token，右键已选牌且控件提供 play 时提交 play |
| `GameEconomy` | provider 检查、reserve 最大损失、settle 净输赢、refund、retry |
| `Participant.bot` | 机器人标记；公共层使用原版 Mannequin，与真人共用原生坐姿和座位布局 |
| `GameSession.supportsBots / addBot / removeBot` | 游戏可选择支持机器人席位；默认拒绝，免费桌且已有真人时添加 |
| `GameContext.botAI` / `CardAI` | 异步请求外部 AI，返回 `CompletableFuture<BotDecision>` |

所有接口及回调在 Paper 主线程调用。每个 session 独立保存状态；无效玩家操作或配置用带中文说明的 `IllegalArgumentException`。`validate()` 必须在建立状态之前验证自己的 `TableConfig.options`；公共配置只保存位置、游戏 id、底注、牌面、时间和字符串选项，不含特定游戏规则。

AI 例外：`decide(gameId, state)` 异步计算，未来结果须在后续 `tick()` 主线程检查完成并执行，不能在 future 回调内访问 Bukkit。请求只包含该机器人的手牌与公开状态，游戏自己生成合法动作并验证结果。必须丢弃旧局、旧轮次或手牌变化前的响应；结束和关闭时取消待处理 future。外挂 JSONL 协议见 [ai/README.md](ai/README.md)，其他游戏可增加自己对应的 game id/observation，或替换 `ai.command` 运行自己的策略程序。

机器人席位必须包含至少一名真人；机器人自动准备，每局结束真人重新准备。最后真人离开后，session 按其离桌规则结束/取消当前局并清空机器人；共享层会移除相应人偶。收费桌禁止机器人，不为虚拟 UUID 创建经济账户。1.2 增加 Participant 和 GameContext 字段，扩展须用 1.2 API 重新编译。

`view(null)` 是公共视图，绝不能包含任何人的私有牌面。`view(playerId)` 只能包含该玩家手牌，其他玩家只给张数。未知观察者应返回空手牌和空操作列表。叫分/发牌等未公开底牌应返回 `asset="back"`、`token=""`，名称不泄露内容；不要携带可推导真实牌面的编号。世界渲染所有座位、手牌、公共牌与底牌。菜单只读取 `view(null)` 的桌况，用于设置，不显示手牌也不派发游戏动作。游戏必须在 `GameView.controls` 提供当前阶段全部可用动作；全息按钮每排三个，标题应简短，每阶段建议不超过六个，复杂操作拆成游戏阶段。

状态变化后调用 `changed()`；它会刷新已打开的设置菜单和世界实体。每秒 `tick()` 可调用该回调更新倒计时；手牌、文字、按钮和命中实体按稳定标识复用，更新现有实体并移除本轮不再显示的内容，避免整批删建造成的刷新闪烁。公共牌堆与飞牌动画独立管理。`broadcast()` 给参与玩家发中文通知。`voice(key)` 的短键播放 `doudizhu:voice.<key>`，完整 `namespace:event` 则原样播放。新游戏可沿用标准扑克牌，或在 `skin-prefixes` 下制作自己的 asset 后缀。世界手牌始终单行，每页最多 20 张，在可用宽度内按本页张数调整重叠间距；超过 20 张后翻页，跨页保留 `CardFace.selected`，选择始终通过原始 `CardFace.token` 传回游戏。手牌与回合动作按钮交换原先区域，设置菜单继续只读桌况。

1.3.4 新增独立可选接口 `TableTurnOrder`，不修改 `GameSession` 和 `GameView` 构造。实现它的 session 提供 `val turnDirection: TurnDirection?`；从桌面正上方看，`CLOCKWISE` 为顺时针 `⟳`，`COUNTERCLOCKWISE` 为逆时针 `⟲`，等待或不适用方向时返回 `null`。方向来自当前游戏规则状态，不按文字或当前行动者位置推断；例如 UNO 双人反转会改变方向，同时按规则让出牌者继续行动。未实现接口的扩展游戏省略方向符号。轮次、时间或方向变化后调用 `changed()`。

1.3.9 的公共呈现使用无背景桌面文字：桌心独立显示放大的白色方向符号，依照 `PlayerView.current` 仅在当前席位前显示白色 `GameView.remainingSeconds` 倒计时和指向该席位的实心三角形 `▼`；三角形位于倒计时靠近玩家的一侧，整组提示相较 1.3.7 向桌心收回 0.10 格，其余席位前默认为空。禁手提示复用该席位的指针第二行，只显示白色 `∅`，不显示数字，持续 30 tick，末 6 tick 淡出；快速重新轮到该席位时，正常计时优先。当前行动者不用姓名标记，倒计时只在该席位前显示。方向统一由 `TableTurnOrder` 提供桌心符号，游戏的 `note` 无需重复方向；UNO 个人提示仅保留颜色和规则。

1.3.5 新增独立可选接口 `TablePlayEvents`，不修改 `GameSession` 或 `GameView` 构造。`val lastTablePlay: TablePlay?` 提供最新一次公开出牌快照，数据结构为 `TablePlay(sequence: Long, seat: Int, cards: List<CardFace>)`。`sequence` 在同一个 session 的生存期内每次成功出牌严格递增，重开、取消时不归零；`seat` 是 `Participant.seat` 对应的物理席位，`cards` 是本次实际打出的公开牌，按出牌顺序排列，癞子使用实际解释后的牌面。选牌、不要、叫分、倒计时和开局翻牌不产生事件；不要包含对手私牌、抽牌顺序或未揭晓底牌。更新事件与公开牌堆后调用 `changed()`，共享层按序号去重，仅对仍位于当前公开牌堆顶的新事件，从出牌席位依次播放飞牌动画。未实现接口的扩展游戏继续即时显示公开牌堆。

正常获胜后可以重置手牌、规则、AI 和资金状态，同时单独保留最终公开展示牌堆与 `lastTablePlay`，让最后出牌动画完成；内置两游戏会保留到下一局发牌。取消、对局中离桌或关闭时清空展示牌堆和事件，但保留序号计数。UNO 的最终展示快照取自罚抽与弃牌回收完成后的真实弃牌堆，不把已洗回抽牌堆的旧牌留在桌面。

1.3.9 增加两个独立可选接口，不修改 `GameSession` 和 `GameView` 构造。`TableSkipEvents.lastTableSkip: TableSkip?` 提供 `TableSkip(sequence: Long, seat: Int)`，UNO 仅在开局翻到 SKIP 和普通 SKIP 确实跳过下一个回合时发出，`seat` 为被跳过者的 `Participant.seat`；最后一张 SKIP 立即获胜时不产生事件，+2/+4、反转和主动不要也不产生禁手事件。最新禁手事件保留到新事件、新局或 reset，30 tick 的显示时长由共享层管理。

`TableDrawEvents.lastTableDraw: TableDraw?` 提供 `TableDraw(sequence: Long, seat: Int, count: Int)`，只公开实际摸到的非零张数与物理席位，不包含私牌 token 或牌面。UNO 在普通 draw 和既有 penalty 成功取得牌后发出，包含开局 +2、行动牌罚抽、挑战与漏叫处罚；开局 7 张不发事件。共享层从 `GameView.bottomCards` 对应的固定抽牌堆位置播放公开牌背，每张飞行 12 tick、逐张间隔 2 tick。真实规则变化立即生效，游戏的 `act/tick` 不等待动画，也不因动画阻止操作。

Skip、Draw 各有独立的 `sequence`，同一 session 生存期内持续递增，跨局不归零。成功 start、新局 reset、取消和关闭清空这两个事件；正常获胜清禁手与旧摸牌事件，仅当本次最后 +2/+4 实际产生了罚抽时保留该次 `lastTableDraw`，随最终公开牌堆和出牌事件供收尾动画使用。事件在主线程随状态更新，并沿用 `changed()` 发布；不支持接口的扩展游戏仍可按既有规则游玩。

1.3.3 新增独立可选接口 `TableCardPile`，不改既有 `GameSession` 和 `GameView`。实现它的 session 通过 `tableCards()` 返回所有人均可看的牌堆快照，旧牌在前、新牌在后；回收、重开或取消时由游戏同步清理，变更后调用 `changed()`。不要返回隐藏手牌、摸牌堆顺序或未揭晓底牌。未实现的游戏继续用 `GameView.publicCards` 作为桌面内容。共享层只渲染上层最多 16 张，但不修改游戏牌堆；多张 `publicCards` 仍提供面向玩家的最新组合摘要。右键已选牌时读取实时视图，只有控件包含 `action="play"` 才提交该控件的 action/argument；规则与回合权限仍须在游戏的 `act` 中校验，左键始终传 `select`。

1.3/1.3.1 不改变游戏接口。共享层管理内置坐下、Shift 离桌、全息选牌与动作、设置导航；不要自行创建玩家坐骑。世界牌模型的正面是 north (-Z)，FIXED 变换为单位变换，未缩放宽高为 0.625 × 0.9375。模型尺寸与渲染/选取尺寸必须一致。1.3.1 的游戏按钮由共享层生成原版矩形文字全息，不要求 CE 图标；`GameControl.icon` 保留兼容。自定义物品仍可用 CE `minecraft:display_context` 的 `gui` 分支显示独立识别图标。`cardtable:` 操作前缀留给共享世界界面。设置菜单独立派发 `skin/music/close/lobby/settings/page/bot_fill/bot_clear`，不会把这些操作传入游戏。

1.1 增加了 `GameView` 的牌背和离桌说明字段，扩展插件需使用 1.1 API 重新编译。UNO 素材是各皮肤下的 `uno_*` 后缀，牌背为 `uno_back`；斗地主保持默认 `back`。

1.3.2 在共享纸牌渲染层补偿原版 ItemDisplay 的局部 Y 轴半周旋转。扩展牌模型仍保持 north 为正面、FIXED 为单位变换，不要在模型中重复补偿；命中平面继续使用朝向玩家的逻辑旋转。

收费游戏在发牌之前预扣足够覆盖每人最大损失的金额，保存 roundId；明确失败不能开始对局。结算给每位托管玩家传入正或负净输赢，所有玩家净额合计必须为零，且每人损失不能超过预扣。免费局传 reserve amount=0，无需 Vault。`close()` 和无胜负取消局必须 refund(roundId)；正常胜负用 settle。资金服务自动持久化并处理未完局退款/充值失败恢复，扩展不应自己绕过它直接调用 Vault。

1.3.10 的 UNO 主动摸牌会将 `Seat.selected` 设为实际摸到的那张牌，由既有 `CardFace.selected` 呈现。空摸保持未选中，罚摸不选中；出牌仍通过既有回合与合法性检查。公共 API 不变。

1.3.12 新增可选 `TableHeader` 接口，`tableHeader()` 返回 `TableHeaderView(title: Component, detail: Component)`。两项各占一行：标题放玩法与阶段，详情放该阶段必要的公开信息；用组件直接携带颜色，每行保持简短，不嵌入换行。抬头对旁观者也可见，不得加入手牌、摸到的牌或挑战证据；不重复当前玩家姓名、方向和倒计时。渲染层为每桌复用一个居中的文字实体，单独比较抬头快照，倒计时不会触发实体重建。未实现该接口的游戏仍可使用，显示玩法名和人数/对局中。既有 `GameView` 构造和会话方法保持不变。1.3.13 起顶部使用原版默认字号和默认半透明背景，分组仅以空格分隔。1.3.14 恢复玩家头顶姓名、身份、手牌数和准备状态；空位不显示入座文字。
