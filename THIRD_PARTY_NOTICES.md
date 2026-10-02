# Third-party notices

## RLCard rule agents

CardTable's external Python AI process invokes RLCard's original rule agents.
The plugin adapts its own cards, public observations and legal actions to the
upstream interfaces and validates returned actions using CardTable's existing
rule engine. This is an integration with rule-based agents, not a Kotlin port
and not a trained RLCard model.

Upstream repository: https://github.com/datamllab/rlcard

Reference commit: `d7d0a957baf4cc7225a50522adb0164bf130a9d0`

Reference files:

- https://github.com/datamllab/rlcard/blob/d7d0a957baf4cc7225a50522adb0164bf130a9d0/rlcard/models/doudizhu_rule_models.py
- https://github.com/datamllab/rlcard/blob/d7d0a957baf4cc7225a50522adb0164bf130a9d0/rlcard/models/uno_rule_models.py

Integration scope:

- RLCard provides the original Dou Dizhu and UNO rule-agent decision functions.
- CardTable provides the JSONL transport, observation/action conversion,
  original game rules, bidding, laizi, UNO announcements and challenge handling.
- The Python process requires RLCard and NumPy. These rule agents do not require
  PyTorch or pretrained model weights.

Upstream license: MIT. The following text is reproduced from the reference
commit's `LICENSE.md`:

Copyright (c) 2019 DATA Lab at Texas A&M University

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
