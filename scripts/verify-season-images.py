#!/usr/bin/env python3
from pathlib import Path
import hashlib
import sys

EXPECTED = {
    "season_winter.jpg": "5719fff5bb97658d30916dc00a4386c0261a9e3ef0f3e7ec9f2e0d7ae29381dd",
    "season_spring.jpg": "6e2c10f4ac7a298f2657ea7c5c377a462acd2733f4d9ee5c450e7927ced10e40",
    "season_autumn.jpg": "d68bdcb1781c8b8f79dfb4a095eac2973546bfdbf874d44f54194db853be90c4",
    "season_summer.jpg": "7241bd51f1a29ea2bee637cf8305ac8d264caa2664e18e271b4253d4ea5a5822",
}

root = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "drawable-nodpi"
errors = []
for name, expected in EXPECTED.items():
    path = root / name
    if not path.is_file():
        errors.append(f"missing: {path}")
        continue
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        errors.append(f"wrong image: {name}\n  expected {expected}\n  actual   {actual}")

if errors:
    print("Season image verification FAILED")
    print("\n".join(errors))
    sys.exit(1)

print("Season image verification OK: all four files exactly match the approved uploads")
