#!/usr/bin/env python3
from pathlib import Path
import hashlib
import sys

EXPECTED = {
    "season_winter.jpg": "2670b9fa42de076462531af944622fe4f6d8e54eae987f5715778e0e3ea7b5e8",
    "season_spring.jpg": "346f863bb1f5a370cdec1fa5dae492bd6d8f1ae6c568e17bfe6bf541edbd6f44",
    "season_autumn.jpg": "74c9e709baf521863767de9d125775319e4238afe974cbbb7773adb72b7c9e09",
    "season_summer.jpg": "9311ef97ff763ef06088243530329fb2ebbaaef71e04e9f8d3ed5c39555eb97a",
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
