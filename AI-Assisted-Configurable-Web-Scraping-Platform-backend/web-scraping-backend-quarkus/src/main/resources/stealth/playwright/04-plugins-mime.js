(() => {
    try {
        const makeMime = (type, suffixes, description) => {
            const mime = Object.create(MimeType.prototype);
            Object.defineProperties(mime, {
                type: {value: type, enumerable: true},
                suffixes: {value: suffixes, enumerable: true},
                description: {value: description, enumerable: true},
                enabledPlugin: {value: null, enumerable: true, writable: true}
            });
            return mime;
        };

        const pdfMime = makeMime('application/pdf', 'pdf', 'Portable Document Format');
        const pdfMimeViewer = makeMime('text/pdf', 'pdf', 'Portable Document Format');

        const makePlugin = (name, filename, description, mimes) => {
            const plugin = Object.create(Plugin.prototype);
            Object.defineProperties(plugin, {
                name: {value: name, enumerable: true},
                filename: {value: filename, enumerable: true},
                description: {value: description, enumerable: true},
                length: {value: mimes.length, enumerable: true}
            });
            mimes.forEach((m, i) => {
                plugin[i] = m;
                plugin[m.type] = m;
                m.enabledPlugin = plugin;
            });
            plugin.item = (i) => plugin[i] || null;
            plugin.namedItem = (n) => plugin[n] || null;
            return plugin;
        };

        const plugins = [
            makePlugin('PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfMimeViewer]),
            makePlugin('Chrome PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfMimeViewer]),
            makePlugin('Chromium PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfMimeViewer]),
            makePlugin('Microsoft Edge PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfMimeViewer]),
            makePlugin('WebKit built-in PDF', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfMimeViewer])
        ];

        const pluginArray = Object.create(PluginArray.prototype);
        plugins.forEach((p, i) => {
            Object.defineProperty(pluginArray, i, {value: p, enumerable: true});
            Object.defineProperty(pluginArray, p.name, {value: p, enumerable: false});
        });
        Object.defineProperty(pluginArray, 'length', {value: plugins.length});
        pluginArray.item = (i) => pluginArray[i] || null;
        pluginArray.namedItem = (n) => pluginArray[n] || null;
        pluginArray.refresh = () => {
        };

        const allMimes = [pdfMime, pdfMimeViewer];
        const mimeTypeArray = Object.create(MimeTypeArray.prototype);
        allMimes.forEach((m, i) => {
            Object.defineProperty(mimeTypeArray, i, {value: m, enumerable: true});
            Object.defineProperty(mimeTypeArray, m.type, {value: m, enumerable: false});
        });
        Object.defineProperty(mimeTypeArray, 'length', {value: allMimes.length});
        mimeTypeArray.item = (i) => mimeTypeArray[i] || null;
        mimeTypeArray.namedItem = (n) => mimeTypeArray[n] || null;

        Object.defineProperty(Navigator.prototype, 'plugins', {get: () => pluginArray, configurable: true});
        Object.defineProperty(Navigator.prototype, 'mimeTypes', {get: () => mimeTypeArray, configurable: true});
    } catch (e) { /* silent */
    }
})();