(() => {
    try {
        const VENDOR = '{{WEBGL_VENDOR}}';
        const RENDERER = '{{WEBGL_RENDERER}}';

        const patchGL = (proto) => {
            if (!proto) return;
            const originalGetParameter = proto.getParameter;
            proto.getParameter = function (parameter) {
                if (parameter === 37445) return VENDOR;
                if (parameter === 37446) return RENDERER;
                if (parameter === 7936) return 'WebKit';
                if (parameter === 7937) return 'WebKit WebGL';
                return originalGetParameter.apply(this, arguments);
            };
            Object.defineProperty(proto.getParameter, 'toString', {
                value: () => 'function getParameter() { [native code] }',
                configurable: true
            });
        };

        patchGL(WebGLRenderingContext.prototype);
        if (typeof WebGL2RenderingContext !== 'undefined') {
            patchGL(WebGL2RenderingContext.prototype);
        }
    } catch (e) { /* silent */
    }
})();