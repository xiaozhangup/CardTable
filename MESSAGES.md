# CardTable 玩家聊天消息清单

按 2026-10-05 当前工作区源码整理，共 **216 个编号条目**，含完整消息和聊天中使用的状态、牌名片段

本文件供查看和修改文案时对照，不是运行时语言配置，插件不会读取它。编号固定用于定位，不作为运行时消息键

## 阅读约定

- 模板省略统一前缀 `[牌桌] `，动态值依次写成 `{0}`、`{1}`；相同值重复出现时复用同一编号
- 源码位置默认从 `src/main/kotlin/me/xiaozhangup/cardtable/` 起算，例如 `TableCommands.kt:24`。以 `../CrabKotlin/` 开头的是同级 CrabKotlin 源码，行号对应本次整理时的工作区
- CardTable 消息按 `util/ChatMessages.kt:12` 的规则展示：连续 ASCII 空格压成一个，在半角 `(` 前和 `)` 后补空格，再去掉首尾空白。因此源码 `),` 会显示为 `) ,`，本表保留这个实际结果
- 模板保留实际标点。四条底层英文校验文案仍有末尾句号，已单独注明；当前空格处理器不会删除句号
- 参数的动态内容继续接受同一空格处理；来自经济插件、异常或第三方玩法的原始详情无法在静态清单中穷举
- 默认正文色与前缀由 Crab Notify 提供。表中 `§e` 表示黄色高亮，UNO 的 `§c/§e/§a/§9` 分别为红/黄/绿/蓝，`§r` 恢复普通正文色，这些标记不会显示为文字
- “广播”指当前牌桌内能取得在线 Player 的参与者，见 `table/CardTableService.kt:141`；不发给旁观者。错误提示通常仅发起操作的人可见；UNO 挑战证据仅发给挑战者

## 收录范围

收录 `tell`、`sendTableMessage`、游戏广播、`requireInput.playerMessage`，以及玩家操作经 `attempt` 接收到的异常消息。另列相同 Notify 的 Crab 命令框架反馈

不收录纯控制台日志、菜单说明、全息、顶部标题、actionbar、模型和物品牌面标签。唯一例外是 `/ct list` 确实拼入聊天的 `GameView.status`，以及聊天消息参数实际引用的牌名/牌型片段；这些单独标注

## 通用入口与个人反馈

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| GEN-001 | `参数无效` | 无；捕获到没有 message 的 IllegalArgumentException 时使用 | `CardTablePlugin.kt:130` |
| GEN-002 | `{0}` | 0=未包装为 InputException 的异常 message；动态外部文字不能静态穷举 | `CardTablePlugin.kt:130` |
| GEN-003 | `未知牌面: {0}` | 0=牌面名 | `CardTablePlugin.kt:137` |
| GEN-004 | `已切换为 {0} 牌面` | 0=牌面名 | `CardTablePlugin.kt:140` |
| GEN-005 | `本次在线期间已关闭牌桌音乐` | 无 | `ui/TableAudio.kt:48` |
| GEN-006 | `已开启牌桌音乐` | 无 | `ui/TableAudio.kt:48` |
| GEN-007 | `请走近牌桌再操作` | 无 | `ui/WorldTableRenderer.kt:738` |
| GEN-008 | `入座传送被取消, 请稍后再试` | 无 | `ui/SeatingHook.kt:36` |
| GEN-009 | `坐下被取消, 请稍后再试` | 无 | `ui/SeatingHook.kt:52` |

