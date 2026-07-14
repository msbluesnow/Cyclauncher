package dev.msbs.cyclauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import java.util.UUID

data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Color
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("color", color.toArgb())
        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Tag {
            return Tag(
                id = json.getString("id"),
                name = json.getString("name"),
                color = Color(json.getInt("color"))
            )
        }
    }
}
