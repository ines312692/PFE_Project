(() => {
    try {
        if (!window.chrome) {
            Object.defineProperty(window, 'chrome', {
                value: {},
                writable: true,
                configurable: true,
                enumerable: true
            });
        }

        if (!window.chrome.runtime) {
            window.chrome.runtime = {
                PlatformOs:   { MAC: 'mac', WIN: 'win', ANDROID: 'android', CROS: 'cros', LINUX: 'linux', OPENBSD: 'openbsd' },
                PlatformArch: { ARM: 'arm', X86_32: 'x86-32', X86_64: 'x86-64' },
                PlatformNaclArch: { ARM: 'arm', X86_32: 'x86-32', X86_64: 'x86-64' },
                RequestUpdateCheckStatus: { THROTTLED: 'throttled', NO_UPDATE: 'no_update', UPDATE_AVAILABLE: 'update_available' },
                OnInstalledReason: { INSTALL: 'install', UPDATE: 'update', CHROME_UPDATE: 'chrome_update', SHARED_MODULE_UPDATE: 'shared_module_update' },
                OnRestartRequiredReason: { APP_UPDATE: 'app_update', OS_UPDATE: 'os_update', PERIODIC: 'periodic' }
            };
        }

        if (!window.chrome.loadTimes) {
            window.chrome.loadTimes = function () {
                const now = Date.now() / 1000;
                return {
                    requestTime: now - Math.random() * 2,
                    startLoadTime: now - Math.random() * 2,
                    commitLoadTime: now - Math.random(),
                    finishDocumentLoadTime: now - Math.random() / 2,
                    finishLoadTime: now - Math.random() / 4,
                    firstPaintTime: now - Math.random() / 4,
                    firstPaintAfterLoadTime: 0,
                    navigationType: 'Other',
                    wasFetchedViaSpdy: true,
                    wasNpnNegotiated: true,
                    npnNegotiatedProtocol: 'h2',
                    wasAlternateProtocolAvailable: false,
                    connectionInfo: 'h2'
                };
            };
        }

        if (!window.chrome.csi) {
            window.chrome.csi = function () {
                return {
                    startE: Date.now() - 1000,
                    onloadT: Date.now() - 500,
                    pageT: 500,
                    tran: 15
                };
            };
        }

        const nativeToString = Function.prototype.toString;
        const makeNative = (fn, name) => {
            const proxy = new Proxy(fn, {
                apply: (target, thisArg, args) => Reflect.apply(target, thisArg, args),
                get: (target, prop) => {
                    if (prop === 'toString') {
                        return () => `function ${name}() { [native code] }`;
                    }
                    return Reflect.get(target, prop);
                }
            });
            return proxy;
        };
        window.chrome.loadTimes = makeNative(window.chrome.loadTimes, 'loadTimes');
        window.chrome.csi = makeNative(window.chrome.csi, 'csi');
    } catch (e) { /* silent */ }
})();