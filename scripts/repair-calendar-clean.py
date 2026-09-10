from pathlib import Path
import subprocess

path = 'app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt'
base = subprocess.check_output(['git', 'show', 'bc4415d40e5fcddd82e674e08b0835324cad9881:' + path], text=True)

base = base.replace('import android.widget.ImageView\n', '')
base = base.replace('import androidx.compose.ui.viewinterop.AndroidView\n', 'import androidx.compose.foundation.Image\nimport androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.res.painterResource\n')

old_photo = '''    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = false
                setImageResource(imageRes)
            }
        },
        update = {
            it.scaleType = ImageView.ScaleType.CENTER_CROP
            it.adjustViewBounds = false
            it.setImageResource(imageRes)
        }
    )'''
new_photo = '''    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )'''
if old_photo not in base:
    raise SystemExit('SeasonalPhoto block not found')
base = base.replace(old_photo, new_photo)

base = base.replace(
    'containerColor = if (selectedDay) accent.copy(alpha = .88f) else Color(0x991B2028)',
    'containerColor = if (day == null) Color.Transparent else if (selectedDay) accent.copy(alpha = .88f) else Color(0x991B2028)'
)
base = base.replace(
    '''border = BorderStroke(
                                1.dp,
                                if (selectedDay) accent.copy(alpha = .95f) else Color.White.copy(alpha = .18f)
                            ),''',
    '''border = if (day == null) null else BorderStroke(
                                1.dp,
                                if (selectedDay) accent.copy(alpha = .95f) else Color.White.copy(alpha = .18f)
                            ),'''
)
base = base.replace(
    'defaultElevation = if (selectedDay) 6.dp else 3.dp',
    'defaultElevation = if (day == null) 0.dp else if (selectedDay) 6.dp else 3.dp'
)

Path(path).write_text(base)
