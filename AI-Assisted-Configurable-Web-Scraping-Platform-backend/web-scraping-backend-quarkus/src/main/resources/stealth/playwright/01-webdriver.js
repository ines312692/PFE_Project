(() => {
    try {
        Object.defineProperty(Navigator.prototype, 'webdriver', {
            get: () => undefined,
            configurable: true,
            enumerable: false
        });
        delete Object.getPrototypeOf(navigator).webdriver;
    } catch (e) { /* swallow: detection of throw is itself a signal */ }
})();