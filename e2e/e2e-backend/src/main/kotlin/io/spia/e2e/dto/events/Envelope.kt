package io.spia.e2e.dto.events

import io.spia.e2e.dto.animals.Animal
import io.spia.e2e.dto.messages.Message

data class Envelope(
    val animal: Animal,
    val message: Message,
    val timestamp: Long,
)
