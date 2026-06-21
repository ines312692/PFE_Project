(() => {
    try {
        const sessionFactor = 1 + (crypto.getRandomValues(new Uint32Array(1))[0] / 4294967296) * 1e-7;

        const patch = (proto, method) => {
            if (!proto || !proto[method]) return;
            const original = proto[method];
            proto[method] = function (...args) {
                const result = original.apply(this, args);
                if (result && result.length) {
                    for (let i = 0; i < result.length; i += 100) {
                        result[i] = result[i] * sessionFactor;
                    }
                }
                return result;
            };
            Object.defineProperty(proto[method], 'toString', {
                value: () => `function ${method}() { [native code] }`,
                configurable: true
            });
        };

        if (typeof AudioBuffer !== 'undefined') {
            patch(AudioBuffer.prototype, 'getChannelData');
        }
        if (typeof AnalyserNode !== 'undefined') {
            patch(AnalyserNode.prototype, 'getFloatFrequencyData');
            patch(AnalyserNode.prototype, 'getByteFrequencyData');
        }
    } catch (e) { /* silent */
    }
})();