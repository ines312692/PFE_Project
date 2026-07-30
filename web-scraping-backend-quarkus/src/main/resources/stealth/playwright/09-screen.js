(() => {
    try {
        const SCREEN = {
            width: {SCREEN_WIDTH},
            height: {SCREEN_HEIGHT},
            availWidth: {SCREEN_AVAIL_WIDTH},
            availHeight: {SCREEN_AVAIL_HEIGHT},
            colorDepth: 24,
            pixelDepth: 24
        };

        for (const [key, value] of Object.entries(SCREEN)) {
            try {
                Object.defineProperty(Screen.prototype, key, {
                    get: () => value,
                    configurable: true
                });
            } catch (e) { /* not configurable in all versions */
            }
        }
    } catch (e) { /* silent */
    }
})();