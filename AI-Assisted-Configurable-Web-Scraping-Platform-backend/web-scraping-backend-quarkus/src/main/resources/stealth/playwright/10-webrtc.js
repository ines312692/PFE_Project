(() => {
    try {
        if (typeof RTCPeerConnection === 'undefined') return;

        const OriginalRTCPeerConnection = window.RTCPeerConnection;

        class PatchedRTCPeerConnection extends OriginalRTCPeerConnection {
            constructor(...args) {
                super(...args);
                this.addEventListener('icecandidate', (event) => {
                    if (event.candidate && event.candidate.candidate) {
                        const c = event.candidate.candidate;
                        if (c.includes('typ host') || c.includes('typ srflx')) {
                            event.stopImmediatePropagation();
                        }
                    }
                }, true);
            }

            createOffer(...args) {
                return super.createOffer(...args).then((offer) => {
                    if (offer && offer.sdp) {
                        offer.sdp = offer.sdp.replace(/^a=candidate:.+typ (host|srflx).+\r\n/gm, '');
                    }
                    return offer;
                });
            }
        }

        window.RTCPeerConnection = PatchedRTCPeerConnection;
        Object.defineProperty(PatchedRTCPeerConnection, 'toString', {
            value: () => 'function RTCPeerConnection() { [native code] }',
            configurable: true
        });

        if (window.webkitRTCPeerConnection) {
            window.webkitRTCPeerConnection = PatchedRTCPeerConnection;
        }
    } catch (e) { /* silent */
    }
})();