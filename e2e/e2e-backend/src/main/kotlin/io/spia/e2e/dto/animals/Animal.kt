package io.spia.e2e.dto.animals

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Dog::class, name = "dog"),
    JsonSubTypes.Type(value = Cat::class, name = "cat"),
    JsonSubTypes.Type(value = Bird::class, name = "bird"),
)
sealed class Animal {
    abstract val name: String
}

@JsonTypeName("dog")
data class Dog(override val name: String, val breed: String) : Animal()

@JsonTypeName("cat")
data class Cat(override val name: String, val livesLeft: Int) : Animal()

@JsonTypeName("bird")
data class Bird(override val name: String, val wingspanCm: Double) : Animal()
