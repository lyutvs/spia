package io.spia.sandbox;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Java @RestController demonstrating SPIA's Java support.
 * Value-class / sealed-union Kotlin features are not applicable to Java.
 */
@RestController
@RequestMapping("/api/ec-java")
public class EcJavaController {

    @GetMapping("/{name}")
    public EcJavaDto get(@PathVariable String name) {
        return new EcJavaDto(name, 42);
    }

    @PostMapping
    public EcJavaDto create(@RequestBody EcJavaDto dto) {
        return dto;
    }
}
