# 外部规则 AI

`bridge.py` 在独立 Python 进程里直接导入 RLCard 原始 `DouDizhuRuleAgentV1` 和 `UNORuleAgentV1`，通过标准输入/输出的 JSONL 与 CardTable 通信。不会启动 HTTP 服务。规则代理不需要 PyTorch、GPU 或训练权重；只接收自己的手牌、公开信息和插件提供的合法动作。

源码运行资源位于 `src/main/resources/ai/`，构建后打入 JAR 的 `ai/` 路径，启动时释放到下述服务端目录。

新服默认从 JAR 释放 `namemc-cache.json`，预置 59 个机器人名称和 8 套签名皮肤。已有当前格式的统一缓存时直接读取；不迁移旧文件。启动时继续异步刷新 NameMC，刷新失败使用缓存，无需手动从测试服复制。

## 自动准备

从 1.3.16 开始，默认启用自动管理。启动插件即可，服务器无需预装 Python、pip 或 uv，也无需配置绝对路径。

首次启动会在 `plugins/CardTable/env/` 下载固定版 uv、CPython 3.14.7，并安装固定提交的 RLCard 与依赖。准备在后台进行，不阻塞服务器主线程；日志出现`External AI is ready: rlcard-rule.`后即可添加人机。以后每次启动复用已经准备好的环境并重新启动 AI 进程；依赖清单、解释器版本或环境路径变化时会重新准备。下载或安装失败后保留诊断日志，下次启动或空桌时 `/ct reload` 会再次准备。

```yaml
ai:
  enabled: true
  mode: managed
  command: []
```

配置缺少 `ai.mode` 时也采用 `managed`。旧的 `ai.command` 在此模式下不使用；显式关闭的 `ai.enabled: false` 会保留。自定义程序只有切换到下方 `external` 模式才会使用。

目录结构：

```text
plugins/CardTable/env/
  README.md          运行环境说明
  bin/               已校验的 uv 可执行程序
  python/            插件专用的 Python 解释器
  venv/              RLCard 与运行依赖
  cache/  tmp/       下载、构建缓存和临时文件
  bridge.py          随插件更新的 JSONL 桥接程序
  requirements.txt   随插件更新的依赖清单
  .ready             已完成安装的版本指纹
  setup.log          下载与安装子进程日志
  process.log        AI 进程的 stderr 日志
```

当前自动管理支持 Linux glibc x64/ARM64、Windows x64/ARM64 和 macOS Intel/Apple Silicon；依赖 wheel 要求 Linux glibc ≥2.27，Intel macOS ≥10.15。Alpine/musl 不在当前自动管理支持范围。首次准备需要能够访问 PyPI/files.pythonhosted.org、GitHub 及其下载站点；复用完整环境时无需联网安装。

uv 固定为 0.12.15，下载地址和 SHA-256 固定在 `uv-runtime.json`，校验通过才提取可执行文件。Python 固定为 3.14.7、独立构建版本 20260901，由 uv 下载并校验。依赖锁定 RLCard 官方提交 `d7d0a957baf4cc7225a50522adb0164bf130a9d0`、NumPy 2.5.0、termcolor 3.3.0、setuptools 84.0.0 和 pip 26.2.1。后两项分别用于 RLCard 的 `distutils.version` 导入和导入期间的 `pip freeze`。

下载 uv 最多等待 3 分钟，每条安装命令最多等待 10 分钟；环境准备不占用 AI 的 30 秒就绪超时。重载或关闭插件会取消准备工作，并结束安装进程及 AI 进程；重新准备前会等待上一轮安装退出。中断的安装不会写入完成标记。旧的手动 `ai/env` 环境和 `ai/bridge.py` 不会被删除。

玩家右键空位，再点击全息“补齐人机”和“准备”即可开始；命令 `/ct join <桌名>`、`/ct bot`、`/ct ready` 也继续保留。人机只允许免费桌。

## 自定义外部 AI（可选）

需要其他 AI 程序时才配置 `mode: external` 和命令数组。例如，已经自行安装依赖的 Python 程序：

```yaml
ai:
  enabled: true
  mode: external
  command:
    - /absolute/path/to/another/env/bin/python
    - -u
    - "{data}/ai/bridge.py"
```

该模式不下载或修改自定义运行环境。命令不经过 shell；`{data}` 替换为插件数据目录，stderr 仍写入 `env/process.log`。程序启动后需要输出：

```json
{"ready":true,"engine":"rlcard-rule"}
```

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

这是规则人机；没有宣称使用训练模型或达到 DouZero 的策略强度。详细调研和许可见 [AI_SOURCES.md](AI_SOURCES.md) 与 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
