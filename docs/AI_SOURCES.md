# 人机策略开源调研

核对日期：2026-10-02。以下结论来自官方仓库的固定提交及源码。CardTable 采用外部 Python 进程直接调用 RLCard 的原始规则代理，插件负责转换牌桌信息、生成合法动作并验证返回结果。完整版权与许可声明见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

| 项目 | 已核对的提交 | 许可证 | 可用于 CardTable 的部分 |
| --- | --- | --- | --- |
| [RLCard](https://github.com/datamllab/rlcard/tree/d7d0a957baf4cc7225a50522adb0164bf130a9d0) | `d7d0a957baf4cc7225a50522adb0164bf130a9d0` | [MIT](https://github.com/datamllab/rlcard/blob/d7d0a957baf4cc7225a50522adb0164bf130a9d0/LICENSE.md) | 已采用：Python 原始斗地主、UNO 规则代理 |
| [DouZero](https://github.com/kwai/DouZero/tree/718a5c920bf3361e34178a38f3b80458e176b351) | `718a5c920bf3361e34178a38f3b80458e176b351` | [Apache-2.0](https://github.com/kwai/DouZero/blob/718a5c920bf3361e34178a38f3b80458e176b351/LICENSE) | 后续经典斗地主的独立深度学习策略适配候选 |

## RLCard：实际采用的规则基线

[斗地主规则模型](https://github.com/datamllab/rlcard/blob/d7d0a957baf4cc7225a50522adb0164bf130a9d0/rlcard/models/doudizhu_rule_models.py)为 180 行，核心流程是按火箭、炸弹、三张/飞机、顺子、连对、对子、单张拆分自己的手牌；先手选择包含最小牌的组合，跟牌优先选择同牌型中最小的合法压牌。其决策读取自己的 `current_hand`、公共出牌记录 `trace`、地主/自己的位置及合法动作 `actions`，不读取其他座位的私牌。

上游斗地主动作是有序牌点字符串及 `pass`。不能逐字移植其组合处理：最近一手是 `pass` 时，原代码的 `target_player` 仍取该弃牌玩家；`pick_chain(count=2)` 还沿用长度至少 5 的条件，输出没有把牌点复制为对子。CardTable 使用自身规则引擎生成并校验实际组合，将自己的牌和公共信息转换为上游输入；上游选择与本插件合法候选不一致时，按既有合法候选处理。叫分、癞子等不属于上游代理原有输入，需要单独适配。

[UNO 规则模型](https://github.com/datamllab/rlcard/blob/d7d0a957baf4cc7225a50522adb0164bf130a9d0/rlcard/models/uno_rule_models.py)为 124 行，读取自己的 `hand` 与 `raw_legal_actions`。实际实现遇到合法 `wild_draw_4` 时优先打出并选择自己手牌最多的颜色；否则在普通牌可用时保留普通万能牌，从筛选后的合法牌中随机选择。原注释宣称“选择最少颜色”，与实现不同。其动作采用 `颜色-牌型`，例如 `r-3`、`g-wild_draw_4`，另有 `draw`。

[UNO 原规则环境](https://github.com/datamllab/rlcard/blob/d7d0a957baf4cc7225a50522adb0164bf130a9d0/rlcard/games/uno/round.py)只在没有其他可选动作时开放 +4，抽到可出的牌会自动出牌，没有本插件的 +4 挑战和 UNO 宣告窗口。因此只适配策略，继续由 CardTable 的 UNO 引擎处理出牌、抽牌、选色、挑战和漏喊处罚。人机不可读取抽牌堆顺序或对手的私牌。

RLCard 的两个规则模型不需要训练权重。外部进程需要 Python、RLCard 和 NumPy；规则代理无需 PyTorch。这是规则人机，策略强度不等同于训练模型。上游 MIT 版权及许可声明随发布内容保留。

## DouZero：后续高强度经典斗地主候选

[官方推理代理](https://github.com/kwai/DouZero/blob/718a5c920bf3361e34178a38f3b80458e176b351/douzero/evaluation/deep_agent.py)加载 PyTorch 模型，对合法动作分别估值并取最大值；[依赖](https://github.com/kwai/DouZero/blob/718a5c920bf3361e34178a38f3b80458e176b351/requirements.txt)包括 `torch>=1.6.0` 和 RLCard。需要地主、地主上家、地主下家的对应权重，不能仅复制一份短规则文件获得该模型强度。

该固定提交的[主环境](https://github.com/kwai/DouZero/blob/718a5c920bf3361e34178a38f3b80458e176b351/douzero/env/env.py)直接按 20/17/17 发牌并从地主开始出牌，不含叫分动作；[牌型生成器](https://github.com/kwai/DouZero/blob/718a5c920bf3361e34178a38f3b80458e176b351/douzero/env/game.py)使用普通牌点和硬炸弹，没有癞子牌点转换。官方 README 确实链接了另一个叫牌演示，故上述限制仅指这里核对的主环境与权重输入接口，不代表所有关联项目都不支持叫牌。

DouZero 的推理特征使用自己的手牌、对手剩余牌的合并集合、公开出牌记录及各座位牌数。合并集合可以用完整牌组减去自己的牌和所有已出牌推导；不需要知道每个对手具体持有哪张牌。源码环境另保存 `all_handcards`，但标准深度代理的 `get_obs` 不将每个对手的独立私牌作为特征。

官方 `DeepAgent` 支持 CPU 推理；主仓 `baselines/` 只有权重放置提示，预训练权重另存 Google Drive 和百度网盘。本轮未能直接获取官方 Drive 目录，没有安装 PyTorch 或下载模型权重，因此实际运行采用 RLCard 规则代理。准备方法和 JSONL 协议见 [AI 运行环境说明](AI_RUNTIME.md)。如果后续接入 DouZero，需要另做权重许可核对、输入适配和异步推理；结果返回后仍须检查桌号、对局和轮次，并交给既有规则引擎执行。
