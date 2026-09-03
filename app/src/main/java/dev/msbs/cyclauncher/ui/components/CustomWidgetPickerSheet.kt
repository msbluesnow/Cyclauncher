package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.ShadowSettings

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

data class AppWidgetGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val widgets: List<AppWidgetProviderInfo>
)

private fun Drawable.toSafeBitmap(): Bitmap? {
    return try {
        if (this is BitmapDrawable && this.bitmap != null) {
            this.bitmap
        } else {
            val width = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(600) else 100
            val height = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(600) else 100
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        }
    } catch (_: Exception) {
        null
    }
}

private fun AppWidgetProviderInfo.safeLabel(pm: PackageManager): String {
    return try {
        loadLabel(pm)?.takeIf { it.isNotBlank() }
            ?: label?.takeIf { it.isNotBlank() }
            ?: provider.shortClassName.substringAfterLast('.')
    } catch (_: Throwable) {
        try {
            label?.takeIf { it.isNotBlank() } ?: provider.shortClassName.substringAfterLast('.')
        } catch (_: Throwable) {
            "Widget"
        }
    }
}

/**
 * Custom in-app widget picker modal styled with Cyclauncher design language.
 * - Search bar to filter installed applications by name
 * - Grouping of all available widgets by application with app icons
 * - Expandable accordion list displaying graphic previews and cell dimensions (e.g. 4x2)
 */
