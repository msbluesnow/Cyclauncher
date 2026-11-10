package dev.msbs.cyclauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.msbs.cyclauncher.R
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.LocalPopupAlpha
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.ShadowSettings
import kotlin.math.roundToInt

/**
 * Modal dialog allowing real-time adjustment of widget height and width.
 */
@Composable
fun WidgetResizeDialog(
    initialHeightDp: Int,
    initialWidthFraction: Float,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    buttonTextColor: PrimaryTextColor,
    popupTheme: PopupTheme = PopupTheme.DARK,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onDismiss: () -> Unit,
    heightRange: IntRange = 70..420,
    onApply: (heightDp: Int, widthFraction: Float) -> Unit
) {
    var heightDp by remember(initialHeightDp, heightRange) {
        mutableIntStateOf(initialHeightDp.coerceIn(heightRange))
    }
    var widthFraction by remember(initialWidthFraction) { mutableFloatStateOf(initialWidthFraction) }
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)
    val popupAlpha = LocalPopupAlpha.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = popupTheme.solidBackgroundColor(popupAlpha)),
            border = BorderStroke(1.dp, popupTheme.borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.highlight_resize_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.color,
                        style = TextStyle(shadow = shadow)
                    )
                    Text(
                        text = "${heightDp}dp • ${(widthFraction * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = popupTheme.secondaryContentColor,
                        style = TextStyle(shadow = shadow)
                    )
                }

                HorizontalDivider(color = popupTheme.dividerColor)

                // Height Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.highlight_resize_height, heightDp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = popupTheme.contentColor,
                        style = TextStyle(shadow = shadow)
                    )
                    Slider(
                        value = heightDp.coerceIn(heightRange).toFloat(),
                        onValueChange = { heightDp = it.roundToInt().coerceIn(heightRange) },
                        valueRange = heightRange.first.toFloat()..heightRange.last.toFloat(),
                        steps = ((heightRange.last - heightRange.first) / 10 - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor.color,
                            activeTrackColor = accentColor.color,
                            inactiveTrackColor = popupTheme.contentColor.copy(alpha = 0.2f)
                        )
                    )

                    // Quick Height Presets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            stringResource(R.string.highlight_preset_compact) to 80,
                            stringResource(R.string.highlight_preset_medium) to 150,
                            stringResource(R.string.highlight_preset_large) to 220,
                            stringResource(R.string.highlight_preset_tall) to 320
                        )
                        for ((name, valDp) in presets.filter { it.second in heightRange }) {
                            key(valDp) {
                                val isSelected = heightDp == valDp
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) accentColor.color.copy(alpha = 0.25f) else popupTheme.contentColor.copy(alpha = 0.08f))
                                        .border(1.dp, if (isSelected) accentColor.color else popupTheme.contentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) { heightDp = valDp }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TouchMarqueeText(
                                        text = name,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) accentColor.color else popupTheme.contentColor,
                                        style = TextStyle(shadow = shadow),
                                        textAlign = TextAlign.Center,
                                        interactionSource = interactionSource
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = popupTheme.dividerColor)

                // Width Options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.highlight_resize_width),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = popupTheme.contentColor,
                        style = TextStyle(shadow = shadow)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val widthOptions = listOf(
                            stringResource(R.string.highlight_width_full) to 1.0f,
                            stringResource(R.string.highlight_width_75) to 0.75f,
                            stringResource(R.string.highlight_width_half) to 0.5f
                        )
                        for ((wLabel, wFraction) in widthOptions) {
                            key(wFraction) {
                                val isSelected = kotlin.math.abs(widthFraction - wFraction) < 0.05f
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) accentColor.color.copy(alpha = 0.25f) else popupTheme.contentColor.copy(alpha = 0.08f))
                                        .border(1.dp, if (isSelected) accentColor.color else popupTheme.contentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) { widthFraction = wFraction }
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TouchMarqueeText(
                                        text = wLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) accentColor.color else popupTheme.contentColor,
                                        style = TextStyle(shadow = shadow),
                                        textAlign = TextAlign.Center,
                                        interactionSource = interactionSource
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = onDismiss,
                        interactionSource = cancelInteractionSource
                    ) {
                        TouchMarqueeText(
                            text = stringResource(R.string.common_cancel),
                            color = popupTheme.secondaryContentColor,
                            fontWeight = FontWeight.Medium,
                            interactionSource = cancelInteractionSource
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val applyInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            onApply(heightDp, widthFraction)
                            onDismiss()
                        },
                        interactionSource = applyInteractionSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor.color,
                            contentColor = buttonTextColor.color
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        TouchMarqueeText(
                            text = stringResource(R.string.common_apply),
                            color = buttonTextColor.color,
                            fontWeight = FontWeight.Bold,
                            interactionSource = applyInteractionSource
                        )
                    }
                }
            }
        }
    }
}