## 命令结果与参数错误

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| CMD-001 | `{0}[{1}] {2}/{3} {4}` | 0=桌名；1=游戏 ID；2=入座人数；3=座位数；4=下表中的当前状态；多桌用 ; 加一个空格连接 | `TableCommands.kt:24` |
| CMD-002 | `尚未设置牌桌` | 无 | `TableCommands.kt:24` |
| CMD-003 | `{0}: {1}` | 0=游戏 ID；1=游戏显示名；多个玩法用 ; 加一个空格连接 | `TableCommands.kt:26` |
| CMD-004 | `请先加入牌桌` | 无；也由牌桌操作入口使用 | `TableCommands.kt:50; table/CardTableService.kt:89,94` |
| CMD-005 | `使用 /ct select <牌编号,牌编号>, 编号必须来自自己的手牌` | 无 | `TableCommands.kt:53` |
| CMD-006 | `已创建 {0}. 点击椅子或 /ct join {0} 入座` | 0=桌名；保留句中半角句号 | `TableCommands.kt:62` |
| CMD-007 | `牌桌已创建` | 无 | `TableCommands.kt:64` |
| CMD-008 | `找不到世界` | 无 | `TableCommands.kt:72` |
| CMD-009 | `坐标必须是数字` | 无 | `TableCommands.kt:73` |
| CMD-010 | `坐标超出世界范围` | 无 | `TableCommands.kt:74` |
| CMD-011 | `已创建 {0}` | 0=桌名 | `TableCommands.kt:76` |
| CMD-012 | `已移除 {0}, 未完局预扣款已退回` | 0=桌名 | `TableCommands.kt:82` |
| CMD-013 | `底注必须为数字` | 无 | `TableCommands.kt:86` |
| CMD-014 | `底注必须在0..1000000之间` | 无；原文数字边界不另加空格 | `TableCommands.kt:87` |
| CMD-015 | `底注已设为 {0}` | 0=底注 Double 的文字值 | `TableCommands.kt:88` |
| CMD-016 | `该游戏尚未注册` | 无 | `TableCommands.kt:93` |
| CMD-017 | `牌桌游戏已设为 {0}` | 0=游戏 ID | `TableCommands.kt:94` |
| CMD-018 | `选项键格式或值长度无效` | 无 | `TableCommands.kt:100` |
| CMD-019 | `游戏选项 {0}={1} 已保存` | 0=选项键；1=选项值 | `TableCommands.kt:101` |
| CMD-020 | `找不到牌面 {0}` | 0=牌面名 | `TableCommands.kt:106` |
| CMD-021 | `默认牌面已更新` | 无 | `TableCommands.kt:110` |
| CMD-022 | `时间必须为整数` | 无 | `TableCommands.kt:115` |
| CMD-023 | `时间必须在5..300秒之间` | 无；原文数字边界不另加空格 | `TableCommands.kt:116` |
| CMD-024 | `回合时间已更新` | 无 | `TableCommands.kt:117` |
| CMD-025 | `配置及牌桌已重载` | 无 | `TableCommands.kt:120` |
| CMD-026 | `请等玩家全部离桌后修改游戏规则或底注` | 无 | `TableCommands.kt:137` |

## /ct list 中实际出现的状态片段

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| LST-001 | `等待准备 {0}/3 人` | 0=斗地主入座人数；供 CMD-001 的参数 4 使用 | `game/doudizhu/DoudizhuProvider.kt:158` |
| LST-002 | `{0} 叫地主 当前 {1} 分` | 0=当前操作人名字；1=当前最高叫分 | `game/doudizhu/DoudizhuProvider.kt:159` |
| LST-003 | `{0} 出牌` | 0=当前操作人名字 | `game/doudizhu/DoudizhuProvider.kt:160` |
| LST-004 | `UNO 等待准备 {0}/{1} 人 (至少 2 人)` | 0=UNO 入座人数；1=座位数 | `game/uno/UnoSession.kt:760` |
| LST-005 | `{0} 出牌或摸牌` | 0=当前操作人名字 | `game/uno/UnoSession.kt:761` |
| LST-006 | `{0} 选择出刚摸到的牌或不要` | 0=当前操作人名字 | `game/uno/UnoSession.kt:761` |
| LST-007 | `{0} 选择颜色` | 0=当前操作人名字 | `game/uno/UnoSession.kt:762` |
| LST-008 | `{0} 接受 +4 或挑战` | 0=当前操作人名字 | `game/uno/UnoSession.kt:763` |

