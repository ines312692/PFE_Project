'use strict';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const TestEnvironment = require('jest-environment-jsdom');
const Base = TestEnvironment.default ?? TestEnvironment;

const LOCATION_METHODS = new Set(['assign', 'replace', 'reload']);

class LocationMockEnvironment extends Base {
  constructor(config, context) {
    const realDefineProperty = Object.defineProperty.bind(Object);

    Object.defineProperty = function patchedDefineProperty(obj, prop, descriptor) {
      if (
        LOCATION_METHODS.has(String(prop)) &&
        descriptor != null &&
        descriptor.configurable === false
      ) {
        return realDefineProperty(obj, prop, {
          ...descriptor,
          configurable: true,
          writable: true,
        });
      }

      return realDefineProperty(obj, prop, descriptor);
    };

    try {
      super(config, context);
    } finally {
      // Always restore — even if super() throws.
      Object.defineProperty = realDefineProperty;
    }
  }
}

module.exports = LocationMockEnvironment;
