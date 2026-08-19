package dev.msbs.cyclauncher.model

/**
 * Represents an item that can be stored and displayed in the launcher's Favorites list.
 * Can be either an individual application or a grouped tag folder.
 */
sealed class FavoriteItem {
    /** The unique persistence key identifying this favorite entry. */
    abstract val key: String

    /** An individual application shortcut in favorites. */
    data class App(val appInfo: AppInfo) : FavoriteItem() {
        override val key: String get() = appInfo.componentKey
    }

    /** A categorized tag folder in favorites containing assigned apps. */
    data class TagFolder(val tag: Tag, val apps: List<AppInfo>) : FavoriteItem() {
        override val key: String get() = "tag:${tag.id}"
    }
}