## 牌桌、配置与机器人可用性

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| TBL-001 | `找不到牌桌 {0}` | 0=桌名 | `table/CardTableService.kt:36; game/doudizhu/DoudizhuController.kt:88,376` |
| TBL-002 | `牌桌 {0} 已存在` | 0=桌名 | `table/CardTableService.kt:41; game/doudizhu/DoudizhuController.kt:68` |
| TBL-003 | `游戏 {0} 尚未注册` | 0=游戏 ID | `table/CardTableService.kt:42` |
| TBL-004 | `与另一张牌桌太近, 请为桌椅和出入通道留出空间 (普通桌至少间隔 6.4 格)` | 无 | `table/CardTableService.kt:50` |
| TBL-005 | `你没有游玩牌桌的权限` | 无 | `table/CardTableService.kt:57` |
| TBL-006 | `请先离开当前牌桌` | 无 | `table/CardTableService.kt:58` |
| TBL-007 | `请走到牌桌附近再入座` | 无 | `table/CardTableService.kt:60` |
| TBL-008 | `已入座 {0}. 右键手牌选中, 轮到你时再次右键已选牌即可出牌; 左键取消选择. 其他操作点全息按钮, Shift 离桌` | 0=桌名；保留句中半角句号 | `table/CardTableService.kt:77` |
| TBL-009 | `这个游戏尚未支持机器人` | 无；API 默认方法也使用此文案 | `table/CardTableService.kt:95; api/CardGame.kt:35,36` |
| TBL-010 | `请等本局结束后调整机器人席位` | 无 | `table/CardTableService.kt:96` |
| TBL-011 | `机器人只支持免费桌, 请先将底注设为0` | 无 | `table/CardTableService.kt:99` |
| TBL-012 | `这张牌桌已经坐满了` | 无 | `table/CardTableService.kt:102; game/doudizhu/DoudizhuController.kt:92` |
| TBL-013 | `这张牌桌没有机器人` | 无 | `table/CardTableService.kt:108` |
| TBL-014 | `使用 /ct bot add\|fill\|remove\|clear` | 无；四个子命令为固定用法文字 | `table/CardTableService.kt:112` |
| TBL-015 | `牌桌名只能包含小写字母, 数字, 下划线和连字符, 最多32字` | 无 | `table/TableStorage.kt:67` |
| CFG-001 | `请等所有玩家离桌后再重载` | 无 | `CardTablePlugin.kt:103` |
| CFG-002 | `turn-seconds 必须在 5..300 之间` | 无；玩家执行重载时可收到 | `Settings.kt:24` |
| CFG-003 | `max-distance 必须在 3..128 之间` | 无 | `Settings.kt:25` |
| CFG-004 | `max-multiplier 必须在 1..1024 之间` | 无 | `Settings.kt:26` |
| CFG-005 | `doudizhu-voice 必须为 female 或 male` | 无 | `Settings.kt:27` |
| CFG-006 | `default-skin 必须出现在 skin-prefixes 中` | 无 | `Settings.kt:28` |
| CFG-007 | `牌面名称或资源前缀格式无效` | 无 | `Settings.kt:29` |
| CFG-008 | `Invalid song length or note position in music.yml.` | 无；重载时音乐配置校验若失败，可由 attempt 原样提示；源码仍有末尾句号 | `ui/TableAudio.kt:24` |
| BOT-001 | `机器人运行环境正在准备, 请稍后再添加机器人` | 无；经 table/CardTableService.kt:100 发送 unavailableReason | `ai/ExternalCardAI.kt:33` |
| BOT-002 | `机器人已关闭, 请在配置中启用 ai.enabled` | 无 | `ai/ExternalCardAI.kt:42` |
| BOT-003 | `机器人正在启动, 请稍后再添加机器人` | 无 | `ai/ExternalCardAI.kt:63` |
| BOT-004 | `机器人进程已退出, 请检查服务端日志后重载` | 无 | `ai/ExternalCardAI.kt:116` |
| BOT-005 | `机器人启动失败, 请检查服务端日志后重载` | 无 | `ai/ExternalCardAI.kt:126` |
| BOT-006 | `机器人名称正在加载, 请稍后再试` | 无 | `table/BotNames.kt:37` |
| BOT-007 | `机器人名称暂不可用, 请稍后重试` | 无 | `table/BotNames.kt:37` |
| BOT-008 | `机器人名称不足, 无法添加 {0} 个机器人` | 0=请求添加数量 | `table/BotNames.kt:42` |

## 经济反馈

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| ECO-001 | `这张牌桌需要 Vault 和经济插件, 当前无法使用` | 无 | `table/EconomyService.kt:62` |
| ECO-002 | `未找到可用的 Vault 经济服务` | 无 | `table/EconomyService.kt:66,81` |
| ECO-003 | `下注金额必须是有效的正数` | 无 | `table/EconomyService.kt:67` |
| ECO-004 | `经济插件最多支持 {0} 位小数, 请调整本桌底注` | 0=经济插件允许的小数位数 | `table/EconomyService.kt:73` |
| ECO-005 | `该局已有预扣记录, 请先处理资金记录` | 无 | `table/EconomyService.kt:82` |
| ECO-006 | `{0} 的余额不足, 本桌每人需要预扣 {1}` | 0=余额不足的名字列表，用逗号加空格连接；1=两位小数的每人预扣金额 | `table/EconomyService.kt:86` |
| ECO-007 | `经济插件预扣异常, 未确认的金额已记入资金记录, 请联系管理员核对` | 无 | `table/EconomyService.kt:97` |
| ECO-008 | `{0} 的预扣失败: {1}. 已成功预扣的玩家将退回金额` | 0=玩家名；1=经济插件原始错误；保留句中半角句号 | `table/EconomyService.kt:103` |
| ECO-009 | `Settlement results must be finite and sum to zero.` | 无；内部结算断言，异常若沿玩家操作返回会使用英文原文；源码仍有末尾句号 | `table/EconomyService.kt:113` |
| ECO-010 | `Settlement players must match this round's escrow participants.` | 无；内部结算断言，同上 | `table/EconomyService.kt:114` |
| ECO-011 | `Settlement amount exceeds the escrow reserve.` | 无；内部结算断言，同上 | `table/EconomyService.kt:117` |

