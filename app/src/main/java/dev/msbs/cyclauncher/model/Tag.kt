package dev.msbs.cyclauncher.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import java.util.UUID

/**
 * Represents a custom tag used to categorize applications.
 *
 * @property id The unique identifier of the tag, defaults to a random UUID string.
 * @property name The display name of the tag.
 * @property color The color assigned to the tag.
 */
data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Color
) {
    /**
     * Serializes this tag instance into a [JSONObject].
     *
     * @return The serialized tag as a JSONObject containing id, name, and ARGB color value.
     */
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("color", color.toArgb())
        return json
    }

    companion object {
        /**
         * Deserializes a [JSONObject] back into a [Tag] instance.
         *
         * @param json The JSON object containing the tag data.
         * @return A deserialized Tag instance.
         */
        fun fromJsonObject(json: JSONObject): Tag {
            return Tag(
                id = json.getString("id"),
                name = json.getString("name"),
                color = Color(json.getInt("color"))
            )
        }
    }
}
