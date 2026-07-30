(() => {
    try {
        const HW = {
            hardwareConcurrency: {HW_CONCURRENCY},
            deviceMemory: {DEVICE_MEMORY},
            platform: '{PLATFORM}',
            maxTouchPoints: {MAX_TOUCH_POINTS},
            vendor: '{VENDOR}',
            languages: {LANGUAGES_JSON},
            language: '{LANGUAGE}',
            doNotTrack: null
        };

        for (const [key, value] of Object.entries(HW)) {
            try {
                Object.defineProperty(Navigator.prototype, key, {
                    get: () => value,
                    configurable: true,
                    enumerable: true
                });
            } catch (e) { /* some props are not configurable in all Chrome versions */
            }
        }
    } catch (e) { /* silent */
    }
})();