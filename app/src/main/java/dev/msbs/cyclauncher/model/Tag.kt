package dev.msbs.cyclauncher.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import java.util.UUID

/** Represents a custom user-defined tag for categorizing applications. */
data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Color,
    val emoji: String? = null
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("color", color.toArgb())
        if (!emoji.isNullOrBlank()) {
            json.put("emoji", emoji)
        }
        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Tag {
            return Tag(
                id = json.getString("id"),
                name = json.getString("name"),
                color = Color(json.getInt("color")),
                emoji = if (json.has("emoji")) json.optString("emoji", "").takeIf { it.isNotBlank() } else null
            )
        }
    }
}
