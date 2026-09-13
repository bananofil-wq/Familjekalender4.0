#!/usr/bin/env python3
import struct
import sys
import zlib
from pathlib import Path

path = Path(sys.argv[1] if len(sys.argv) > 1 else "phase1-calendar.png")
data = path.read_bytes()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("Preview is not a PNG")

pos = 8
width = height = color_type = bit_depth = None
chunks = []
while pos < len(data):
    length = struct.unpack(">I", data[pos:pos+4])[0]
    kind = data[pos+4:pos+8]
    payload = data[pos+8:pos+8+length]
    pos += 12 + length
    if kind == b"IHDR":
        width, height, bit_depth, color_type, *_ = struct.unpack(">IIBBBBB", payload)
    elif kind == b"IDAT":
        chunks.append(payload)
    elif kind == b"IEND":
        break

if bit_depth != 8 or color_type not in (2, 6):
    raise SystemExit(f"Unsupported PNG format: depth={bit_depth}, type={color_type}")

bpp = 3 if color_type == 2 else 4
raw = zlib.decompress(b"".join(chunks))
stride = width * bpp
prev = bytearray(stride)
bright = total = 0
offset = 0

for _ in range(height):
    filt = raw[offset]
    offset += 1
    row = bytearray(raw[offset:offset+stride])
    offset += stride
    for x in range(stride):
        a = row[x-bpp] if x >= bpp else 0
        b = prev[x]
        c = prev[x-bpp] if x >= bpp else 0
        if filt == 1:
            row[x] = (row[x] + a) & 255
        elif filt == 2:
            row[x] = (row[x] + b) & 255
        elif filt == 3:
            row[x] = (row[x] + ((a + b) >> 1)) & 255
        elif filt == 4:
            p = a + b - c
            pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
            pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
            row[x] = (row[x] + pr) & 255
    for x in range(0, stride, bpp):
        r, g, b = row[x], row[x+1], row[x+2]
        total += 1
        if max(r, g, b) > 28:
            bright += 1
    prev = row

ratio = bright / max(total, 1)
print(f"Preview size: {width}x{height}")
print(f"Non-dark pixel ratio: {ratio:.3f}")
if ratio < 0.08:
    raise SystemExit("Preview is still effectively black")
