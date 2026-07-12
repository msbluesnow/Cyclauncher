package dev.msbs.cyclauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoResizingText(
    text: String,
    targetFontSize: Int,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text) { mutableStateOf(targetFontSize.sp) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = if (readyToDraw) Color.White else Color.Transparent,
        modifier = modifier,
        textAlign = textAlign,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && fontSize.value > 8f) {
                fontSize = (fontSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        },
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun AppIconItem(
    app: AppInfo, 
    size: Int = 48, 
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {}
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    
    app.icon?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .onGloballyPositioned { itemPosition = it.positionInRoot() }
                .pointerInput("${app.packageName}/${app.activityName}") {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick(itemPosition + it) }
                    )
                }
        )
    }
}

@Composable
fun AppListItem(
    app: AppInfo, 
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
    textAlign: TextAlign = TextAlign.Center
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput("${app.packageName}/${app.activityName}") {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick(itemPosition + it) }
                )
            }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = when(textAlign) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        AutoResizingText(
            text = app.label,
            targetFontSize = 20,
            textAlign = textAlign
        )
    }
}

@Composable
fun AppListItemWithIcon(
    app: AppInfo,
    handSide: HandSide,
    fontSize: Int = 18,
    iconSize: Int = 40,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {}
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput("${app.packageName}/${app.activityName}") {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick(itemPosition + it) }
                )
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
    ) {
        if (handSide == HandSide.LEFT) {
            AppIconItem(app = app, size = iconSize, onClick = onClick, onLongClick = onLongClick)
            Spacer(modifier = Modifier.width(16.dp))
            AutoResizingText(
                text = app.label,
                targetFontSize = fontSize,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        } else {
            AutoResizingText(
                text = app.label,
                targetFontSize = fontSize,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            AppIconItem(app = app, size = iconSize, onClick = onClick, onLongClick = onLongClick)
        }
    }
}