## 斗地主：聊天与提示

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| DDZ-001 | `{0} 入座 ({1}/3)` | 0=玩家名；1=入座人数；广播 | `game/doudizhu/DoudizhuController.kt:100` |
| DDZ-002 | `{0} (机器人) 入座 ({1}/3)` | 0=机器人名；1=入座人数；广播 | `game/doudizhu/DoudizhuController.kt:100` |
| DDZ-003 | `{0} 准备就绪` | 0=玩家名；广播 | `game/doudizhu/DoudizhuController.kt:118` |
| DDZ-004 | `{0} 取消了准备` | 0=玩家名；广播 | `game/doudizhu/DoudizhuController.kt:118` |
| DDZ-005 | `发牌完成, 请 {0} 叫分` | 0=叫分人名字；普通玩法广播 | `game/doudizhu/DoudizhuController.kt:166` |
| DDZ-006 | `发牌完成, 请 {0} 叫分, 本局癞子: {1}` | 0=叫分人名字；1=癞子点数；广播 | `game/doudizhu/DoudizhuController.kt:166` |
| DDZ-007 | `{0}: 不叫` | 0=叫分人名字；广播 | `game/doudizhu/DoudizhuController.kt:181` |
| DDZ-008 | `{0}: {1} 分` | 0=叫分人名字；1=叫分；广播 | `game/doudizhu/DoudizhuController.kt:181` |
| DDZ-009 | `无人叫地主, 重新发牌` | 无；广播 | `game/doudizhu/DoudizhuController.kt:190` |
| DDZ-010 | `{0} 成为地主 ({1} 分) , 由地主先出牌` | 0=地主名字；1=最高叫分；右括号后空格按聊天入口保留 | `game/doudizhu/DoudizhuController.kt:200` |
| DDZ-011 | `{0} 出牌: {1} ({2})` | 0=出牌人名字；1=下面的牌型片段；2=各张牌名，用空格连接；非炸弹广播 | `game/doudizhu/DoudizhuController.kt:231` |
| DDZ-012 | `{0} 出牌: {1} ({2}) , 倍数 {3}` | 0=出牌人名字；1=牌型片段；2=各张牌名，用空格连接；3=倍数；炸弹广播 | `game/doudizhu/DoudizhuController.kt:231` |
| DDZ-013 | `{0}: 不要` | 0=玩家名；广播 | `game/doudizhu/DoudizhuController.kt:251` |
| DDZ-014 | `§e没有可以压过上一手的牌, 可以选择不要` | 无；正文黄色，仅发起提示操作的玩家可见 | `game/doudizhu/DoudizhuController.kt:267` |
| DDZ-015 | `{0} 离桌, 所属阵营判负` | 0=离桌人名字；广播 | `game/doudizhu/DoudizhuController.kt:279` |
| DDZ-016 | `{0} 离桌, 尚未确定地主, 本局取消并退回预扣金额` | 0=离桌人名字；广播 | `game/doudizhu/DoudizhuController.kt:283` |
| DDZ-017 | `{0} 离开牌桌` | 0=离桌人名字；广播 | `game/doudizhu/DoudizhuController.kt:291` |
| DDZ-018 | `{0} 操作超时, 已自动不叫` | 0=超时玩家名；广播 | `game/doudizhu/DoudizhuController.kt:311` |
| DDZ-019 | `{0} 操作超时, 已自动不要` | 0=超时玩家名；广播 | `game/doudizhu/DoudizhuController.kt:311` |
| DDZ-020 | `{0} 操作超时, 已自动出最小单牌` | 0=超时玩家名；广播 | `game/doudizhu/DoudizhuController.kt:311` |
| DDZ-021 | `春天! 倍数升至 {0}` | 0=倍数；广播 | `game/doudizhu/DoudizhuController.kt:329` |
| DDZ-022 | `反春天! 倍数升至 {0}` | 0=倍数；广播 | `game/doudizhu/DoudizhuController.kt:329` |
| DDZ-023 | `地主获胜! 叫分 {0} × 倍数 {1}` | 0=叫分；1=倍数；免费桌广播 | `game/doudizhu/DoudizhuController.kt:342` |
| DDZ-024 | `农民获胜! 叫分 {0} × 倍数 {1}` | 0=叫分；1=倍数；免费桌广播 | `game/doudizhu/DoudizhuController.kt:342` |
| DDZ-025 | `地主获胜! 叫分 {0} × 倍数 {1}, 每份 {2}` | 0=叫分；1=倍数；2=两位小数金额；收费桌广播 | `game/doudizhu/DoudizhuController.kt:342` |
| DDZ-026 | `农民获胜! 叫分 {0} × 倍数 {1}, 每份 {2}` | 0=叫分；1=倍数；2=两位小数金额；收费桌广播 | `game/doudizhu/DoudizhuController.kt:342` |
| DDZ-027 | `外部 AI 决策失败, 本局取消, 请检查 AI 程序` | 无；广播 | `game/doudizhu/DoudizhuController.kt:446` |

