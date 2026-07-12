package dev.msbs.cyclauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

@Composable
fun AppActionMenu(
    app: AppInfo,
    isFavorite: Boolean,
    showRemoveFromHistory: Boolean = false,
    offset: Offset,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveFromHistory: () -> Unit = {},
    accentColor: AccentColor = AccentColor.CYAN
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val menuWidth = 240.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }
    
    // Estimated menu height
    val menuHeightPx = with(density) { (if (showRemoveFromHistory) 220.dp else 170.dp).toPx() }

    // Logic to keep menu on screen
    var x = offset.x
    var y = offset.y

    if (x + menuWidthPx > screenWidthPx) x -= menuWidthPx
    if (y + menuHeightPx > screenHeightPx) y -= menuHeightPx

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.DarkGray.copy(alpha = 0.98f))
                .padding(vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = app.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor.color
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                MenuItem(
                    text = if (isFavorite) "★ Remove from Favorites" else "☆ Add to Favorites",
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    }
                )

                if (showRemoveFromHistory) {
                    MenuItem(
                        text = "🕒 Remove from History",
                        onClick = {
                            onRemoveFromHistory()
                            onDismiss()
                        }
                    )
                }

                MenuItem(
                    text = "🗑 Uninstall App",
                    onClick = {
                        onUninstall()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge
    )
}
