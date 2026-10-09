package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.theme.ShadowSettings
import dev.msbs.cyclauncher.ui.theme.LocalIconPackVersion
import dev.msbs.cyclauncher.ui.theme.LocalMarqueeEnabled

import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Single-line text component with marquee animation that automatically restarts upon touch begin (ACTION_DOWN).
 * When text fits within its available width, it renders statically without animation.
 * When touched (even before a tap or click is completed), the marquee animation immediately starts/restarts.
 * Can also observe an optional parent [interactionSource] (e.g. from a Button or Card) so touching anywhere
 * on the parent element triggers the marquee.
 */
@Composable
fun TouchMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    iterations: Int = 3,
    initialDelayMillis: Int = 1200,
    repeatDelayMillis: Int = 1200,
    velocity: Dp = 35.dp,
    spacing: MarqueeSpacing = MarqueeSpacing(24.dp),
    interactionSource: InteractionSource? = null,
    enabled: Boolean = LocalMarqueeEnabled.current
) {
    if (!enabled) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = modifier
        )
        return
    }

    var restartTrigger by remember { mutableIntStateOf(0) }

    interactionSource?.let { source ->
        LaunchedEffect(source) {
            source.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    restartTrigger++
                }
            }
        }
    }

    val touchModifier = if (interactionSource == null) {
        Modifier.pointerInput(restartTrigger) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDown() }) {
                        restartTrigger++
                    }
                }
            }
        }
    } else Modifier

    key(restartTrigger) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = modifier
                .basicMarquee(
                    iterations = iterations,
                    animationMode = MarqueeAnimationMode.Immediately,
                    repeatDelayMillis = repeatDelayMillis,
                    initialDelayMillis = if (restartTrigger == 0) initialDelayMillis else 0,
                    spacing = spacing,
                    velocity = velocity
                )
                .then(touchModifier)
        )
    }
}

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
 * Title text component that adaptively shrinks its font size to prevent overlapping
 * with header action icons (e.g. back buttons) and ensure the entire title fits on a single line.
 */
@Composable
fun AdaptiveHeaderTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 14.sp,
    maxFontSize: TextUnit = if (style.fontSize != TextUnit.Unspecified && style.fontSize.value > 0f) style.fontSize else 28.sp,
    textAlign: TextAlign = TextAlign.Center
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxPx = constraints.maxWidth.toFloat()

        val optimalFontSize = remember(text, style, maxPx, minFontSize, maxFontSize) {
            if (maxPx <= 0f || text.isEmpty()) {
                maxFontSize
            } else {
                val minVal = minFontSize.value
                val maxVal = maxFontSize.value
                // First check if maxFontSize fits comfortably
                val maxMeasure = textMeasurer.measure(
                    text = text,
                    style = style.copy(fontSize = maxFontSize),
                    maxLines = 1,
                    softWrap = false
                )
                if (maxMeasure.size.width <= maxPx) {
                    maxFontSize
                } else {
                    // Binary search for the largest font size that fits maxPx
                    var low = minVal
                    var high = maxVal
                    var best = minVal
                    for (i in 0 until 8) {
                        val mid = (low + high) / 2f
                        val measured = textMeasurer.measure(
                            text = text,
                            style = style.copy(fontSize = mid.sp),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (measured.size.width <= maxPx) {
                            best = mid
                            low = mid
                        } else {
                            high = mid
                        }
                    }
                    best.sp
                }
            }
        }

        TouchMarqueeText(
            text = text,
            color = color,
            style = style.copy(fontSize = optimalFontSize),
            textAlign = textAlign
        )
    }
}

/**
 * Universal top bar for Cyclauncher screens with adaptive hand-side back arrow
 * and auto-resizing, non-overlapping centered title text.
 */