## 斗地主：规则与操作错误

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| DDR-001 | `斗地主只支持 laizi 选项` | 无 | `game/doudizhu/DoudizhuProvider.kt:39` |
| DDR-002 | `laizi 只能为 true 或 false` | 无 | `game/doudizhu/DoudizhuProvider.kt:42` |
| DDR-003 | `你已经在一张牌桌上, 请先离开` | 无 | `game/doudizhu/DoudizhuController.kt:76` |
| DDR-004 | `机器人只支持免费桌` | 无 | `game/doudizhu/DoudizhuController.kt:82,125` |
| DDR-005 | `请先由真人入座` | 无 | `game/doudizhu/DoudizhuController.kt:83` |
| DDR-006 | `这张牌桌正在游戏中` | 无 | `game/doudizhu/DoudizhuController.kt:89` |
| DDR-007 | `这个席位不是机器人` | 无 | `game/doudizhu/DoudizhuController.kt:107` |
| DDR-008 | `请等本局结束后移除机器人` | 无 | `game/doudizhu/DoudizhuController.kt:108` |
| DDR-009 | `本局已开始` | 无 | `game/doudizhu/DoudizhuController.kt:116` |
| DDR-010 | `至少需要一名真人` | 无 | `game/doudizhu/DoudizhuController.kt:124` |
| DDR-011 | `叫分只能是 0, 1, 2, 3` | 无 | `game/doudizhu/DoudizhuController.kt:177` |
| DDR-012 | `叫分必须高于当前的 {0} 分, 或选择不叫` | 0=当前最高叫分 | `game/doudizhu/DoudizhuController.kt:179` |
| DDR-013 | `请先选中要出的牌` | 无 | `game/doudizhu/DoudizhuController.kt:211` |
| DDR-014 | `选中的牌型不合法, 或无法压过上一手牌` | 无 | `game/doudizhu/DoudizhuController.kt:213` |
| DDR-015 | `你是首出, 必须出牌` | 无 | `game/doudizhu/DoudizhuController.kt:243` |
| DDR-016 | `请先加入一张牌桌` | 无 | `game/doudizhu/DoudizhuController.kt:394` |
| DDR-017 | `现在不是叫分阶段` | 无 | `game/doudizhu/DoudizhuController.kt:466` |
| DDR-018 | `现在不是出牌阶段` | 无 | `game/doudizhu/DoudizhuController.kt:466` |
| DDR-019 | `还没轮到你, 请等待 {0}` | 0=当前操作人名字；斗地主与 UNO 共用此文案 | `game/doudizhu/DoudizhuController.kt:469; game/uno/UnoSession.kt:827` |
| DDR-020 | `请选择 0, 1, 2, 3 分` | 无 | `game/doudizhu/DoudizhuProvider.kt:135` |
| DDR-021 | `请先加入这张牌桌` | 无 | `game/doudizhu/DoudizhuProvider.kt:141` |
| DDR-022 | `本局尚未发牌` | 无；UNO 也使用此文案 | `game/doudizhu/DoudizhuProvider.kt:142; game/uno/UnoSession.kt:302` |
| DDR-023 | `请选择手中的一张牌` | 无 | `game/doudizhu/DoudizhuProvider.kt:143` |
| DDR-024 | `这张牌不在你的手牌中` | 无；UNO 也使用此文案 | `game/doudizhu/DoudizhuProvider.kt:144; game/uno/UnoSession.kt:304` |
| DDR-025 | `未知操作: {0}` | 0=操作名 | `game/doudizhu/DoudizhuProvider.kt:148` |

## 聊天牌名与牌型参数

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| CARD-001 | `软炸 {0}` | 0=主点数；用在 DDZ-011 / DDZ-012 参数 1 | `game/doudizhu/Card.kt:72` |
| CARD-002 | `炸弹 {0}` | 0=主点数 | `game/doudizhu/Card.kt:73` |
| CARD-003 | `四癞子炸` | 无 | `game/doudizhu/Card.kt:74` |
| CARD-004 | `王炸` | 无 | `game/doudizhu/Card.kt:75` |
| CARD-005 | `{0} {1}` | 0=牌型中文名；1=主点数；牌型为单张、对子、三张、三带一、三带二、顺子、连对、飞机、飞机带单、飞机带对、四带二、四带两对 | `game/doudizhu/Card.kt:42,76` |
| CARD-006 | `{0}{1}` | 0=♠/♥/♣/♦；1=3–10/J/Q/K/A/2；大小王直接为小王、大王 | `game/doudizhu/Card.kt:9` |
| CARD-007 | `§c红色§r` | 无；UNO 颜色参数，红色 | `game/uno/UnoCard.kt:4,9` |
| CARD-008 | `§e黄色§r` | 无；UNO 颜色参数，黄色 | `game/uno/UnoCard.kt:5,9` |
| CARD-009 | `§a绿色§r` | 无；UNO 颜色参数，绿色 | `game/uno/UnoCard.kt:6,9` |
| CARD-010 | `§9蓝色§r` | 无；UNO 颜色参数，蓝色 | `game/uno/UnoCard.kt:7,9` |
| CARD-011 | `{0} {1}` | 0=上面含颜色的 UNO 颜色名；1=0–9、跳过、反转、+2；仅颜色名染色 | `game/uno/UnoCard.kt:56` |
| CARD-012 | `万能变色` | 无；默认消息色 | `game/uno/UnoCard.kt:26,56` |
| CARD-013 | `万能 +4` | 无；默认消息色 | `game/uno/UnoCard.kt:27,56` |

