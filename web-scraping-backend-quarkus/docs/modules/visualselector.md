## Visual Selector Module

### Purpose

This module supports a point and click selector building tool in the frontend. It renders a sanitized snapshot of a live page so a user can click an element, then takes the user's rough CSS selector guess and hardens it into a more robust, reusable selector, together with an equivalent XPath and match count feedback.

### Endpoints

1. `POST /v1/visual-selector/snapshot`. Navigates to a URL with a headless browser, dismisses common cookie banners, optionally waits for a given selector, and returns the final URL, the sanitized page HTML, the page title, and the HTML length. Returns an error if the rendered HTML looks too short, which usually means the page was blocked, and suggests enabling stealth mode.
2. `POST /v1/visual-selector/harden`. Given the page HTML, a candidate CSS selector, the page URL, and whether the user expects a single value or a list, returns a hardened CSS selector, an XPath equivalent, the match count, whether the selector is unique, a few preview values, a suggested field name, and a warning when applicable.

### HTML Sanitization

Before page HTML is sent to the frontend for the user to click on, it is sanitized. Relative links and lazy loaded image attributes are made absolute, script and noscript tags are removed, `javascript:` URLs are stripped from links and form actions, inline event handlers are stripped, and content security policy and base tags are removed. This protects the tool's own rendering surface, it is not a scraping security boundary.

### Selector Hardening

Given a candidate selector that matched at least one element, hardening tries several strategies in order of preference: a unique id selector, rejecting ids that look auto generated, a unique stable data attribute or `itemprop` selector, a tag combined with up to two stable class names, and an ancestor anchored path when a nearby stable id can be found. Overly generic tags used alone, such as `div` or `span`, are rejected. The result includes a converted XPath, a suggested field name derived from the most stable attribute available, and a warning when the selector matches nothing, matches far too many elements, or is ambiguous relative to what the user expected.

### Related Modules

1. [Schema Module](./schema.md) is where a hardened selector typically ends up, as a field in an extraction schema.
