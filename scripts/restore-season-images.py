#!/usr/bin/env python3
from pathlib import Path
import base64
import hashlib
import sys

ROOT = Path(__file__).resolve().parents[1]
PARTS = ROOT / "app" / "src" / "main" / "season-assets"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
EXPECTED = {
    "season_winter.jpg": "5719fff5bb97658d30916dc00a4386c0261a9e3ef0f3e7ec9f2e0d7ae29381dd",
    "season_spring.jpg": "6e2c10f4ac7a298f2657ea7c5c377a462acd2733f4d9ee5c450e7927ced10e40",
    "season_summer.jpg": "7241bd51f1a29ea2bee637cf8305ac8d264caa2664e18e271b4253d4ea5a5822",
    "season_autumn.jpg": "d68bdcb1781c8b8f79dfb4a095eac2973546bfdbf874d44f54194db853be90c4",
}

OUT.mkdir(parents=True, exist_ok=True)
for filename, expected_hash in EXPECTED.items():
    stem = filename.removesuffix(".jpg")
    part_files = sorted(PARTS.glob(f"{stem}.b64.*"))
    if not part_files:
        sys.exit(f"Missing encoded source parts for {filename}")
    encoded = "".join(p.read_text(encoding="ascii").strip() for p in part_files)
    data = base64.b64decode(encoded, validate=True)
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected_hash:
        sys.exit(f"Hash mismatch for {filename}: {actual}")
    (OUT / filename).write_bytes(data)
    print(f"Restored {filename}: {len(data)} bytes, SHA-256 {actual}")
