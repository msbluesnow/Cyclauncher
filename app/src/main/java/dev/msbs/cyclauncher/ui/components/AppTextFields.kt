package dev.msbs.cyclauncher.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled

/**
 * Custom OutlinedTextField that eliminates cursor blinking when animations are disabled,
 * with pixel-perfect cursor positioning relative to text bounds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    cursorColor: Color = Color.Unspecified
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    val effectiveCursorColor = if (cursorColor != Color.Unspecified) {
        cursorColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)

    val cursorModifier = if (!animationsEnabled && isFocused && enabled && !readOnly) {
        Modifier.drawWithContent {
            drawContent()
            val layout = textLayoutResult
            if (layout != null) {
                val cursorOffset = textFieldValue.selection.start.coerceIn(0, layout.layoutInput.text.length)
                val cursorRect = layout.getCursorRect(cursorOffset)
                val strokeW = 2.dp.toPx()
                drawLine(
                    color = effectiveCursorColor,
                    start = Offset(cursorRect.left, cursorRect.top),
                    end = Offset(cursorRect.left, cursorRect.bottom),
                    strokeWidth = strokeW
                )
            }
        }
    } else {
        Modifier
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newTfv ->
            textFieldValueState = newTfv
            if (value != newTfv.text) {
                onValueChange(newTfv.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        cursorBrush = if (animationsEnabled) SolidColor(effectiveCursorColor) else SolidColor(Color.Transparent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = { textLayoutResult = it },
        decorationBox = @Composable { innerTextField ->
            val innerWithCursor = @Composable {
                Box(modifier = cursorModifier) {
                    innerTextField()
                }
            }
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = visualTransformation,
                innerTextField = innerWithCursor,
                placeholder = placeholder,
                label = label,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape
                    )
                }
            )
        }
    )
}

/**
 * Custom TextField that eliminates cursor blinking when animations are disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    cursorColor: Color = Color.Unspecified
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    val effectiveCursorColor = if (cursorColor != Color.Unspecified) {
        cursorColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)

    val cursorModifier = if (!animationsEnabled && isFocused && enabled && !readOnly) {
        Modifier.drawWithContent {
            drawContent()
            val layout = textLayoutResult
            if (layout != null) {
                val cursorOffset = textFieldValue.selection.start.coerceIn(0, layout.layoutInput.text.length)
                val cursorRect = layout.getCursorRect(cursorOffset)
                val strokeW = 2.dp.toPx()
                drawLine(
                    color = effectiveCursorColor,
                    start = Offset(cursorRect.left, cursorRect.top),
                    end = Offset(cursorRect.left, cursorRect.bottom),
                    strokeWidth = strokeW
                )
            }
        }
    } else {
        Modifier
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newTfv ->
            textFieldValueState = newTfv
            if (value != newTfv.text) {
                onValueChange(newTfv.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        cursorBrush = if (animationsEnabled) SolidColor(effectiveCursorColor) else SolidColor(Color.Transparent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = { textLayoutResult = it },
        decorationBox = @Composable { innerTextField ->
            val innerWithCursor = @Composable {
                Box(modifier = cursorModifier) {
                    innerTextField()
                }
            }
            TextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = visualTransformation,
                innerTextField = innerWithCursor,
                placeholder = placeholder,
                label = label,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                interactionSource = interactionSource,
                colors = colors,
                container = {
                    TextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape
                    )
                }
            )
        }
    )
}
