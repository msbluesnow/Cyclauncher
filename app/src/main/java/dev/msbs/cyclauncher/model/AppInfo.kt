package dev.msbs.cyclauncher.model

/**
 * Информация об установленном приложении.
 * Иконки загружаются лениво через Coil по [iconKey], чтобы ViewModel
 * не держал декодированные битмапы в памяти.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconKey: String,
    val searchChar: Char = ' '
) {
    val componentKey: String get() = "$packageName/$activityName"
}