## UNO：聊天与私密证据

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| UNO-001 | `{0} 入座 UNO ({1}/{2})` | 0=玩家名；1=入座人数；2=座位数；广播 | `game/uno/UnoSession.kt:178` |
| UNO-002 | `{0} 入座 UNO ({1}/{2}) , 机器人准备就绪` | 0=机器人名；1=入座人数；2=座位数；广播 | `game/uno/UnoSession.kt:178` |
| UNO-003 | `{0} 离开牌桌, 本局取消, 所有预扣退回` | 0=离桌者名字；取消广播，发生在移除席位前 | `game/uno/UnoSession.kt:185` |
| UNO-004 | `{0} 离开 UNO 牌桌` | 0=离桌者或被移除机器人名字；移除后向留在桌内的玩家广播 | `game/uno/UnoSession.kt:188,197` |
| UNO-005 | `{0} 准备就绪` | 0=玩家名；广播 | `game/uno/UnoSession.kt:233` |
| UNO-006 | `{0} 取消准备` | 0=玩家名；广播 | `game/uno/UnoSession.kt:233` |
| UNO-007 | `UNO 开局, 每人 7 张, 翻出 {0}` | 0=带颜色牌名；广播 | `game/uno/UnoSession.kt:276` |
| UNO-008 | `开局反转, 由发牌者 {0} 先出` | 0=发牌者名字；广播 | `game/uno/UnoSession.kt:285` |
| UNO-009 | `{0} 被开局跳过` | 0=被跳过者名字；广播 | `game/uno/UnoSession.kt:288` |
| UNO-010 | `{0}: UNO!` | 0=宣告者名字；广播 | `game/uno/UnoSession.kt:333,478` |
| UNO-011 | `{0} 出了 {1}` | 0=出牌者名字；1=带颜色牌名；广播 | `game/uno/UnoSession.kt:339` |
| UNO-012 | `{0} 被跳过` | 0=被跳过者名字；广播 | `game/uno/UnoSession.kt:357` |
| UNO-013 | `{0} 无牌可摸, 本回合可选择不要` | 0=行动者名字；广播 | `game/uno/UnoSession.kt:390` |
| UNO-014 | `{0} 摸了 1 张` | 0=摸牌者名字；广播 | `game/uno/UnoSession.kt:394` |
| UNO-015 | `{0} 选择不要` | 0=行动者名字；广播 | `game/uno/UnoSession.kt:405` |
| UNO-016 | `牌堆已空, 所有玩家均无法接牌, 本局流局, 所有预扣退回` | 无；广播 | `game/uno/UnoSession.kt:409` |
| UNO-017 | `{0} 选择了 {1}` | 0=选色者名字；1=带颜色的颜色名；广播 | `game/uno/UnoSession.kt:421` |
| UNO-018 | `{0} 请选择接受 +4, 或挑战是否合法` | 0=下家名字；实际发送范围为本桌广播 | `game/uno/UnoSession.kt:430` |
| UNO-019 | `UNO 挑战证据: 出牌前颜色为 {0}; {1} 当时手牌: {2}` | 0=带颜色的原颜色名；1=出 +4 者名字；2=整手带颜色牌名，用逗号加空格连接；仅挑战者私聊 | `game/uno/UnoSession.kt:455,457` |
| UNO-020 | `{0} 挑战失败, +4 合法, 罚摸 6 张并跳过` | 0=挑战者名字；广播 | `game/uno/UnoSession.kt:461` |
| UNO-021 | `{0} 挑战成功, {1} 当时持有原颜色, 罚摸 4 张` | 0=挑战者名字；1=出 +4 者名字；广播 | `game/uno/UnoSession.kt:465` |
| UNO-022 | `{0} 预先宣告 UNO, 本回合出至 1 张时生效` | 0=宣告者名字；广播 | `game/uno/UnoSession.kt:482` |
| UNO-023 | `{0} 抓到 {1} 漏叫 UNO, 罚摸 2 张` | 0=抓漏叫者名字；1=漏叫者名字；广播 | `game/uno/UnoSession.kt:499` |
| UNO-024 | `{0} 因 {1} 摸了 {2} 张` | 0=摸牌者名字；1=罚摸原因，见下方原因表；2=实际张数；牌堆足够时广播 | `game/uno/UnoSession.kt:537` |
| UNO-025 | `{0} 因 {1} 摸了 {2} 张 (牌堆不足, 原应摸 {3} 张)` | 0=摸牌者名字；1=罚摸原因；2=实际张数；3=应摸张数；牌堆不足时广播 | `game/uno/UnoSession.kt:537` |
| UNO-026 | `{0} 获胜, 本局得分 {1}` | 0=获胜者名字；1=本局分数；免费局广播 | `game/uno/UnoSession.kt:568,569` |
| UNO-027 | `{0} 获胜, 本局得分 {1}, 净赢 {2}, 每名输者输 {3}` | 0=获胜者名字；1=分数；2=净赢金额；3=每人输额；收费局广播，金额两位小数 | `game/uno/UnoSession.kt:568,569` |
| UNO-028 | `UNO 开局失败: {0}; 请检查机器人策略服务后重新准备` | 0=InputException.playerMessage 或原始 error.message；动态异常详情；广播 | `game/uno/UnoSession.kt:624` |
| UNO-029 | `{0} 操作超时` | 0=超时者名字；广播 | `game/uno/UnoSession.kt:648` |
| UNO-030 | `机器人策略服务请求失败或返回非法操作, 本局 UNO 取消, 所有预扣退回` | 无；广播 | `game/uno/UnoSession.kt:752` |

