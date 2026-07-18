package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.HandSide

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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/**
 * A custom text component that automatically scales down its font size to prevent visual horizontal overflow.
 *
 * @param text The text string to render.
 * @param targetFontSize The preferred font size in sp.
 * @param textAlign The alignment of the text.
 * @param modifier Modifier for configuration.
 * @param showShadows Whether to apply drop shadows.
 */
@Composable
fun AutoResizingText(
    text: String,
    targetFontSize: Int,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    showShadows: Boolean = false
) {
    var fontSize by remember(text) { mutableStateOf(targetFontSize.sp) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

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
        style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
    )
}

/**
 * A circular image icon representation of an application package.
 * Supports basic tap and long-press haptic gestures.
 *
 * @param app The application info.
 * @param size The size of the icon in dp.
 * @param onClick Triggered on quick tap.
 * @param onLongClick Triggered on long-press (provides absolute Offset coordinate).
 */
@Composable
fun AppIconItem(
    app: AppInfo, 
    size: Int = 48, 
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {}
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    
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
                        onTap = { currentOnClick() },
                        onLongPress = { currentOnLongClick(itemPosition + it) }
                    )
                }
        )
    }
}

/**
 * A text-only list item representing an application label.
 * Supports click events and auto-resizing capabilities.
 *
 * @param app The application info.
 * @param onClick Triggered on item tap.
 * @param onLongClick Triggered on item long-press (provides coordinate Offset).
 * @param textAlign Horizontal alignment of the label.
 * @param showShadows True to show adaptive drop shadows.
 */
@Composable
fun AppListItem(
    app: AppInfo, 
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
    textAlign: TextAlign = TextAlign.Center,
    showShadows: Boolean = false
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput("${app.packageName}/${app.activityName}") {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick(itemPosition + it) }
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
            textAlign = textAlign,
            showShadows = showShadows
        )
    }
}

/**
 * A composite list item presenting both the application icon and its text label in a horizontal layout.
 * Supports different layout orientations depending on preferred hand side.
 *
 * @param app The application info.
 * @param handSide Layout orientation side (left/right hand).
 * @param fontSize Preferred font size in sp.
 * @param iconSize Preferred icon size in dp.
 * @param onClick Triggered on item tap.
 * @param onLongClick Triggered on item long-press (provides absolute coordinate Offset).
 * @param showShadows True to show text drop shadows.
 */
@Composable
fun AppListItemWithIcon(
    app: AppInfo,
    handSide: HandSide,
    fontSize: Int = 18,
    iconSize: Int = 40,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
    showShadows: Boolean = false
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput("${app.packageName}/${app.activityName}") {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick(itemPosition + it) }
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
                modifier = Modifier.weight(1f),
                showShadows = showShadows
            )
        } else {
            AutoResizingText(
                text = app.label,
                targetFontSize = fontSize,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
                showShadows = showShadows
            )
            Spacer(modifier = Modifier.width(16.dp))
            AppIconItem(app = app, size = iconSize, onClick = onClick, onLongClick = onLongClick)
        }
    }
}
