package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.theme.LocalIconPackVersion

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Text component that automatically scales down font size to prevent horizontal visual overflow.
 */
@Composable
fun AutoResizingText(
    text: String,
    targetFontSize: Int,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false
) {
    val shadowSettings = LocalShadowSettings.current
    val shadow = primaryTextColor.getShadow(showShadows || shadowSettings.showShadows, shadowSettings.shadowColorOverride)

    BoxWithConstraints(modifier = modifier) {
        val containerWidth = maxWidth
        var fontSize by remember(text, targetFontSize, containerWidth) { mutableStateOf(targetFontSize.sp) }
        var readyToDraw by remember(text, targetFontSize, containerWidth) { mutableStateOf(text.length <= 14) }

        Text(
            text = text,
            color = if (readyToDraw) primaryTextColor.color else Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
            textAlign = textAlign,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow && fontSize.value > 10f) {
                    fontSize = (fontSize.value * 0.85f).sp
                } else {
                    readyToDraw = true
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
        )
    }
}

/**
 * Loads an app icon asynchronously via Coil.
 */
@Composable
fun rememberAppIconPainter(iconKey: String, sizeDp: Int = 48): Painter {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val iconPackVersion = LocalIconPackVersion.current
    return rememberAsyncImagePainter(
        model = remember(iconKey, sizeDp, density, iconPackVersion) {
            val px = with(density) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)
            ImageRequest.Builder(context)
                .data(dev.msbs.cyclauncher.coil.AppIconKey(iconKey))
                .size(px)
                .diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    )
}

/**
 * Circular app icon with click and long-press gestures.
 */
@Composable
fun AppIconItem(
    app: AppInfo,
    size: Int = 48,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {}
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentItemPosition by rememberUpdatedState(itemPosition)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val painter: Painter = rememberAppIconPainter(app.iconKey, size)

    Image(
        painter = painter,
        contentDescription = app.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput(app.componentKey) {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick(currentItemPosition + it) }
                )
            }
    )
}

/**
 * Text-only app list item with auto-resizing label.
 */
@Composable
fun AppListItem(
    app: AppInfo, 
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
    textAlign: TextAlign = TextAlign.Center,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentItemPosition by rememberUpdatedState(itemPosition)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput("${app.packageName}/${app.activityName}") {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick(currentItemPosition + it) }
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
            primaryTextColor = primaryTextColor,
            showShadows = showShadows
        )
    }
}

/**
 * App list item displaying an icon and text label with hand-side alignment and optional update indicator.
 */
@Composable
fun AppListItemWithIcon(
    app: AppInfo,
    handSide: HandSide,
    fontSize: Int = 18,
    iconSize: Int = 40,
    modifier: Modifier = Modifier,
    isRecentlyUpdated: Boolean = false,
    accentColor: AccentColor = AccentColor.SKY,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentItemPosition by rememberUpdatedState(itemPosition)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val painter: Painter = rememberAppIconPainter(app.iconKey, iconSize)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput("${app.packageName}/${app.activityName}") {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick(currentItemPosition + it) }
                )
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
    ) {
        if (handSide == HandSide.LEFT) {
            Image(
                painter = painter,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                AutoResizingText(
                    text = app.label,
                    targetFontSize = fontSize,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f, fill = false),
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows
                )
                if (isRecentlyUpdated) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(contentAlignment = Alignment.Center) {
                        val shadowSettings = LocalShadowSettings.current
                        if (showShadows || shadowSettings.showShadows) {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = "Recently Updated",
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Update,
                            contentDescription = "Recently Updated",
                            tint = accentColor.color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isRecentlyUpdated) {
                    Box(contentAlignment = Alignment.Center) {
                        val shadowSettings = LocalShadowSettings.current
                        if (showShadows || shadowSettings.showShadows) {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = "Recently Updated",
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Update,
                            contentDescription = "Recently Updated",
                            tint = accentColor.color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                AutoResizingText(
                    text = app.label,
                    targetFontSize = fontSize,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false),
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Image(
                painter = painter,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(CircleShape)
            )
        }
    }
}
