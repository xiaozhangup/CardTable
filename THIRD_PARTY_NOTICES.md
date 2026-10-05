# Third-party notices

## NameMC discovery and Mojang skin profiles

Bot names are discovered from https://namemc.com/minecraft-names at startup.
Bot skin authors are discovered from https://namemc.com/minecraft-skins and the
linked skin pages. Their current texture properties are resolved through the
public Mojang profile and session services; they may differ from a historical
skin shown on NameMC. Existing disk caches are retained if discovery fails.
No NameMC application code or fixed collection of skin images is bundled.

## Custom-Nameplates implementation reference

The client-only participant status display was independently implemented after
studying the virtual TextDisplay and passenger-packet approach in
https://github.com/Xiao-MoMi/Custom-Nameplates at commit
`61d66eb7316f39e18a5ea6637688da6752a621eb`.

Reference paths: `api/.../feature/tag/AbstractTag.java`,
`backend/.../feature/tag/TagRendererImpl.java`, and
`platforms/bukkit/.../BukkitPlatform.java`.
Upstream is GPL-3.0-or-later. No upstream source code is copied or bundled, and
Custom-Nameplates is not a runtime dependency. CardTable attaches the virtual
status to its existing seat, alongside the avatar, to preserve vanilla names.

## Dou Dizhu voice recordings

The CraftEngine resource pack includes selected male and female MP3 recordings
from https://github.com/palemoky/fight-the-landlord, converted to mono Vorbis OGG.
No upstream application code is included in the plugin.

Reference commit: `6a2b07bd71a21e0c564277a235846e7f3cf589b1`

Source directory:
https://github.com/palemoky/fight-the-landlord/tree/6a2b07bd71a21e0c564277a235846e7f3cf589b1/internal/sound/gaming/voices

The upstream repository supplies a GNU GPL version 3 license. A complete copy
is preserved in `craftengine/cardtable/licenses/fight-the-landlord/LICENSE.txt`,
alongside `NOTICE.txt`. Copies are also included in the client resource pack
under `licenses/fight-the-landlord/`.

The repository does not identify the recordings' original authors, original
source, or a separate audio license. Recording ownership and authorization have
not been independently verified; the repository license is reported as found,
not as proof that the repository owner holds rights to the recordings.

CardTable integration, 2026-10-05:

- 108 selected recordings, covering both voices' ranks, combinations, bidding
  and passing, are included as 106 sound events; each pass event has two variants.
- Original MP3 files are retained unchanged under
  `craftengine/cardtable/sources/fight-the-landlord/`, with upstream relative paths.
- `craftengine/cardtable/metadata/doudizhu-voices.json` records each source path,
  SHA-256, semantic caption, output event and conversion settings.
- `tools/import_doudizhu_voices.py` reproduces the conversion from those sources.
  Output is mono 44.1 kHz Vorbis, with constant peak normalization and limited
  gain. Semantic captions
  are based on upstream names and code, not verified word-for-word transcripts.
- No unrelated background music, chat phrases, or sound effects are imported.
  CardTable's pixel artwork, furniture and note-block melody are separate works.

The original sources and notices ship in the CraftEngine distribution ZIP;
only converted OGG and client notices are needed in the Minecraft download.

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


## Managed runtime downloads

CardTable downloads, rather than embeds, the following runtime components into
its own data directory. Their upstream licenses and notices remain applicable.

- Astral uv 0.12.15 (Apache-2.0 OR MIT): https://github.com/astral-sh/uv/tree/0.12.15
  Fixed official wheel URLs and SHA-256 digests: `src/main/resources/ai/uv-runtime.json`, sourced from
  https://pypi.org/pypi/uv/0.12.15/json .
- CPython 3.14.7, standalone build 20260901: Python Software Foundation License
  and the bundled dependencies' licenses. Downloaded and checksum-verified by uv
  using its fixed download metadata:
  https://github.com/astral-sh/uv/blob/0.12.15/crates/uv-python/download-metadata.json .
  Build project: https://github.com/astral-sh/python-build-standalone .
- NumPy 2.5.0 (BSD-3-Clause), termcolor 3.3.0 (MIT), setuptools 84.0.0 (MIT),
  pip 26.2.1 (MIT): obtained from PyPI with their package metadata and licenses.

Runtime installation guidance: https://docs.astral.sh/uv/guides/install-python/

## Bundled NameMC cache

`src/main/resources/namemc-cache.json` preserves the local test server snapshot
retrieved on 2026-10-05: 59 names from https://namemc.com/minecraft-names and
8 signed skin profile entries selected through https://namemc.com/minecraft-skins
and resolved through the official Mojang session service. The snapshot retains
source metadata, profile IDs, texture properties and signatures.
