from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/PersonalLayoutScreen.kt')
text = path.read_text()


def once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected 1 match, found {count}')
    text = text.replace(old, new, 1)


once(
    'import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable',
    'import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectDragGestures',
    'gesture import',
)
once(
    'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.text.font.FontWeight',
    'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.text.font.FontWeight',
    'ui imports',
)

once(
    '"Välj en widget. Du kan göra den halv- eller fullbredd, flytta den, ta bort den eller lägga till nya widgetar."',
    '"Tryck på en widget. Dra i ≡-handtaget för att flytta den och dra i ↔-handtaget för att ändra bredd. Du kan också ta bort eller lägga till widgetar."',
    'edit help text',
)

once(
    '''                        onToggleWidth = {
                            val next = if (width == 2) 1 else 2
                            PersonalLayoutStore.saveWidgetWidth(prefs, profile, module, next)
                            widthRevision++
                        },''',
    '''                        onSetWidth = { newWidth ->
                            PersonalLayoutStore.saveWidgetWidth(prefs, profile, module, newWidth)
                            widthRevision++
                        },''',
    'set width callback',
)

once(
    '''    onSelect: () -> Unit,
    onToggleWidth: () -> Unit,
    onMoveUp: () -> Unit,''',
    '''    onSelect: () -> Unit,
    onSetWidth: (Int) -> Unit,
    onMoveUp: () -> Unit,''',
    'widget callback signature',
)

old_body = '''    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else if (editMode) BorderStroke(1.dp, Color.White.copy(alpha = .12f)) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (selected) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(module.label, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Flytta upp", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Flytta ner", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Ta bort", modifier = Modifier.size(18.dp))
                            }
                        }
                        OutlinedButton(
                            onClick = onToggleWidth,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (widthColumns == 2) "Storlek: full bredd · tryck för halv" else "Storlek: halv bredd · tryck för full", fontSize = 10.sp)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (editMode) Modifier.clickable(onClick = onSelect) else Modifier)
            ) {
                content()
            }
        }
    }
'''

new_body = '''    val density = LocalDensity.current
    val reorderThresholdPx = with(density) { 64.dp.toPx() }
    val resizeThresholdPx = with(density) { 34.dp.toPx() }
    var dragOffsetY by remember(module) { mutableFloatStateOf(0f) }
    var resizeOffsetX by remember(module) { mutableFloatStateOf(0f) }

    Card(
        modifier = modifier.graphicsLayer { translationY = if (selected) dragOffsetY else 0f },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else if (editMode) BorderStroke(1.dp, Color.White.copy(alpha = .12f)) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (selected) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(module.label, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Ta bort widget", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = .07f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .pointerInput(module, canMoveUp, canMoveDown) {
                                        detectDragGestures(
                                            onDragStart = {
                                                onSelect()
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = { dragOffsetY = 0f },
                                            onDragCancel = { dragOffsetY = 0f }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            if (dragOffsetY <= -reorderThresholdPx && canMoveUp) {
                                                onMoveUp()
                                                dragOffsetY += reorderThresholdPx
                                            } else if (dragOffsetY >= reorderThresholdPx && canMoveDown) {
                                                onMoveDown()
                                                dragOffsetY -= reorderThresholdPx
                                            }
                                        }
                                    }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("≡  Flytta", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable { onSetWidth(if (widthColumns == 2) 1 else 2) }
                                    .pointerInput(module, widthColumns) {
                                        detectDragGestures(
                                            onDragStart = { resizeOffsetX = 0f },
                                            onDragEnd = { resizeOffsetX = 0f },
                                            onDragCancel = { resizeOffsetX = 0f }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            resizeOffsetX += dragAmount.x
                                            if (resizeOffsetX <= -resizeThresholdPx) {
                                                if (widthColumns != 1) onSetWidth(1)
                                                resizeOffsetX = 0f
                                            } else if (resizeOffsetX >= resizeThresholdPx) {
                                                if (widthColumns != 2) onSetWidth(2)
                                                resizeOffsetX = 0f
                                            }
                                        }
                                    }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (widthColumns == 2) "↔  Full" else "↔  Halv",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (editMode) Modifier.clickable(onClick = onSelect) else Modifier)
            ) {
                content()
            }
        }
    }
'''

once(old_body, new_body, 'editable widget body')

# Arrow imports are no longer needed in the direct widget editor, but are still
# used by PersonalLayoutEditor above, so they intentionally remain.

path.write_text(text)
print('Applied drag and resize widget editing')
