package dev.msbs.cyclauncher.utils

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Universal text wrapper allowing view models and domain layers to emit UI messages
 * without directly depending on Android [Context] or hardcoding display strings.
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    class StringResource(
        @StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    class PluralResource(
        @PluralsRes val resId: Int,
        val quantity: Int,
        vararg val args: Any
    ) : UiText()

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(resId, *args)
        is PluralResource -> pluralStringResource(resId, quantity, *args)
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(resId, *args)
        is PluralResource -> context.resources.getQuantityString(resId, quantity, *args)
    }
}
