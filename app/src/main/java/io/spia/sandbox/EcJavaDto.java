package io.spia.sandbox;

/**
 * Plain Java POJO used by {@link EcJavaController}.
 * SPIA detects fields from getter methods (JavaBeans convention).
 */
public class EcJavaDto {

    private final String name;
    private final int score;

    public EcJavaDto(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }
}
