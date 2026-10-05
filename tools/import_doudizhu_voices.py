#!/usr/bin/env python3
"""Rebuild the bundled Dou Dizhu voices from pinned, SHA-256-checked MP3 sources.

Requires Python 3 and FFmpeg. Original MP3s are included outside the client pack;
missing source files are downloaded from the commit in doudizhu-voices.json.
This command owns doudizhu/sounds/voice, sounds.json, and subtitle language files.
"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "craftengine" / "cardtable"
ASSETS = PACK / "resourcepack" / "assets" / "doudizhu"
MANIFEST = PACK / "metadata" / "doudizhu-voices.json"


def save_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def verified_source(path: Path, url: str, expected_sha256: str) -> Path:
    if path.exists():
        data = path.read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=45) as response:
            data = response.read()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected_sha256:
        raise ValueError(f"Source SHA-256 mismatch for {path}: expected {expected_sha256}, got {actual}")
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    return path


def convert(source: Path, destination: Path, settings: dict) -> None:
    mono = f"aformat=channel_layouts=mono,aresample={settings['sample_rate']}"
    measured = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostdin", "-i", str(source), "-af", mono + ",volumedetect",
         "-f", "null", "-"], check=True, capture_output=True, text=True,
    )
    peak = float(re.search(r"max_volume: ([-\d.]+) dB", measured.stderr).group(1))
    gain = min(settings["maximum_gain_db"], settings["peak_target_dbfs"] - peak)
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", str(source),
         "-map_metadata", "-1", "-af", f"{mono},volume={gain:.4f}dB", "-ac", str(settings["channels"]),
         "-ar", str(settings["sample_rate"]), "-c:a", settings["codec"], "-q:a", str(settings["quality"]),
         "-fflags", "+bitexact", "-flags:a", "+bitexact", str(destination)], check=True,
    )


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    source_dir = PACK / manifest["source_directory"]
    license_info = manifest["upstream_license"]
    license_file = verified_source(
        PACK / license_info["local_file"], manifest["raw_base_url"] + license_info["path"],
        license_info["sha256"],
    )
    sounds = {}
    subtitles = {"zh_cn": {}, "en_us": {}}
    voices = {}
    with tempfile.TemporaryDirectory(prefix="cardtable-voices-") as temporary:
        generated = Path(temporary)
        for clip in manifest["clips"]:
            source = verified_source(source_dir / clip["path"], manifest["raw_base_url"] + clip["path"], clip["sha256"])
            convert(source, generated / clip["file"], manifest["conversion"])
            event = clip["event"]
            subtitle = "subtitles.doudizhu." + event
            sound = sounds.setdefault("voice." + event, {"subtitle": subtitle, "sounds": []})
            sound["sounds"].append({"name": "doudizhu:voice/" + clip["file"].removesuffix(".ogg"), "stream": False})
            subtitles["zh_cn"][subtitle] = "斗地主: " + clip["subtitle_zh_cn"]
            subtitles["en_us"][subtitle] = "Dou Dizhu: " + clip["subtitle_en_us"]
            voices[event] = clip["subtitle_zh_cn"]
        voice_dir = ASSETS / "sounds" / "voice"
        for previous in voice_dir.rglob("*.ogg"):
            previous.unlink()
        shutil.copytree(generated, voice_dir, dirs_exist_ok=True)

    save_json(ASSETS / "sounds.json", sounds)
    for language, entries in subtitles.items():
        save_json(ASSETS / "lang" / (language + ".json"), entries)
    metadata_path = PACK / "metadata" / "doudizhu.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["voices"] = voices
    metadata["voice_source"] = {
        "manifest": "metadata/doudizhu-voices.json", "repository": manifest["repository"],
        "commit": manifest["commit"], "events": len(sounds), "audio_files": len(manifest["clips"]),
        "sample_rate": manifest["conversion"]["sample_rate"], "channels": manifest["conversion"]["channels"],
    }
    save_json(metadata_path, metadata)

    notice = f"""Dou Dizhu voice recordings used by CardTable

Source repository: {manifest['repository']}
Pinned commit: {manifest['commit']}
Original paths: internal/sound/gaming/voices/female/ and internal/sound/gaming/voices/male/
Source tree: {manifest['repository']}/tree/{manifest['commit']}/internal/sound/gaming/voices

The upstream repository contains the GNU General Public License version 3.
Its complete, unmodified license is included as LICENSE.txt. No separate audio
license, original recording author, or recording-specific authorization was
confirmed. The repository license alone does not establish that provenance.
CardTable does not claim authorship of these recordings or confirmed original
audio rights.

CardTable adaptation date: {manifest['adaptation_date']}
CardTable modifications: {manifest['conversion']['processing']}
Event names were adapted to CardTable. Subtitles describe the game event using
the upstream sound mapping and original Chinese filenames; they are not verified
word-for-word transcripts. No upstream application source code is included.

SOURCE-MANIFEST.json lists every original file, source URL base, original SHA-256,
and conversion settings. The unmodified source MP3s are included in the CardTable
CraftEngine distribution under sources/fight-the-landlord/, outside resourcepack/.
They can also be retrieved from the pinned raw URLs recorded in the manifest.
Rebuild command from the CardTable project: python3 tools/import_doudizhu_voices.py
"""
    notice_file = license_file.parent / "NOTICE.txt"
    notice_file.write_text(notice, encoding="utf-8")
    shutil.copyfile(MANIFEST, license_file.parent / "SOURCE-MANIFEST.json")
    client_notices = PACK / "resourcepack" / "licenses" / "fight-the-landlord"
    client_notices.mkdir(parents=True, exist_ok=True)
    for path in (license_file, notice_file):
        shutil.copyfile(path, client_notices / path.name)
    shutil.copyfile(MANIFEST, client_notices / "SOURCE-MANIFEST.json")
    print(f"Imported {len(manifest['clips'])} voice clips and {len(sounds)} sound events; original sources and notices preserved.")


if __name__ == "__main__":
    main()
