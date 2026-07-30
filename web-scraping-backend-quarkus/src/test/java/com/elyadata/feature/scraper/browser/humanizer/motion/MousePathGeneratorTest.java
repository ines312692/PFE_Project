package com.elyadata.feature.scraper.browser.humanizer.motion;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MousePathGenerator")
class MousePathGeneratorTest {

    private final MousePathGenerator generator = new MousePathGenerator();

    @Test
    @DisplayName("generate() returns exactly steps+1 points")
    void sizeIsStepsPlusOne() {
        List<MousePathGenerator.Point> path = generator.generate(0, 0, 100, 100, 10);
        assertThat(path).hasSize(11);
    }

    @Test
    @DisplayName("generate() with 1 step returns 2 points (start + end)")
    void singleStep_twoPoints() {
        List<MousePathGenerator.Point> path = generator.generate(0, 0, 50, 50, 1);
        assertThat(path).hasSize(2);
    }

    @Test
    @DisplayName("generate() with 20 steps returns 21 points")
    void twentySteps_twentyOnePoints() {
        List<MousePathGenerator.Point> path = generator.generate(0, 0, 200, 150, 20);
        assertThat(path).hasSize(21);
    }


    @Test
    @DisplayName("first point is the start coordinate")
    void firstPointIsStart() {
        List<MousePathGenerator.Point> path = generator.generate(100.0, 200.0, 500.0, 400.0, 15);
        assertThat(path.getFirst().x()).isCloseTo(100.0, within(0.001));
        assertThat(path.getFirst().y()).isCloseTo(200.0, within(0.001));
    }

    @Test
    @DisplayName("last point is the target coordinate")
    void lastPointIsTarget() {
        List<MousePathGenerator.Point> path = generator.generate(0.0, 0.0, 300.0, 250.0, 20);
        assertThat(path.getLast().x()).isCloseTo(300.0, within(0.001));
        assertThat(path.getLast().y()).isCloseTo(250.0, within(0.001));
    }


    @Test
    @DisplayName("path moves monotonically towards target on average (horizontal)")
    void pathProgressesHorizontally() {
        List<MousePathGenerator.Point> path = generator.generate(0, 100, 1000, 100, 30);
        double startX = path.getFirst().x();
        double endX = path.getLast().x();
        assertThat(endX).isGreaterThan(startX);
    }

    @Test
    @DisplayName("path moves monotonically towards target on average (vertical)")
    void pathProgressesVertically() {
        List<MousePathGenerator.Point> path = generator.generate(100, 0, 100, 800, 30);
        double startY = path.getFirst().y();
        double endY = path.getLast().y();
        assertThat(endY).isGreaterThan(startY);
    }


    @Test
    @DisplayName("all generated points are finite (no NaN or Infinite)")
    void allPointsAreFinite() {
        List<MousePathGenerator.Point> path = generator.generate(50, 50, 750, 500, 25);
        for (MousePathGenerator.Point p : path) {
            assertThat(Double.isFinite(p.x())).isTrue();
            assertThat(Double.isFinite(p.y())).isTrue();
        }
    }


    @Test
    @DisplayName("pure horizontal path (same y) produces valid points")
    void horizontalPath_valid() {
        List<MousePathGenerator.Point> path = generator.generate(0, 200, 500, 200, 10);
        assertThat(path).hasSize(11);
        assertThat(path.getLast().x()).isCloseTo(500.0, within(0.001));
    }

    @Test
    @DisplayName("pure vertical path (same x) produces valid points")
    void verticalPath_valid() {
        List<MousePathGenerator.Point> path = generator.generate(300, 0, 300, 600, 10);
        assertThat(path).hasSize(11);
        assertThat(path.getLast().y()).isCloseTo(600.0, within(0.001));
    }


    @Test
    @DisplayName("Point record exposes x() and y()")
    void pointRecord_accessors() {
        MousePathGenerator.Point p = new MousePathGenerator.Point(3.14, 2.71);
        assertThat(p.x()).isEqualTo(3.14);
        assertThat(p.y()).isEqualTo(2.71);
    }
}
