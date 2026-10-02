# 外部规则 AI

`bridge.py` 在独立 Python 进程里直接导入 RLCard 原始 `DouDizhuRuleAgentV1` 和 `UNORuleAgentV1`，通过标准输入/输出的 JSONL 与 CardTable 通信。不会启动 HTTP 服务。规则代理不需要 PyTorch、GPU 或训练权重；只接收自己的手牌、公开信息和插件提供的合法动作。

## 准备

使用 Python 3.12 或更新版本。在项目目录创建专用环境：

```sh
python3 -m venv ai/env
ai/env/bin/python -m pip install -r ai/requirements.txt
ai/env/bin/python -u ai/bridge.py
```

依赖锁定 RLCard 官方提交 `d7d0a957baf4cc7225a50522adb0164bf130a9d0`、NumPy 2.5.0、termcolor 3.3.0 和 setuptools 84.0.0。setuptools 提供 RLCard 仍在导入的 `distutils.version` 兼容模块。将插件的 AI 命令配置为这套环境的 Python 和 `bridge.py` 的绝对路径；跨机器部署时要重新创建环境，不要复制 venv。启动后第一行应为：

```json
{"ready":true,"engine":"rlcard-rule"}
```

服务器 `plugins/CardTable/config.yml` 配置示例（把 Python 路径改为自己的绝对路径）：

```yaml
ai:
  enabled: true
  command:
    - /absolute/path/to/ai/env/bin/python
    - -u
    - "{data}/ai/bridge.py"
```

空桌时执行 `/ct reload`，日志出现“外部 AI 已就绪”后即可 `/ct join <桌名>`、`/ct bot`、`/ct ready`。人机只允许免费桌。

stdout 仅用于协议，导入日志和诊断写到 stderr。插件在异步线程串行发送请求；超时或进程故障由插件结束相应对局，不自动重发未确认的操作。

## 协议

一行请求对应一行响应：

```json
{"id":1,"game":"uno","state":{"hand":["r-3","g-4"],"target":"r-8","legal_actions":["r-3"],"action_map":{"r-3":{"action":"play","argument":"12"}}}}
```

```json
{"id":1,"action":"play","argument":"12"}
```

异常返回 `{"id":1,"error":"说明"}`。`action_map` 把上游的牌点动作映射为本插件操作及真实手牌编号，插件继续检查对局、当前轮次与动作合法性。

UNO 颜色为 `r/y/g/b`，牌型为 `0..9/skip/reverse/draw_2/wild/wild_draw_4`。万能牌的合法动作包含四种选色，分别映射到同一张实际牌；随后由插件完成选色。没有可出牌时唯一动作 `draw` 映射为摸牌，摸过后无可出新牌时映射为“不要”。代理不接管 UNO 宣告、选色或接受/挑战流程。

斗地主 `phase` 为 `bid/play`，`current_hand` 使用按点数排序的 `3..9/T/J/Q/K/A/2/B/R` 字符串，`self/landlord` 为座位号，`trace` 为 `[座位号,牌点字符串或pass]`，`legal_actions` 为合法牌点字符串列表，`action_map` 映射为操作及实际牌编号。叫分额外传 `highest_bid`，由包装程序按自己的高牌、炸弹与三张数量评估；RLCard 原代理没有叫分模型。

## 上游边界适配

包装程序保持上游规则代理文件不变。斗地主上游组合生成存在连对等边界问题，输入适配后仍须检查上游返回值是否属于本次合法集合。代理抛出已知选择错误或返回插件合法集合外的动作时，包装程序只从本次请求的合法动作中选一项：斗地主选包含最小牌的短组合，UNO 选合法列表首项。合法性来自插件规则引擎，包装程序不会构造新的非法出牌，也不会访问其他玩家手牌。

这是规则人机；没有宣称使用训练模型或达到 DouZero 的策略强度。详细调研和许可见 [AI_SOURCES.md](../docs/AI_SOURCES.md) 与 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
