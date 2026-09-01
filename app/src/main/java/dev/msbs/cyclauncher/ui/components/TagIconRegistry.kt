package dev.msbs.cyclauncher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Descriptor for a lightweight Material vector icon available for tagging folders.
 */
data class TagVectorIcon(
    val key: String,
    val name: String,
    val icon: ImageVector,
    val category: String = "General"
)

/**
 * Category wrapper for grouping vector icons in the expanded picker dialog.
 */
data class TagIconCategory(
    val title: String,
    val icons: List<TagVectorIcon>
)

/**
 * Comprehensive registry of curated Material vector icons for folders with 0 overhead and dynamic tag tinting.
 */
object TagIconRegistry {
    val PRIMARY_ICONS: List<TagVectorIcon> = listOf(
        TagVectorIcon("folder", "Folder", Icons.Outlined.Folder),
        TagVectorIcon("games", "Games", Icons.Outlined.SportsEsports),
        TagVectorIcon("work", "Work", Icons.Outlined.WorkOutline),
        TagVectorIcon("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
        TagVectorIcon("music", "Music", Icons.Outlined.Headphones),
        TagVectorIcon("camera", "Camera", Icons.Outlined.PhotoCamera),
        TagVectorIcon("video", "Video", Icons.Outlined.Videocam),
        TagVectorIcon("web", "Web", Icons.Outlined.Language),
        TagVectorIcon("shop", "Shop", Icons.Outlined.ShoppingCart),
        TagVectorIcon("money", "Finance", Icons.Outlined.Payments),
        TagVectorIcon("book", "Books", Icons.AutoMirrored.Outlined.MenuBook),
        TagVectorIcon("tools", "Tools", Icons.Outlined.Build),
        TagVectorIcon("settings", "Settings", Icons.Outlined.Settings),
        TagVectorIcon("star", "Star", Icons.Outlined.StarOutline),
        TagVectorIcon("heart", "Favorite", Icons.Outlined.FavoriteBorder),
        TagVectorIcon("bolt", "Fast", Icons.Outlined.Bolt),
        TagVectorIcon("home", "Home", Icons.Outlined.Home),
        TagVectorIcon("lock", "Lock", Icons.Outlined.Lock),
        TagVectorIcon("palette", "Art", Icons.Outlined.Palette),
        TagVectorIcon("coffee", "Coffee", Icons.Outlined.LocalCafe),
        TagVectorIcon("fitness", "Fitness", Icons.Outlined.FitnessCenter),
        TagVectorIcon("cloud", "Cloud", Icons.Outlined.CloudQueue),
        TagVectorIcon("terminal", "Dev", Icons.Outlined.Terminal),
        TagVectorIcon("phone", "Phone", Icons.Outlined.Phone),
        TagVectorIcon("mail", "Email", Icons.Outlined.MailOutline),
        TagVectorIcon("idea", "Idea", Icons.Outlined.Lightbulb),
        TagVectorIcon("map", "Map", Icons.Outlined.Map),
        TagVectorIcon("alarm", "Alarm", Icons.Outlined.Alarm)
    )

    val CATEGORIES: List<TagIconCategory> = listOf(
        TagIconCategory(
            title = "General & System",
            icons = listOf(
                TagVectorIcon("folder", "Folder", Icons.Outlined.Folder),
                TagVectorIcon("settings", "Settings", Icons.Outlined.Settings),
                TagVectorIcon("star", "Star", Icons.Outlined.StarOutline),
                TagVectorIcon("heart", "Favorite", Icons.Outlined.FavoriteBorder),
                TagVectorIcon("bolt", "Fast", Icons.Outlined.Bolt),
                TagVectorIcon("home", "Home", Icons.Outlined.Home),
                TagVectorIcon("lock", "Lock", Icons.Outlined.Lock),
                TagVectorIcon("alarm", "Alarm", Icons.Outlined.Alarm),
                TagVectorIcon("search", "Search", Icons.Outlined.Search),
                TagVectorIcon("shield", "Security", Icons.Outlined.Shield),
                TagVectorIcon("wifi", "Wi-Fi", Icons.Outlined.Wifi),
                TagVectorIcon("bluetooth", "Bluetooth", Icons.Outlined.Bluetooth),
                TagVectorIcon("battery", "Battery", Icons.Outlined.BatteryStd),
                TagVectorIcon("notifications", "Notifications", Icons.Outlined.Notifications),
                TagVectorIcon("schedule", "Schedule", Icons.Outlined.Schedule),
                TagVectorIcon("bookmark", "Bookmark", Icons.Outlined.BookmarkBorder),
                TagVectorIcon("check", "Done", Icons.Outlined.Check),
                TagVectorIcon("power", "Power", Icons.Outlined.PowerSettingsNew),
                TagVectorIcon("dark_mode", "Night", Icons.Outlined.DarkMode),
                TagVectorIcon("brightness", "Light", Icons.Outlined.Brightness6)
            )
        ),
        TagIconCategory(
            title = "Communication & Social",
            icons = listOf(
                TagVectorIcon("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
                TagVectorIcon("chat_alt", "Messages", Icons.AutoMirrored.Outlined.Chat),
                TagVectorIcon("phone", "Phone", Icons.Outlined.Phone),
                TagVectorIcon("mail", "Email", Icons.Outlined.MailOutline),
                TagVectorIcon("forum", "Forum", Icons.Outlined.Forum),
                TagVectorIcon("groups", "Community", Icons.Outlined.Groups),
                TagVectorIcon("send", "Direct", Icons.AutoMirrored.Outlined.Send),
                TagVectorIcon("alternate_email", "Mention", Icons.Outlined.AlternateEmail),
                TagVectorIcon("share", "Share", Icons.Outlined.Share),
                TagVectorIcon("thumb_up", "Like", Icons.Outlined.ThumbUp),
                TagVectorIcon("sentiment_satisfied", "Social", Icons.Outlined.SentimentSatisfied),
                TagVectorIcon("rss_feed", "Feed", Icons.Outlined.RssFeed)
            )
        ),
        TagIconCategory(
            title = "Media & Audio",
            icons = listOf(
                TagVectorIcon("music", "Music", Icons.Outlined.Headphones),
                TagVectorIcon("camera", "Camera", Icons.Outlined.PhotoCamera),
                TagVectorIcon("video", "Video", Icons.Outlined.Videocam),
                TagVectorIcon("movie", "Movies", Icons.Outlined.Movie),
                TagVectorIcon("image", "Photos", Icons.Outlined.Image),
                TagVectorIcon("palette", "Art & Design", Icons.Outlined.Palette),
                TagVectorIcon("mic", "Podcasts / Mic", Icons.Outlined.Mic),
                TagVectorIcon("radio", "Radio", Icons.Outlined.Radio),
                TagVectorIcon("audiotrack", "Track", Icons.Outlined.Audiotrack),
                TagVectorIcon("album", "Album", Icons.Outlined.Album),
                TagVectorIcon("volume", "Volume", Icons.AutoMirrored.Outlined.VolumeUp),
                TagVectorIcon("queue_music", "Playlist", Icons.AutoMirrored.Outlined.QueueMusic),
                TagVectorIcon("equalizer", "Equalizer", Icons.Outlined.Equalizer)
            )
        ),
        TagIconCategory(
            title = "Gaming & Entertainment",
            icons = listOf(
                TagVectorIcon("games", "Games", Icons.Outlined.SportsEsports),
                TagVectorIcon("sports_soccer", "Football", Icons.Outlined.SportsSoccer),
                TagVectorIcon("sports_basketball", "Basketball", Icons.Outlined.SportsBasketball),
                TagVectorIcon("sports_tennis", "Tennis", Icons.Outlined.SportsTennis),
                TagVectorIcon("casino", "Cards / Board", Icons.Outlined.Casino),
                TagVectorIcon("extension", "Puzzles", Icons.Outlined.Extension),
                TagVectorIcon("videogame_asset", "Console", Icons.Outlined.VideogameAsset),
                TagVectorIcon("toys", "Fun / Toys", Icons.Outlined.Toys)
            )
        ),
        TagIconCategory(
            title = "Productivity & Office",
            icons = listOf(
                TagVectorIcon("work", "Work", Icons.Outlined.WorkOutline),
                TagVectorIcon("book", "Books / Education", Icons.AutoMirrored.Outlined.MenuBook),
                TagVectorIcon("note", "Notes", Icons.AutoMirrored.Outlined.Note),
                TagVectorIcon("description", "Documents", Icons.Outlined.Description),
                TagVectorIcon("receipt", "Invoices / Bills", Icons.AutoMirrored.Outlined.ReceiptLong),
                TagVectorIcon("calendar", "Calendar", Icons.Outlined.CalendarMonth),
                TagVectorIcon("calculate", "Calculator", Icons.Outlined.Calculate),
                TagVectorIcon("draw", "Draw / Notes", Icons.Outlined.Draw),
                TagVectorIcon("task_alt", "Tasks", Icons.Outlined.TaskAlt),
                TagVectorIcon("timeline", "Timeline", Icons.Outlined.Timeline),
                TagVectorIcon("assessment", "Reports", Icons.Outlined.Assessment),
                TagVectorIcon("analytics", "Analytics", Icons.Outlined.Analytics),
                TagVectorIcon("folder_special", "Special Folder", Icons.Outlined.FolderSpecial)
            )
        ),
        TagIconCategory(
            title = "Shopping & Finance",
            icons = listOf(
                TagVectorIcon("shop", "Shopping", Icons.Outlined.ShoppingCart),
                TagVectorIcon("money", "Finance", Icons.Outlined.Payments),
                TagVectorIcon("credit_card", "Banking", Icons.Outlined.CreditCard),
                TagVectorIcon("account_balance", "Bank", Icons.Outlined.AccountBalance),
                TagVectorIcon("savings", "Savings", Icons.Outlined.Savings),
                TagVectorIcon("attach_money", "Cash", Icons.Outlined.AttachMoney),
                TagVectorIcon("qr_code", "QR Pay", Icons.Outlined.QrCode),
                TagVectorIcon("store", "Market", Icons.Outlined.Store),
                TagVectorIcon("sell", "Deals", Icons.Outlined.Sell),
                TagVectorIcon("shopping_bag", "Bags", Icons.Outlined.ShoppingBag),
                TagVectorIcon("inventory", "Inventory", Icons.Outlined.Inventory2)
            )
        ),
        TagIconCategory(
            title = "Lifestyle, Food & Travel",
            icons = listOf(
                TagVectorIcon("coffee", "Coffee", Icons.Outlined.LocalCafe),
                TagVectorIcon("fitness", "Fitness", Icons.Outlined.FitnessCenter),
                TagVectorIcon("restaurant", "Food", Icons.Outlined.Restaurant),
                TagVectorIcon("fastfood", "Fast Food", Icons.Outlined.Fastfood),
                TagVectorIcon("map", "Maps", Icons.Outlined.Map),
                TagVectorIcon("navigation", "Navigation", Icons.Outlined.Navigation),
                TagVectorIcon("directions_car", "Auto / Drive", Icons.Outlined.DirectionsCar),
                TagVectorIcon("flight", "Travel / Flights", Icons.Outlined.Flight),
                TagVectorIcon("explore", "Explore", Icons.Outlined.Explore),
                TagVectorIcon("wb_sunny", "Weather", Icons.Outlined.WbSunny),
                TagVectorIcon("bedtime", "Sleep", Icons.Outlined.Bedtime),
                TagVectorIcon("eco", "Nature", Icons.Outlined.Eco),
                TagVectorIcon("local_hospital", "Health", Icons.Outlined.LocalHospital)
            )
        ),
        TagIconCategory(
            title = "Dev, Tech & Web",
            icons = listOf(
                TagVectorIcon("terminal", "Terminal / Dev", Icons.Outlined.Terminal),
                TagVectorIcon("cloud", "Cloud", Icons.Outlined.CloudQueue),
                TagVectorIcon("web", "Web / Browser", Icons.Outlined.Language),
                TagVectorIcon("code", "Coding", Icons.Outlined.Code),
                TagVectorIcon("storage", "Storage", Icons.Outlined.Storage),
                TagVectorIcon("memory", "Hardware", Icons.Outlined.Memory),
                TagVectorIcon("developer_mode", "Developer", Icons.Outlined.DeveloperMode),
                TagVectorIcon("bug_report", "Bugs / Testing", Icons.Outlined.BugReport),
                TagVectorIcon("security", "Security", Icons.Outlined.Security),
                TagVectorIcon("data_object", "Data / JSON", Icons.Outlined.DataObject),
                TagVectorIcon("idea", "Idea", Icons.Outlined.Lightbulb),
                TagVectorIcon("tools", "Tools", Icons.Outlined.Build)
            )
        )
    )

    val ALL_ICONS: List<TagVectorIcon> = (PRIMARY_ICONS + CATEGORIES.flatMap { it.icons }).distinctBy { it.key }

    val ICONS: List<TagVectorIcon> = PRIMARY_ICONS

    private val ICON_MAP: Map<String, ImageVector> = ALL_ICONS.associate { it.key to it.icon }

    /**
     * Resolves an [ImageVector] for a given icon key (e.g. "icon:games" or "games").
     */
    fun getVectorIcon(key: String?): ImageVector? {
        if (key == null) return null
        val cleanKey = key.removePrefix("icon:").lowercase().trim()
        return ICON_MAP[cleanKey]
    }

    /**
     * Returns true if the string represents a vector icon key.
     */
    fun isVectorIcon(value: String?): Boolean {
        if (value == null) return false
        return value.startsWith("icon:") || ICON_MAP.containsKey(value.lowercase().trim())
    }

    /**
     * Formats a raw icon key into standard storage format "icon:<key>".
     */
    fun formatKey(key: String): String {
        return if (key.startsWith("icon:")) key else "icon:$key"
    }
}

/**
 * Utility for strictly extracting and validating a single Emoji or Unicode symbol.
 */
object EmojiUtils {
    /**
     * Extracts strictly ONE emoji / unicode symbol from the given input.
     * Returns null if the input contains no emojis (e.g. only normal letters/digits/whitespace).
     * Supports complex emojis with ZWJ (Zero-Width Joiners), Skin Tone Modifiers, Keycaps, and Flags.
     */
    fun extractSingleEmoji(input: String): String? {
        if (input.isBlank()) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        var i = 0
        while (i < trimmed.length) {
            val codePoint = trimmed.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            if (isEmojiOrSymbol(codePoint)) {
                var end = i + charCount
                while (end < trimmed.length) {
                    val nextCp = trimmed.codePointAt(end)
                    val nextCharCount = Character.charCount(nextCp)

                    // Variation Selectors (e.g. \uFE0F for emoji representation)
                    if (nextCp in 0xFE00..0xFE0F) {
                        end += nextCharCount
                        continue
                    }
                    // Skin tone modifiers (1F3FB..1F3FF)
                    if (nextCp in 0x1F3FB..0x1F3FF) {
                        end += nextCharCount
                        continue
                    }
                    // Combining Enclosing Keycap (U+20E3)
                    if (nextCp == 0x20E3) {
                        end += nextCharCount
                        continue
                    }
                    // Regional Indicator Symbols (Flags: pair of two indicators e.g. 🇺🇸)
                    if (codePoint in 0x1F1E6..0x1F1FF && nextCp in 0x1F1E6..0x1F1FF && end == i + charCount) {
                        end += nextCharCount
                        continue
                    }
                    // Zero-Width Joiner (ZWJ: U+200D) connects sequences (e.g. 👨‍👩‍👦)
                    if (nextCp == 0x200D) {
                        end += nextCharCount
                        if (end < trimmed.length) {
                            val connectedCp = trimmed.codePointAt(end)
                            end += Character.charCount(connectedCp)
                        }
                        continue
                    }
                    break
                }
                return trimmed.substring(i, end)
            }
            i += charCount
        }
        return null
    }

    private fun isEmojiOrSymbol(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return codePoint in 0x1F300..0x1FAFF || // Misc Symbols, Pictographs, Emoticons, Transport, Supplemental
               codePoint in 0x2600..0x27BF ||   // Misc Symbols, Dingbats
               codePoint in 0x2300..0x23FF ||   // Misc Technical
               codePoint in 0x2B50..0x2B55 ||   // Stars & Symbols
               codePoint in 0x1F1E6..0x1F1FF || // Regional indicators (Flags)
               codePoint in 0x2190..0x21FF ||   // Arrows
               codePoint in 0x2900..0x297F ||   // Supplemental Arrows
               type == Character.OTHER_SYMBOL.toInt() ||
               type == Character.SURROGATE.toInt() ||
               type == Character.MATH_SYMBOL.toInt() ||
               type == Character.MODIFIER_SYMBOL.toInt()
    }
}
