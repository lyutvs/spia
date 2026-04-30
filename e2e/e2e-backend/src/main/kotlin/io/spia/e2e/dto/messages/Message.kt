package io.spia.e2e.dto.messages

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    JsonSubTypes.Type(value = TextMessage::class, name = "text"),
    JsonSubTypes.Type(value = ImageMessage::class, name = "image"),
)
sealed class Message

@JsonTypeName("text")
data class TextMessage(val body: String) : Message()

@JsonTypeName("image")
data class ImageMessage(val url: String, val widthPx: Int) : Message()
