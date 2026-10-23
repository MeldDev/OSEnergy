"""Merge saved waypoints across the existing McSkill connection aliases."""
import json
from datetime import datetime
from pathlib import Path
import shutil
import sys

ROOT = Path(r"C:\Users\DESKTOP\McSkill\clients\Industrial_1.7.10\journeymap\data\mp")
ALIASES = (
    "proxy-1.metalabsmc.net_25600",
    "proxy-2.metalabsmc.net_25600",
    "proxy-3.metalabsmc.net_25600",
    "titan.skydrive.metalabs.work",
)


def main():
    originals = {}
    merged = {}
    for alias in ALIASES:
        directory = ROOT / alias / "waypoints"
        if not directory.is_dir():
            raise RuntimeError(f"Missing expected directory: {directory}")
        for path in sorted(directory.glob("*.json")):
            raw = path.read_bytes()
            data = json.loads(raw.decode("utf-8-sig"))
            key = data["id"]
            if key in merged and merged[key][2] != data:
                raise RuntimeError(f"Conflicting waypoint: {key!a}; no files changed")
            originals[path] = raw
            merged[key] = (path.name, raw, data)

    additions = []
    for alias in ALIASES:
        directory = ROOT / alias / "waypoints"
        for filename, raw, data in merged.values():
            target = directory / filename
            if not target.exists():
                additions.append((target, raw))
            elif json.loads(target.read_text(encoding="utf-8-sig")) != data:
                raise RuntimeError(f"Conflicting destination: {target}; no files changed")

    print(f"Found {len(merged)} unique waypoints; {len(additions)} files to add")
    if "--apply" not in sys.argv:
        return

    backup = Path(__file__).resolve().parent / "backups" / (
        "journeymap-waypoints-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    )
    backup.mkdir(parents=True)
    for source, raw in originals.items():
        dest = backup / source.relative_to(ROOT)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(raw)
        shutil.copystat(source, dest)
    (backup / "restore-manifest.json").write_text(json.dumps({
        "root": str(ROOT),
        "added_files": [str(path.relative_to(ROOT)) for path, _ in additions],
        "waypoint_count": len(merged),
    }, ensure_ascii=True, indent=2), encoding="utf-8")

    for target, raw in additions:
        with target.open("xb") as stream:
            stream.write(raw)

    for path, raw in originals.items():
        assert path.read_bytes() == raw, f"Original changed: {path}"
    for alias in ALIASES:
        actual = {}
        for path in (ROOT / alias / "waypoints").glob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8-sig"))
            actual[data["id"]] = data
        assert actual == {key: entry[2] for key, entry in merged.items()}, alias
        print(f"Verified {alias}: {len(actual)} waypoints")
    print(f"Backup: {backup}")


if __name__ == "__main__":
    main()
