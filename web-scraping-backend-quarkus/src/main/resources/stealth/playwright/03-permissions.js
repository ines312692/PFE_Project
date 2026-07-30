(() => {
    try {
        const originalQuery = window.navigator.permissions.query.bind(window.navigator.permissions);
        window.navigator.permissions.query = (parameters) => {
            if (parameters && parameters.name === 'notifications') {
                return Promise.resolve({
                    state: Notification.permission,
                    onchange: null,
                    addEventListener: () => {},
                    removeEventListener: () => {},
                    dispatchEvent: () => false
                });
            }
            return originalQuery(parameters);
        };
        Object.defineProperty(window.navigator.permissions.query, 'toString', {
            value: () => 'function query() { [native code] }',
            configurable: true
        });
    } catch (e) { /* silent */ }
})();