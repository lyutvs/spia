package io.spia.sandbox

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = Circle::class, name = "circle"),
    JsonSubTypes.Type(value = Square::class, name = "square"),
    JsonSubTypes.Type(value = Triangle::class, name = "triangle"),
)
sealed class Shape

@JsonTypeName("circle")
data class Circle(val radius: Double) : Shape()

@JsonTypeName("square")
data class Square(val side: Double) : Shape()

@JsonTypeName("triangle")
data class Triangle(val base: Double, val height: Double) : Shape()

@RestController
@RequestMapping("/api/ec-sealed")
class EcSealedController {

    @GetMapping("/shape/circle")
    fun getCircle(): Shape = Circle(radius = 5.0)

    @GetMapping("/shape/square")
    fun getSquare(): Shape = Square(side = 3.0)

    @GetMapping("/shape/triangle")
    fun getTriangle(): Shape = Triangle(base = 4.0, height = 6.0)
}