@Composable
fun ScreenTopBar(
    title: String,
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowColorOverride: PrimaryTextColor? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shadow = primaryTextColor.getShadow(showShadows, shadowColorOverride)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val backIcon = when (handSide) {
            HandSide.RIGHT -> Icons.AutoMirrored.Outlined.ArrowForward
            HandSide.LEFT -> Icons.AutoMirrored.Outlined.ArrowBack
        }
        val buttonAlignment = if (handSide == HandSide.RIGHT) Alignment.CenterEnd else Alignment.CenterStart

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(buttonAlignment)
        ) {
            Box {
                if (showShadows) {
                    val shadowOffset = 1.dp
                    Icon(
                        imageVector = backIcon,
                        contentDescription = null,
                        tint = primaryTextColor.getShadowColor(shadowColorOverride),
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = shadowOffset, y = shadowOffset)
                    )
                }
                Icon(
                    imageVector = backIcon,
                    contentDescription = "Back",
                    tint = accentColor.color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        AdaptiveHeaderTitle(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                shadow = shadow
            ),
            color = accentColor.color,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 52.dp)
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
        },
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
    )
}

/**
 * Universal icon with optional drop shadow offset, taking settings from [LocalShadowSettings] by default.
 */
@Composable
fun ShadowedIcon(
    imageVector: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: androidx.compose.ui.unit.Dp? = null,
    showShadows: Boolean = LocalShadowSettings.current.showShadows,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    shadowSettings: ShadowSettings = LocalShadowSettings.current,
    shadowColorOverride: PrimaryTextColor? = shadowSettings.shadowColorOverride,
    shadowAlpha: Float = 0.25f,
    offset: androidx.compose.ui.unit.Dp = 1.dp
) {
    val boxModifier = if (size != null) modifier.size(size) else modifier
    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        if (showShadows) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = shadowAlpha),
                modifier = Modifier
                    .matchParentSize()
                    .offset(offset, offset)
            )
        }
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = if (size != null) Modifier.fillMaxSize() else Modifier
        )
    }
}

private class ItemPositionRef {
    var position: Offset = Offset.Zero
}

/**
 * Attaches root position tracking and tap/long-press gestures to an app item composable.
 */
fun Modifier.appItemGestures(
    key: Any?,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {}
): Modifier = composed {
    val positionRef = remember { ItemPositionRef() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    this
        .onGloballyPositioned { positionRef.position = it.positionInRoot() }
        .pointerInput(key) {
            detectTapGestures(
                onTap = { currentOnClick() },
                onLongPress = { currentOnLongClick(positionRef.position + it) }
            )
        }
}

/**
 * Circular app icon with click and long-press gestures.
 */
@Composable
fun AppIconItem(
    app: AppInfo,
    size: Int = 48,
    colorFilter: ColorFilter? = null,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {}
) {
    val painter: Painter = rememberAppIconPainter(app.iconKey, size)

    Image(
        painter = painter,
        contentDescription = app.label,
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .appItemGestures(app.componentKey, onClick, onLongClick)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .appItemGestures(app.componentKey, onClick, onLongClick)
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
    colorFilter: ColorFilter? = null,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false
) {
    val painter: Painter = rememberAppIconPainter(app.iconKey, iconSize)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .appItemGestures(app.componentKey, onClick, onLongClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
    ) {
        if (handSide == HandSide.LEFT) {
            Image(
                painter = painter,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
        ) {
            if (handSide == HandSide.RIGHT && isRecentlyUpdated) {
                RecentlyUpdatedBadge(accentColor, primaryTextColor, showShadows)
                Spacer(modifier = Modifier.width(6.dp))
            }
            AutoResizingText(
                text = app.label,
                targetFontSize = fontSize,
                textAlign = if (handSide == HandSide.LEFT) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
                primaryTextColor = primaryTextColor,
                showShadows = showShadows
            )
            if (handSide == HandSide.LEFT && isRecentlyUpdated) {
                Spacer(modifier = Modifier.width(6.dp))
                RecentlyUpdatedBadge(accentColor, primaryTextColor, showShadows)
            }
        }

        if (handSide == HandSide.RIGHT) {
            Spacer(modifier = Modifier.width(10.dp))
            Image(
                painter = painter,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun RecentlyUpdatedBadge(
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean
) {
    val shadowSettings = LocalShadowSettings.current
    ShadowedIcon(
        imageVector = Icons.Outlined.Update,
        contentDescription = "Recently Updated",
        tint = accentColor.color,
        modifier = Modifier.size(16.dp),
        showShadows = showShadows || shadowSettings.showShadows,
        primaryTextColor = primaryTextColor,
        shadowSettings = shadowSettings
    )
}
