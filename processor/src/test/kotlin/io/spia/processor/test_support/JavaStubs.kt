package io.spia.processor.test_support

import com.tschuchort.compiletesting.SourceFile

/** Java POJO stub used by Java controller smoke tests. */
fun javaDto(): SourceFile = SourceFile.java(
    "EcJavaDto.java",
    """
    package test;

    public class EcJavaDto {
        private final String name;
        private final int score;

        public EcJavaDto(String name, int score) {
            this.name = name;
            this.score = score;
        }

        public String getName() { return name; }
        public int getScore() { return score; }
    }
    """.trimIndent()
)

/** Java @RestController stub used by Java controller smoke tests. */
fun javaController(): SourceFile = SourceFile.java(
    "EcJavaController.java",
    """
    package test;

    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.PathVariable;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;

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
    """.trimIndent()
)
