package com.happyfamily.contract.data.ai

import com.google.firebase.ai.type.Schema
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 응답 형식(JSON 스키마)을 한 번만 정의하고
 * Firebase AI Logic 용 [Schema] 와 Gemini REST API 용 JSON 으로 둘 다 바꿔 쓴다.
 */
sealed class AiSchema(val description: String?) {
    class Str(description: String? = null) : AiSchema(description)
    class Long(description: String? = null) : AiSchema(description)
    class Bool(description: String? = null) : AiSchema(description)
    class Enum(val values: List<String>, description: String? = null) : AiSchema(description)
    class Arr(val items: AiSchema, description: String? = null) : AiSchema(description)
    class Obj(val properties: Map<String, AiSchema>, description: String? = null) : AiSchema(description)

    /** Firebase AI Logic(firebase-ai) 형식 */
    fun toFirebase(): Schema = when (this) {
        is Str -> Schema.string(description)
        is Long -> Schema.long(description)
        is Bool -> Schema.boolean(description)
        is Enum -> Schema.enumeration(values, description)
        is Arr -> Schema.array(items.toFirebase(), description)
        is Obj -> Schema.obj(properties.mapValues { it.value.toFirebase() }, description = description)
    }

    /** Gemini REST API(generationConfig.responseSchema) 형식 */
    fun toRestJson(): JSONObject = JSONObject().apply {
        when (val s = this@AiSchema) {
            is Str -> put("type", "STRING")
            is Long -> put("type", "INTEGER").put("format", "int64")
            is Bool -> put("type", "BOOLEAN")
            is Enum -> put("type", "STRING").put("format", "enum").put("enum", JSONArray(s.values))
            is Arr -> put("type", "ARRAY").put("items", s.items.toRestJson())
            is Obj -> {
                put("type", "OBJECT")
                put("properties", JSONObject().apply { s.properties.forEach { (k, v) -> put(k, v.toRestJson()) } })
                put("required", JSONArray(s.properties.keys.toList()))
                put("propertyOrdering", JSONArray(s.properties.keys.toList()))
            }
        }
        description?.let { put("description", it) }
    }
}
