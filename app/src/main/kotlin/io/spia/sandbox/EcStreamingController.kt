package io.spia.sandbox

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

data class Tick(val seq: Long, val timestamp: Long)

@RestController
@RequestMapping("/api/ec-streaming")
class EcStreamingController {

    @GetMapping("/ticks", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun ticks(): Flux<ServerSentEvent<Tick>> =
        Flux.interval(Duration.ofSeconds(1))
            .map { i -> ServerSentEvent.builder(Tick(i, System.currentTimeMillis())).build() }
            .take(10)

    @GetMapping("/file/{name}")
    fun download(@PathVariable name: String): ResponseEntity<Resource> {
        val data = "hello from $name".toByteArray()
        val resource: Resource = ByteArrayResource(data)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$name\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }
}
