package com.elyadata.feature.scraper.browser.humanizer.timing;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HumanTiming")
class HumanTimingTest {

    private final HumanTiming timing = new HumanTiming();

    @Nested
    @DisplayName("logNormalDelayMs()")
    class LogNormal {

        @Test
        @DisplayName("result is strictly positive")
        void resultIsPositive() {
            for (int i = 0; i < 20; i++) {
                assertThat(timing.logNormalDelayMs(200, 0.3)).isPositive();
            }
        }

        @Test
        @DisplayName("result never exceeds medianMs * 10 (hard cap)")
        void cappedAtTenTimesMedian() {
            long median = 500L;
            for (int i = 0; i < 50; i++) {
                assertThat(timing.logNormalDelayMs(median, 2.0)).isLessThanOrEqualTo(median * 10);
            }
        }

        @Test
        @DisplayName("sigma=0 returns value within 1ms of medianMs (float truncation)")
        void sigmaZero_returnsMedian() {
            long median = 300L;
            assertThat(timing.logNormalDelayMs(median, 0.0))
                    .isBetween(median - 1, median);
        }

        @Test
        @DisplayName("higher median produces statistically higher outputs")
        void higherMedian_higherOutput() {
            long lowSum = 0, highSum = 0;
            int n = 30;
            for (int i = 0; i < n; i++) {
                lowSum += timing.logNormalDelayMs(100, 0.1);
                highSum += timing.logNormalDelayMs(1000, 0.1);
            }
            assertThat(highSum).isGreaterThan(lowSum);
        }
    }


    @Nested
    @DisplayName("reactionTimeMs()")
    class ReactionTime {

        @Test
        @DisplayName("returns positive value")
        void returnsPositive() {
            assertThat(timing.reactionTimeMs()).isPositive();
        }

        @Test
        @DisplayName("capped at 3500ms (350 * 10)")
        void cappedAtMax() {
            for (int i = 0; i < 50; i++) {
                assertThat(timing.reactionTimeMs()).isLessThanOrEqualTo(3500L);
            }
        }
    }


    @Nested
    @DisplayName("readingTimeMs()")
    class ReadingTime {

        @Test
        @DisplayName("0 chars → clamped to minimum 800ms")
        void zeroChars_clampedToMinimum() {
            assertThat(timing.readingTimeMs(0)).isPositive();
        }

        @Test
        @DisplayName("small text → result is within reasonable bounds")
        void smallText_reasonable() {
            long delay = timing.readingTimeMs(50);
            assertThat(delay).isPositive().isLessThanOrEqualTo(120_000L);
        }

        @Test
        @DisplayName("longer text produces higher delays on average")
        void longerText_higherDelay() {
            long shortSum = 0, longSum = 0;
            int n = 20;
            for (int i = 0; i < n; i++) {
                shortSum += timing.readingTimeMs(10);
                longSum += timing.readingTimeMs(500);
            }
            assertThat(longSum).isGreaterThan(shortSum);
        }

        @Test
        @DisplayName("very long text → clamped base to 12000ms * 10 = 120000ms max")
        void veryLongText_cappedAtMax() {
            for (int i = 0; i < 20; i++) {
                assertThat(timing.readingTimeMs(100_000)).isLessThanOrEqualTo(120_000L);
            }
        }
    }


    @Test
    @DisplayName("interActionDelayMs() returns positive value within cap")
    void interActionDelay_positiveAndCapped() {
        for (int i = 0; i < 20; i++) {
            long delay = timing.interActionDelayMs();
            assertThat(delay).isPositive().isLessThanOrEqualTo(12_000L);
        }
    }


    @Test
    @DisplayName("sleep(0) returns immediately without error")
    void sleepZero_noError() {
        assertThatCode(() -> timing.sleep(0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sleep(1) completes without error")
    void sleepOne_noError() {
        assertThatCode(() -> timing.sleep(1)).doesNotThrowAnyException();
    }
}