## UNO：规则与操作错误

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| UNR-001 | `机器人只能加入免费牌桌, 请先把本桌底注设为 0` | 无 | `game/uno/UnoSession.kt:162` |
| UNR-002 | `请先让一名玩家加入牌桌` | 无 | `game/uno/UnoSession.kt:163` |
| UNR-003 | `机器人策略服务尚未就绪` | 无 | `game/uno/UnoSession.kt:164,244` |
| UNR-004 | `这张 UNO 牌桌正在游戏中` | 无 | `game/uno/UnoSession.kt:169` |
| UNR-005 | `你已经入座` | 无 | `game/uno/UnoSession.kt:170` |
| UNR-006 | `这张 UNO 牌桌已经坐满了` | 无 | `game/uno/UnoSession.kt:173` |
| UNR-007 | `对局中不能移除机器人, 请等待本局结束` | 无 | `game/uno/UnoSession.kt:193` |
| UNR-008 | `只能用此操作移除机器人` | 无 | `game/uno/UnoSession.kt:195` |
| UNR-009 | `UNO 不支持操作: {0}` | 0=操作名 | `game/uno/UnoSession.kt:226` |
| UNR-010 | `本局 UNO 已开始` | 无 | `game/uno/UnoSession.kt:231` |
| UNR-011 | `收费牌桌不能进行机器人对局` | 无 | `game/uno/UnoSession.kt:241` |
| UNR-012 | `请选择手中的一张 UNO 牌` | 无 | `game/uno/UnoSession.kt:303` |
| UNR-013 | `请先选中一张 UNO 牌` | 无 | `game/uno/UnoSession.kt:316` |
| UNR-014 | `摸牌后只能出刚摸到的那一张, 或选择不要` | 无 | `game/uno/UnoSession.kt:318` |
| UNR-015 | `这张牌与当前颜色或符号不匹配` | 无 | `game/uno/UnoSession.kt:321` |
| UNR-016 | `本回合已经摸过牌, 请出刚摸到的牌或选择不要` | 无 | `game/uno/UnoSession.kt:377` |
| UNR-017 | `UNO 不能直接跳过, 请先摸 1 张` | 无 | `game/uno/UnoSession.kt:402` |
| UNR-018 | `请选择 red, yellow, green 或 blue` | 无；保留实际颜色参数名 | `game/uno/UnoSession.kt:417` |
| UNR-019 | `本局尚未开始` | 无 | `game/uno/UnoSession.kt:473,491,820` |
| UNR-020 | `剩 1 张时可宣告 UNO; 轮到你且有 2 张时也可在出牌前宣告` | 无 | `game/uno/UnoSession.kt:485` |
| UNR-021 | `目前没有可以抓漏叫 UNO 的玩家` | 无 | `game/uno/UnoSession.kt:493` |
| UNR-022 | `请用 UNO 操作宣告自己的最后一张牌` | 无 | `game/uno/UnoSession.kt:494` |
| UNR-023 | `该玩家已经叫过 UNO, 或已不剩 1 张` | 无 | `game/uno/UnoSession.kt:496` |
| UNR-024 | `刚摸到的牌不能出, 请选择不要` | 无；已经摸牌时按提示 | `game/uno/UnoSession.kt:520` |
| UNR-025 | `没有可接的牌, 请摸 1 张` | 无；尚未摸牌时按提示 | `game/uno/UnoSession.kt:520` |
| UNR-026 | `请先加入这张 UNO 牌桌` | 无 | `game/uno/UnoSession.kt:812` |
| UNR-027 | `请先选择颜色` | 无 | `game/uno/UnoSession.kt:821` |
| UNR-028 | `请先接受 +4 或挑战, 不能叠加罚牌` | 无 | `game/uno/UnoSession.kt:822` |
| UNR-029 | `当前为出牌阶段` | 无 | `game/uno/UnoSession.kt:823` |
| UNR-030 | `UNO 只支持 seats 选项` | 无 | `game/uno/UnoProvider.kt:15` |
| UNR-031 | `UNO 的 seats 必须为 2..10, 默认 4` | 无 | `game/uno/UnoProvider.kt:19` |

