#!/usr/bin/env python3
from pathlib import Path
import sys

EXPECTED = (
    "season_spring.webp",
    "season_summer.webp",
    "season_autumn.webp",
    "season_winter.webp",
)

root = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "drawable-nodpi"
errors = []
for name in EXPECTED:
    path = root / name
    if not path.is_file():
        errors.append(f"missing: {path}")
        continue
    data = path.read_bytes()
    if len(data) < 100_000:
        errors.append(f"image unexpectedly small: {name} ({len(data)} bytes)")
    if not (data.startswith(b"RIFF") and data[8:12] == b"WEBP"):
        errors.append(f"not a valid WebP container: {name}")

if errors:
    print("Season image verification FAILED")
    print("\n".join(errors))
    sys.exit(1)

print("Season image verification OK: all four approved high-resolution WebP resources are present")
