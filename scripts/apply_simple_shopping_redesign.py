from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
start = text.index('@Composable\nprivate fun ShoppingScreen(')
end = text.index('\nprivate fun shareFamilyInvite', start)
screen = text[start:end]

# Keep the simple shopping design, but never strip the app-wide motion polish.
if 'val motionEnabled = appMotionEnabled()' not in screen:
    screen = screen.replace(
        ') {\n    var text by remember { mutableStateOf("") }',
        ') {\n    val motionEnabled = appMotionEnabled()\n    var text by remember { mutableStateOf("") }',
        1,
    )

if 'val animatedProgress by animateFloatAsState' not in screen:
    screen = screen.replace(
        '    val done = checkedItems.size\n',
        '''    val done = checkedItems.size
    val progressTarget = if (total == 0) 0f else done.toFloat() / total.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(motionDuration(320, motionEnabled)),
        label = "shopping-progress"
    )
''',
        1,
    )

screen = screen.replace(
    'if (total > 0) Text("${done * 100 / total}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)',
    'if (total > 0) Text("${(animatedProgress * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)',
    1,
)
screen = screen.replace(
    'LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)))',
    'LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)))',
    1,
)

# Animate the list as one calm surface so adds/check-offs/removals do not jump.
if 'label = "shopping-items"' not in screen:
    list_marker = '    if (openItems.isEmpty() && checkedItems.isEmpty()) {'
    confirm_marker = '    if (showClearConfirmation) {'
    list_start = screen.index(list_marker)
    confirm_start = screen.index(confirm_marker, list_start)
    list_block = screen[list_start:confirm_start].rstrip()
    list_block = (
        list_block
        .replace('openItems', 'visibleOpen')
        .replace('checkedItems', 'visibleChecked')
        .replace('grouped', 'visibleGrouped')
    )
    indented = '\n'.join(('        ' + line) if line else '' for line in list_block.splitlines())
    animated_block = '''    AnimatedContent(
        targetState = items,
        transitionSpec = {
            (fadeIn(tween(motionDuration(170, motionEnabled))) +
                slideInVertically(tween(motionDuration(210, motionEnabled))) { it / 16 }) togetherWith
                (fadeOut(tween(motionDuration(120, motionEnabled))) +
                    slideOutVertically(tween(motionDuration(170, motionEnabled))) { -it / 18 })
        },
        label = "shopping-items"
    ) { visibleItems ->
        val visibleOpen = visibleItems.filterNot { it.checked }
        val visibleChecked = visibleItems.filter { it.checked }
        val visibleGrouped = visibleOpen.groupBy { categoryFor(it.name) }
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(tween(motionDuration(220, motionEnabled)))
        ) {
''' + indented + '''
        }
    }

'''
    screen = screen[:list_start] + animated_block + screen[confirm_start:]

path.write_text(text[:start] + screen + text[end:], encoding='utf-8')
print('Applied simple shopping redesign while preserving motion polish')