@Composable
fun CustomWidgetPickerSheet(
    apps: List<AppInfo> = emptyList(),
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onDismiss: () -> Unit,
    onSelectWidget: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val shadowSettings = LocalShadowSettings.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    var searchQuery by remember { mutableStateOf("") }

    // Load and group all installed widget providers by app with package-level resolution
    val allGroups = remember(apps) {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
        val myUserHandle = android.os.Process.myUserHandle()
        val profiles = userManager?.userProfiles?.ifEmpty { listOf(myUserHandle) } ?: listOf(myUserHandle)

        val providers = mutableListOf<AppWidgetProviderInfo>()

        // 1. Initial providers already in AppWidgetManager memory cache across all user profiles
        for (profile in profiles) {
            try {
                manager.getInstalledProvidersForProfile(profile)?.let { providers.addAll(it) }
            } catch (_: Exception) {}
        }
        if (providers.isEmpty()) {
            try {
                manager.installedProviders?.let { providers.addAll(it) }
            } catch (_: Exception) {}
        }

        // 2. Discover via package-level query for each known installed app.
        // On modern Android (especially post-reboot), AppWidgetService lazily indexes providers.
        // Calling getInstalledProvidersForPackage(pkg, profile) forces AppWidgetService
        // to load providers for that app into the system cache and return them!
        val packageNames = apps.map { it.packageName }.toSet()
        val alreadyLoaded = providers.map { it.provider.packageName }.toSet()
        val pendingPackages = packageNames - alreadyLoaded

        for (pkg in pendingPackages) {
            for (profile in profiles) {
                try {
                    val pkgProviders = manager.getInstalledProvidersForPackage(pkg, profile)
                    if (!pkgProviders.isNullOrEmpty()) {
                        providers.addAll(pkgProviders)
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Complete discovery: query PackageManager for all broadcast receivers declaring ACTION_APPWIDGET_UPDATE
        val knownComponents = providers.map { it.provider.flattenToString() }.toMutableSet()
        try {
            val widgetIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            val flags = PackageManager.GET_META_DATA or
                PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE
            val receivers = pm.queryBroadcastReceivers(widgetIntent, flags)
            for (ri in receivers) {
                val cn = android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                val cnKey = cn.flattenToString()
                if (cnKey !in knownComponents) {
                    knownComponents.add(cnKey)
                    val info = AppWidgetProviderInfo().apply {
                        provider = cn
                        label = try {
                            ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: ri.activityInfo.name
                        } catch (_: Exception) {
                            ri.activityInfo.name
                        }
                        icon = ri.activityInfo.icon
                        minWidth = 180
                        minHeight = 110
                    }
                    try {
                        val field = AppWidgetProviderInfo::class.java.getDeclaredField("providerInfo")
                        field.isAccessible = true
                        field.set(info, ri.activityInfo)
                    } catch (_: Throwable) {}
                    try {
                        ri.activityInfo.loadXmlMetaData(pm, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)?.use { parser ->
                            val res = pm.getResourcesForApplication(ri.activityInfo.applicationInfo)
                            var eventType = parser.eventType
                            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                                    for (i in 0 until parser.attributeCount) {
                                        when (parser.getAttributeName(i)) {
                                            "minWidth" -> {
                                                val resId = parser.getAttributeResourceValue(i, 0)
                                                info.minWidth = if (resId != 0) res.getDimensionPixelSize(resId) else parser.getAttributeIntValue(i, 180)
                                            }
                                            "minHeight" -> {
                                                val resId = parser.getAttributeResourceValue(i, 0)
                                                info.minHeight = if (resId != 0) res.getDimensionPixelSize(resId) else parser.getAttributeIntValue(i, 110)
                                            }
                                            "configure" -> {
                                                val confName = parser.getAttributeValue(i)
                                                if (!confName.isNullOrBlank()) {
                                                    info.configure = if (confName.startsWith(".")) {
                                                        android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.packageName + confName)
                                                    } else if (!confName.contains(".")) {
                                                        android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.packageName + "." + confName)
                                                    } else {
                                                        android.content.ComponentName(ri.activityInfo.packageName, confName)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break
                                }
                                eventType = parser.next()
                            }
                        }
                    } catch (_: Exception) {}
                    providers.add(info)
                }
            }
        } catch (_: Exception) {}

        // 4. Group by package and create AppWidgetGroup
        val appsByPackage = apps.associateBy { it.packageName }
        providers
            .distinctBy { it.provider.flattenToString() }
            .groupBy { it.provider.packageName }
            .map { (packageName, widgetList) ->
                val knownApp = appsByPackage[packageName]
                val firstWidget = widgetList.firstOrNull()

                val appLabel = knownApp?.label ?: try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    firstWidget?.safeLabel(pm)?.takeIf { it.isNotBlank() } ?: packageName
                }

                val appIcon = try {
                    pm.getApplicationIcon(packageName)
                } catch (_: Exception) {
                    try {
                        firstWidget?.loadIcon(context, 0)
                    } catch (_: Exception) {
                        null
                    }
                }

                AppWidgetGroup(
                    packageName = packageName,
                    appLabel = appLabel,
                    appIcon = appIcon,
                    widgets = widgetList.sortedBy { it.safeLabel(pm) }
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    val filteredGroups = remember(searchQuery, allGroups) {
        if (searchQuery.isBlank()) {
            allGroups
        } else {
            val query = searchQuery.trim().lowercase()
            allGroups.filter { group ->
                group.appLabel.lowercase().contains(query) ||
                    group.widgets.any { it.safeLabel(context.packageManager).lowercase().contains(query) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        @Suppress("DEPRECATION")
        DisposableEffect(view) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false)
                }
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.72f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.97f)
                ),
                border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PickerShadowedIcon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = accentColor.color,
                                modifier = Modifier.size(20.dp),
                                showShadows = showShadows,
                                primaryTextColor = primaryTextColor,
                                shadowSettings = shadowSettings
                            )
                            Text(
                                text = "WIDGETS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = accentColor.color,
                                style = TextStyle(shadow = shadow)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            PickerShadowedIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close picker",
                                tint = primaryTextColor.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                                showShadows = showShadows,
                                primaryTextColor = primaryTextColor,
                                shadowSettings = shadowSettings
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryTextColor.color.copy(alpha = 0.08f))
                            .border(1.dp, primaryTextColor.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PickerShadowedIcon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = primaryTextColor.color.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                            showShadows = showShadows,
                            primaryTextColor = primaryTextColor,
                            shadowSettings = shadowSettings
                        )

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = primaryTextColor.color,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(accentColor.color),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search apps or widgets...",
                                        color = primaryTextColor.color.copy(alpha = 0.45f),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = primaryTextColor.color.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Widgets List
                    if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isEmpty()) "No widgets found" else "No matching apps found",
                                color = primaryTextColor.color.copy(alpha = 0.6f),
                                fontSize = 13.5.sp,
                                style = TextStyle(shadow = shadow)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredGroups, key = { it.packageName }) { group ->
                                AppWidgetAccordionCard(
                                    group = group,
                                    initiallyExpanded = filteredGroups.size <= 3 || searchQuery.isNotBlank(),
                                    accentColor = accentColor,
                                    primaryTextColor = primaryTextColor,
                                    showShadows = showShadows,
                                    shadowSettings = shadowSettings,
                                    animationsEnabled = animationsEnabled,
                                    onSelectWidget = onSelectWidget
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Accordion card representing an application with its list of available widgets.
 */
@Composable
private fun AppWidgetAccordionCard(
    group: AppWidgetGroup,
    initiallyExpanded: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    animationsEnabled: Boolean,
    onSelectWidget: (AppWidgetProviderInfo) -> Unit
) {
    var isExpanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    val appIconBitmap = remember(group.appIcon) {
        group.appIcon?.toSafeBitmap()?.asImageBitmap()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = if (showShadows) 0.20f else 0.10f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // App Header Row (Clickable to toggle expansion)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accentColor.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = accentColor.color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = group.appLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = shadow),
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Widget Count Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.color.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${group.widgets.size}",
                            color = accentColor.color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = shadow)
                        )
                    }
                }

                PickerShadowedIcon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = primaryTextColor.color.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                    showShadows = showShadows,
                    primaryTextColor = primaryTextColor,
                    shadowSettings = shadowSettings
                )
            }

            // Expanded Widget Previews
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (animationsEnabled) expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(150)) else EnterTransition.None,
                exit = if (animationsEnabled) shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(120)) else ExitTransition.None
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (widgetInfo in group.widgets) {
                        SingleWidgetPreviewCard(
                            widgetInfo = widgetInfo,
                            appIcon = group.appIcon,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows,
                            shadowSettings = shadowSettings,
                            onClick = { onSelectWidget(widgetInfo) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single widget preview card displaying thumbnail, label, and grid cell dimensions (e.g., 4x2).
 */
@Composable
private fun SingleWidgetPreviewCard(
    widgetInfo: AppWidgetProviderInfo,
    appIcon: Drawable?,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = LocalDensity.current
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    val label = remember(widgetInfo) {
        widgetInfo.safeLabel(pm)
    }

    val previewBitmap = remember(widgetInfo) {
        try {
            val drawable = widgetInfo.loadPreviewImage(context, 0)
                ?: widgetInfo.loadIcon(context, 0)
                ?: appIcon
            drawable?.toSafeBitmap()?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    // Calculate grid dimensions (e.g. 4x2 or 2x1)
    val spanLabel = remember(widgetInfo, density) {
        val spanX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && widgetInfo.targetCellWidth > 0) {
            widgetInfo.targetCellWidth
        } else {
            maxOf(1, ((widgetInfo.minWidth / density.density + 15) / 70).roundToInt())
        }

        val spanY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && widgetInfo.targetCellHeight > 0) {
            widgetInfo.targetCellHeight
        } else {
            maxOf(1, ((widgetInfo.minHeight / density.density + 15) / 70).roundToInt())
        }
        "$spanX × $spanY"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.04f)),
        border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Title & Dimensions Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(primaryTextColor.color.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = spanLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor.color.copy(alpha = 0.8f),
                        style = TextStyle(shadow = shadow)
                    )
                }
            }

            // Preview Image or Icon Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryTextColor.color.copy(alpha = 0.03f))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Widgets,
                        contentDescription = null,
                        tint = accentColor.color.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerShadowedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    showShadows: Boolean,
    primaryTextColor: PrimaryTextColor,
    shadowSettings: ShadowSettings
) {
    Box(contentAlignment = Alignment.Center) {
        if (showShadows) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                modifier = modifier.offset(1.dp, 1.dp)
            )
        }
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}
