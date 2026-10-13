package dev.msbs.cyclauncher.model

import androidx.compose.runtime.Immutable

/**
 * Represents an item that can be stored and displayed in the launcher's Favorites list.
 * Can be either an individual application or a grouped tag folder.
 */
@Immutable
sealed class FavoriteItem {
    /** The unique persistence key identifying this favorite entry. */
    abstract val key: String

    /** An individual application shortcut in favorites. */
    @Immutable
    data class App(val appInfo: AppInfo) : FavoriteItem() {
        override val key: String = appInfo.componentKey
    }

    /** A categorized tag folder in favorites containing assigned apps. */
    @Immutable
    data class TagFolder(val tag: Tag, val apps: List<AppInfo>) : FavoriteItem() {
        override val key: String = "tag:${tag.id}"
    }
}
