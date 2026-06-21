package com.elyadata.feature.scraper.browser.humanizer;

import com.elyadata.feature.scraper.browser.humanizer.motion.BezierMouseMotion;
import com.elyadata.feature.scraper.browser.humanizer.scroll.HumanScroll;
import com.elyadata.feature.scraper.browser.humanizer.timing.HumanTiming;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlaywrightHumanizer implements Humanizer {

    private final BezierMouseMotion mouseMotion;
    private final HumanScroll humanScroll;
    private final HumanTiming timing;


    @Override
    public void afterPageLoad(Page page, HumanizerLevel level) {
        if (level == HumanizerLevel.OFF) return;

        try {
            if (level == HumanizerLevel.REALISTIC) {
                Number textLenNum = (Number) page.evaluate(
                        "() => document.body?.innerText?.length || 0");
                int textLen = textLenNum.intValue();

                long readMs = timing.readingTimeMs(textLen);
                log.debug("[HUMAN] Post-load reading pause: {}ms (textChars={})", readMs, textLen);
                mouseMotion.idleMicroMovements(page, 2);
                timing.sleep(readMs);
            } else {
                timing.sleep(timing.logNormalDelayMs(600, 0.3));
            }
        } catch (Exception e) {
            log.debug("[HUMAN] afterPageLoad interrupted: {}", e.getMessage());
        }
    }

    @Override
    public void beforeClick(Page page, String selector, HumanizerLevel level) {
        if (level == HumanizerLevel.OFF) return;
        try {
            ElementHandle el = page.querySelector(selector);
            if (el == null) return;

            var box = el.boundingBox();
            if (box == null) return;
            double targetX = box.x + box.width * (0.3 + Math.random() * 0.4);
            double targetY = box.y + box.height * (0.3 + Math.random() * 0.4);

            if (level == HumanizerLevel.REALISTIC) {
                mouseMotion.moveTo(page, targetX, targetY, box.width);
                timing.sleep(timing.logNormalDelayMs(120, 0.4));
            } else {
                page.mouse().move(targetX, targetY);
                timing.sleep(timing.logNormalDelayMs(200, 0.3));
            }
        } catch (Exception e) {
            log.debug("[HUMAN] beforeClick skipped for '{}': {}", selector, e.getMessage());
        }
    }

    @Override
    public void scroll(Page page, boolean toBottom, HumanizerLevel level) {
        if (level == HumanizerLevel.OFF) {
            page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
            return;
        }
        if (toBottom) {
            humanScroll.scrollToBottom(page, 30);
        } else {
            humanScroll.readingScroll(page);
        }
    }

    @Override
    public void reset() {
        mouseMotion.resetCursor();
    }
}