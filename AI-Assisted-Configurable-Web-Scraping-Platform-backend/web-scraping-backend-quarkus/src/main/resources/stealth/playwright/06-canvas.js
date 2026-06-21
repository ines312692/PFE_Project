(() => {
    try {
        const sessionSeed = (() => {
            const arr = new Uint32Array(2);
            crypto.getRandomValues(arr);
            return arr[0] * 4294967296 + arr[1];
        })();
        const makePrng = (seed) => {
            let s = seed >>> 0;
            return () => {
                s = (s + 0x6D2B79F5) >>> 0;
                let t = s;
                t = Math.imul(t ^ (t >>> 15), t | 1);
                t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
                return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
            };
        };

        const noisify = (canvas, context) => {
            if (!context) return;
            const {width, height} = canvas;
            if (width === 0 || height === 0) return;

            try {
                const imageData = context.getImageData(0, 0, width, height);
                const data = imageData.data;
                const prng = makePrng(sessionSeed ^ (width * 31 + height));
                const pixelCount = width * height;
                const touchCount = Math.min(20, Math.max(3, Math.floor(pixelCount * 0.00001)));

                for (let i = 0; i < touchCount; i++) {
                    const idx = Math.floor(prng() * pixelCount) * 4;
                    data[idx] = data[idx] ^ 1;
                    data[idx + 1] = data[idx + 1] ^ 1;
                    data[idx + 2] = data[idx + 2] ^ 1;
                }
                context.putImageData(imageData, 0, 0);
            } catch (e) { /* tainted canvas (cross-origin image) — skip */
            }
        };

        const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
        HTMLCanvasElement.prototype.toDataURL = function (...args) {
            noisify(this, this.getContext('2d'));
            return originalToDataURL.apply(this, args);
        };

        const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
        CanvasRenderingContext2D.prototype.getImageData = function (...args) {
            const imageData = originalGetImageData.apply(this, args);
            // Apply noise to the returned ImageData directly (some detectors bypass toDataURL).
            const data = imageData.data;
            const prng = makePrng(sessionSeed ^ (args[2] * 31 + args[3]));
            const pixelCount = data.length / 4;
            const touchCount = Math.min(20, Math.max(3, Math.floor(pixelCount * 0.00001)));
            for (let i = 0; i < touchCount; i++) {
                const idx = Math.floor(prng() * pixelCount) * 4;
                data[idx] = data[idx] ^ 1;
                data[idx + 1] = data[idx + 1] ^ 1;
                data[idx + 2] = data[idx + 2] ^ 1;
            }
            return imageData;
        };

        Object.defineProperty(HTMLCanvasElement.prototype.toDataURL, 'toString', {
            value: () => 'function toDataURL() { [native code] }',
            configurable: true
        });
        Object.defineProperty(CanvasRenderingContext2D.prototype.getImageData, 'toString', {
            value: () => 'function getImageData() { [native code] }',
            configurable: true
        });
    } catch (e) { /* silent */
    }
})();