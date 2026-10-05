# CardTable 自动管理的 AI 运行环境

本目录由 CardTable 自动创建和维护，无需预装 Python 或手动执行安装命令。

- `bin/`：固定版 uv 安装器，下载后校验 SHA-256。
- `python/`：CPython 3.14.7（独立构建 20260901），只供本插件使用。
- `venv/`：RLCard 规则代理及 requirements.txt 指定的依赖。
- `cache/`、`tmp/`：本环境下载、构建缓存与临时文件。
- `bridge.py`、`requirements.txt`：每次准备时从插件 JAR 更新，不要在此修改。
- `.ready`：安装完成标记；依赖或解释器版本变化后重新准备。
- `setup.log`：安装子进程日志；`process.log`：规则 AI 的错误输出。

首次启动在后台下载并安装，后续启动直接复用。出现`External AI is ready: rlcard-rule.`后才能添加人机。
下载或安装失败请先查看服务器日志及 setup.log；网络恢复后，在无人入座时执行 `/ct reload` 或正常重启即可重新准备。
关闭插件会结束它启动的安装和 AI 进程。需要重建整个环境时先正常停服，再删除本目录并启动；首次准备需要联网。

默认 `ai.mode: managed`。只有明确需要自定义外部 AI 时才切换 `external` 并填写 `ai.command`。
本目录不应提交到源码仓库，也不要跨机器复制 venv。

来源：Astral uv (https://github.com/astral-sh/uv)，Python standalone builds (https://github.com/astral-sh/python-build-standalone)，RLCard (https://github.com/datamllab/rlcard)。
