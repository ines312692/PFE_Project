## Schema Module

### Purpose

The Schema module defines the extraction schema a job uses to pull structured data out of a page, and provides AI assistance to help a user write extraction prompts, validate CSS selectors against a live page, and automatically repair selectors that break when a site's markup changes.

### Extraction Schema

An extraction schema supports three parallel extraction approaches on the same schema object.

1. A list of named fields, each with a CSS selector, a type (text, attribute, HTML, number, URL, or image), an optional attribute name, and whether the field is required. This is used by `CSS_SELECTOR` extraction mode.
2. An LLM prompt and an expected output format, used by LLM based extraction.
3. A regular expression pattern and capture group, used by regex based extraction.

### Endpoints

1. `POST /v1/schema/assist`. Asks an LLM to generate, improve, or suggest an output format or a set of fields for an extraction prompt.
2. `POST /v1/schema/assist/validate`. Validates a schema's CSS selectors against a live target URL, and asks the LLM to suggest fixes for any selector that no longer matches.

There is no separate create, read, update, delete controller for the schema itself. It is persisted as part of a job.

### AI Assist Flow

1. The user sends a request describing what they want, for example generate a new prompt, improve an existing one, or suggest fields, along with any current prompt text and the target URL.
2. The free text input is checked by a security guard before it ever reaches the LLM, described below.
3. A fast LLM model is called with a system prompt tailored to the requested action, and is required to answer in a strict JSON format.
4. The response is parsed into a prompt, an output format, and a short explanation of the reasoning, or an error is returned if parsing fails.

### Security Guard

Before any user supplied prompt text is sent to an LLM, it passes through a guard that rejects empty or overly long input, detects prompt injection attempts such as instructions to ignore previous instructions or to adopt a different persona, and detects requests for forbidden content such as credentials, payment card numbers, or script and SQL injection strings. The guard also sanitizes the LLM's output, truncating overly long values and stripping any script tags that might have been echoed back. Every prompt, whether allowed or blocked, is recorded in a hashed audit trail.

### Selector Healing

When a scrape starts producing empty results because a site's markup changed, a healing routine detects which fields no longer match anything on a freshly fetched page, samples the page HTML, and asks an LLM for replacement selectors along with a confidence score. Candidates with low confidence, overly generic tags, or a match count of zero or of more than two hundred elements are rejected. Successful replacements are written back to the schema, and the job owner is notified of the outcome.

### Related Modules

1. [Job Module](./job.md) references an extraction schema on a job.
2. [Scraper Module](./scraper.md) is the consumer that runs the CSS, XPath, regex and LLM extractors described by a schema.
