from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
s = p.read_text()
needle = 'import androidx.compose.foundation.shape.CircleShape\n'
add = 'import androidx.compose.foundation.shape.RoundedCornerShape\n'
if add not in s:
    if needle not in s:
        raise SystemExit('CircleShape import not found')
    s = s.replace(needle, needle + add, 1)
    p.write_text(s)
    print('Added RoundedCornerShape import')
else:
    print('RoundedCornerShape import already present')