## Crab 命令框架反馈

本节另列共用 Notify 的依赖模板。发送者类型不匹配和无执行器属于框架回退；当前内置命令的正常玩家操作不会触发这两项

| 编号 | 文字模板 | 参数与发送范围 | 源码位置 |
|---|---|---|---|
| CRB-001 | `你没有执行此命令的权限!` | 无；命令权限检查，走相同的 tableNotify | `../CrabKotlin/common/src/main/kotlin/me/xiaozhangup/crab/command/CommandMessages.kt:29,32` |
| CRB-002 | `当前命令发送者无法执行此命令!` | 无；发送者类型不匹配 | `../CrabKotlin/common/src/main/kotlin/me/xiaozhangup/crab/command/CommandMessages.kt:47` |
| CRB-003 | `此命令尚未设置执行操作!` | 无；命令节点无执行器 | `../CrabKotlin/common/src/main/kotlin/me/xiaozhangup/crab/command/CommandMessages.kt:59` |
| CRB-004 | `命令不完整，请检查用法! {0}` | 0=带 / 的命令上下文，过长部分以 ... 截断；参数采用 Crab 强调色；保留框架原始逗号 | `../CrabKotlin/common/src/main/kotlin/me/xiaozhangup/crab/command/CommandMessages.kt:75,80` |
| CRB-005 | `命令参数错误，请检查输入! {0}` | 0=带 / 的命令上下文；参数采用 Crab 强调色；保留框架原始逗号 | `../CrabKotlin/common/src/main/kotlin/me/xiaozhangup/crab/command/CommandMessages.kt:76,80` |

## UNO 罚摸原因

UNO-024 / UNO-025 的 `{1}` 来自以下调用。挑战结果或抓漏叫结果广播之后，还会再发送罚摸结果

| 原因文字 | 应摸张数 | 源码位置 |
|---|---|---|
| `开局 +2` | 2 | `game/uno/UnoSession.kt:293` |
| `+2` | 2 | `game/uno/UnoSession.kt:352` |
| `接受 +4` | 4 | `game/uno/UnoSession.kt:442` |
| `挑战失败` | 6 | `game/uno/UnoSession.kt:462` |
| `违规 +4` | 4 | `game/uno/UnoSession.kt:466` |
| `漏叫 UNO` | 2 | `game/uno/UnoSession.kt:500` |

UNO 的“本局尚未发牌”“这张牌不在你的手牌中”“还没轮到你”与斗地主文案一致，分别合并在 DDR-022、DDR-024、DDR-019，并列出了两处来源

## 覆盖边界与颜色

- CardTable 业务聊天通过 `CardTablePlugin.tell → sendTableMessage → tableNotify.send`；UNO 挑战证据直接调用同一个发送器，接收者仍只取挑战者的 Player
- `InputException` 选择中文 `playerMessage`；其他 `IllegalArgumentException` 原样使用 `message`，没有 message 时使用 GEN-001。GEN-002 表示这个动态入口，不代表新增一条固定文案
- ECO-009 至 ECO-011 为内部结算校验：通常由游戏状态保证条件，但若在玩家出牌调用期间抛出，当前入口会直接提示英文。CFG-008 的音乐校验同样可在玩家执行重载时返回
- UNO-028 的开局失败详情首选 `InputException.playerMessage`，否则取原始异常 message；没有 message 时，当前 Kotlin 插值会出现文字 `null`
- UNO 的 AI 响应校验英文、皮肤/名称抓取诊断、AI 环境安装日志、`TableStorage.load` 已捕获的配置错误均只进后台日志，不列入玩家模板。UNO / 斗地主牌编号检查及抽牌数量检查只接受内部构造的合法值，也未列入
- Crab 命令框架文案由依赖提供，通过 `TableCommands.kt:18` 的 `tableNotify` 发送；其已有中文逗号按依赖源码保留，并不经过 CardTable 的空格函数
- UNO 开局翻牌、出牌、选择颜色和挑战证据使用 CARD-007 至 CARD-013 的规则，只有颜色词染色；牌值、无色万能牌保持普通正文色。DDZ-014 整段正文保持黄色
