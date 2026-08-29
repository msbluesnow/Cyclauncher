package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Calculates remaining days until Google's mandatory developer verification lockdown (Target: Jan 2, 2027 UTC).
 */
fun calculateKeepAndroidOpenDays(): Long {
    val target = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.YEAR, 2027)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 2)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diff = target.timeInMillis - System.currentTimeMillis()
    return if (diff > 0) TimeUnit.MILLISECONDS.toDays(diff) else 0L
}

private val WarningRed = Color(0xFFD32F2F)
private val DarkWarningRed = Color(0xFFB71C1C)

/**
 * Countdown card for the "Keep Android Open" initiative.
 */
@Composable
fun KeepAndroidOpenBanner(
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    popupTheme: PopupTheme,
    showShadows: Boolean,
    onLearnMoreClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val daysRemaining = remember { calculateKeepAndroidOpenDays() }
    val shadowSettings = LocalShadowSettings.current
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)
    val animationsEnabled = LocalAnimationsEnabled.current

    val pulseScale = if (animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        scale
    } else {
        1.0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        popupTheme.solidBackgroundColor,
                        WarningRed.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        WarningRed.copy(alpha = 0.45f),
                        popupTheme.borderColor,
                        accentColor.color.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(WarningRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = WarningRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Keep Android Open",
                            color = primaryTextColor.color,
                            style = TextStyle(
                                shadow = shadow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Your phone is about to stop being yours",
                            color = primaryTextColor.color.copy(alpha = 0.65f),
                            style = TextStyle(shadow = shadow, fontSize = 11.sp, lineHeight = 14.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(WarningRed, DarkWarningRed)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$daysRemaining",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = "DAYS LEFT",
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Starting in 2027, Google will block every Android app whose developer hasn't registered with Google and submitted government ID.",
                color = primaryTextColor.color.copy(alpha = 0.85f),
                style = TextStyle(shadow = shadow, fontSize = 12.sp, lineHeight = 16.sp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLearnMoreClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.color.copy(alpha = 0.20f),
                        contentColor = accentColor.color
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Learn More",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                }

                Button(
                    onClick = onWebsiteClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Petition ↗",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Detailed modal dialog explaining the Keep Android Open movement.
 */
@Composable
fun KeepAndroidOpenDialog(
    popupTheme: PopupTheme,
    accentColor: AccentColor,
    onDismiss: () -> Unit,
    onOpenWebsite: () -> Unit
) {
    val daysRemaining = remember { calculateKeepAndroidOpenDays() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(popupTheme.solidBackgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(WarningRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = null,
                        tint = WarningRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Keep Android Open",
                    color = popupTheme.contentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your phone is about to stop being yours.",
                    color = WarningRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(WarningRed, DarkWarningRed)
                            )
                        )
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$daysRemaining",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 40.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DAYS UNTIL LOCKDOWN",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = popupTheme.backgroundColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, popupTheme.borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "What is happening?",
                            color = popupTheme.contentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Starting in 2027, a silent update pushed by Google will block every Android app whose developer hasn't registered with Google, paid their fee, and handed over government ID.\n\nEvery app and every device, worldwide, with no opt-out.",
                            color = popupTheme.secondaryContentColor,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InitiativeFeatureItem(
                        icon = Icons.Outlined.VisibilityOff,
                        title = "Developer Privacy",
                        desc = "Individual developers and volunteers should not be forced to hand over government IDs to a single corporation.",
                        popupTheme = popupTheme,
                        accentColor = accentColor
                    )
                    InitiativeFeatureItem(
                        icon = Icons.Outlined.InstallMobile,
                        title = "Right to Sideload",
                        desc = "You bought your phone. You should have the freedom to run the software of your choice and use F-Droid freely.",
                        popupTheme = popupTheme,
                        accentColor = accentColor
                    )
                    InitiativeFeatureItem(
                        icon = Icons.Outlined.Code,
                        title = "FOSS & Forks",
                        desc = "Volunteers and forks will be crippled if every customized version requires corporate registration and ID verification.",
                        popupTheme = popupTheme,
                        accentColor = accentColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Free & de-Googled alternatives: LineageOS, GrapheneOS, /e/OS, CalyxOS.",
                    color = popupTheme.secondaryContentColor,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, popupTheme.borderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = popupTheme.contentColor)
                    ) {
                        Text("Close", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onOpenWebsite()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Outlined.Public, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take Action ↗", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InitiativeFeatureItem(
    icon: ImageVector,
    title: String,
    desc: String,
    popupTheme: PopupTheme,
    accentColor: AccentColor
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(popupTheme.backgroundColor)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor.color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = popupTheme.contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )
            Text(
                text = desc,
                color = popupTheme.secondaryContentColor,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}
