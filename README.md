<div align="center">

<img src="Frontend/src/assets/ElyadataLogoWhite.svg" alt="Elyadata" width="220"/>

<br/>
<br/>

# ElyaScrape

### AI-Assisted Configurable Web Scraping Platform

*From a single request to a full-scale distributed crawl: schema-driven, anti-bot ready, self-healing, and fully self-hostable.*

<br/>

[![License](https://img.shields.io/badge/License-Proprietary-lightgrey?style=flat)](LICENSE)
[![Backend](https://img.shields.io/badge/Backend-Spring_Boot_%7C_Python-6DB33F?style=flat&logo=springboot)](#tech-stack)
[![Frontend](https://img.shields.io/badge/Frontend-Angular-DD0031?style=flat&logo=angular)](#tech-stack)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=flat&logo=docker)](#quick-start)
[![Self-Hostable](https://img.shields.io/badge/Deployment-Self_Hostable-success?style=flat)](#tech-stack)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=flat)](#contributing)

<br/>

**[Features](#key-features)** · **[Quick Start](#quick-start)** · **[Architecture](#architecture)** · **[Comparison](#comparison)** · **[Tech Stack](#tech-stack)** · **[Roadmap](#roadmap)**

</div>

---

## Overview

**ElyaScrape** is a configurable scraping platform that extracts structured data from any web source based on explicitly defined **schemas**, **parameters**, and **extraction rules**, while handling rate limits, bot-detection, and large-scale workloads, then normalizing and cleaning the data before exporting it to your target backend.

Unlike open-source frameworks (which require manual assembly) or proprietary SaaS (which lock you in at premium prices), ElyaScrape combines **enterprise-grade features** with a **single self-hostable platform you control**: a distributed architecture, an integrated cleaning pipeline, AI post-processing, and a no-code web interface, all in one place, deployed on your own infrastructure with no per-request vendor billing.

> Built as an end-to-end application for the **configuration, execution, monitoring, and evaluation** of scraping jobs.

---

## Key Features

### Extraction & Intelligence

- **Schema-driven extraction:** Define what to extract with **CSS**, **XPath**, **Regex**, or let an **LLM** detect the structure automatically from a plain-language prompt.
- **Visual LLM extraction:** A vision-capable LLM reads the rendered page and returns structured JSON, ideal for brittle or selector-less layouts.
- **Self-healing scrapers:** When a layout shift breaks a selector, the AI detects the rupture and proposes a corrected selector, no manual patch.
- **AI post-processing:** KPI extraction, automatic classification, anomaly detection, and semantic enrichment of extracted data.
- **Field validation:** Strict schema validation catches missing fields and type mismatches the moment data is extracted.

### Anti-Bot & Fetching

- **4-level anti-bot bypass:** Stealth browser, then residential proxy rotation, then fingerprint variation, then dedicated engines for **Cloudflare** and **DataDome**.
- **Proxy rotation:** Native proxy pool management with health monitoring and per-country geo-targeting.
- **Fingerprint management:** Full HTTP fingerprint variation, `navigator.webdriver` masking, and human-behavior simulation (mouse movement, randomized delays).
- **CAPTCHA solving (extensible):** Plug in your own CAPTCHA-solving provider through the plugin SDK, you stay in control.
- **Anti-bot difficulty scoring:** Probe a target and score its protection level *before* launching a job.
- **JavaScript rendering:** Full SPA and dynamic-content support (Angular, React, Vue) via headless browser automation.

### Architecture & Scale

- **Distributed architecture:** Task queue plus horizontally scalable worker nodes for high-volume crawls.
- **Resilient by design:** Automatic retries, throttling, failure handling, and graceful recovery.
- **Plugin system:** Extend with custom extractors, transformers, and destinations through a clean SDK, without touching the core.

### Data Pipeline & Output

- **Integrated cleaning pipeline:** Format normalization (dates, currencies, units), deduplication, missing-value strategies, and encoding fixes.
- **Multi-format export:** **JSON**, **CSV**, **Parquet**, **PostgreSQL**, **MongoDB**, **REST API**, or **webhook**, streamed straight into your stack.
- **Data versioning:** Versioned snapshots of every run for monitoring, debugging, and reproducibility.

### Operations & Experience

- **Real-time monitoring:** Live metrics, structured logs, success rates, proxy health, and configurable alerts on Slack, email, or webhook.
- **No-code wizard:** A point-and-click builder for non-technical users: pick fields visually, set a schedule, hit export.
- **GDPR-ready by default:** `robots.txt` respected, PII auto-anonymization, full audit trail, configurable retention.
- **Multi-tenant workspaces:** Projects, roles, invitations, and audit logs for team collaboration.

---

## Quick Start

### With Docker

```bash
# Clone the repository
git clone https://github.com/elyadata/elyascrape.git
cd elyascrape

# Spin up the full stack (API, workers, frontend, DBs, queue)
docker compose up -d
```

The web interface is then available at `http://localhost:4200` and the API at `http://localhost:8080`.

### Define a job in seconds

```jsonc
{
  "name": "product-catalog",
  "url": "https://example.com/products",
  "extraction": {
    "mode": "css",
    "baseSelector": ".product",
    "fields": [
      { "name": "title",  "selector": "h2",          "type": "text" },
      { "name": "price",  "selector": ".price",       "type": "text" },
      { "name": "stock",  "selector": ".availability", "type": "text" }
    ]
  },
  "pipeline": { "deduplicate": true, "normalize": ["price", "currency"] },
  "export":   { "format": "json", "destination": "webhook" }
}
```

---

## Architecture

```
            ┌──────────────────────────────────────────────┐
            │            Web Interface (Angular)           │
            │   Jobs · Schema Builder · Monitoring · ETL   │
            └──────────────────────┬───────────────────────┘
                                   │  REST / WebSocket
            ┌──────────────────────▼───────────────────────┐
            │           API Layer (Spring Boot)            │
            │   Auth · Projects · Jobs · Webhooks · Audit  │
            └──────────┬─────────────────────┬─────────────┘
                       │                     │
              ┌────────▼────────┐   ┌────────▼─────────┐
              │   Task Queue    │   │   Datastores     │
              │  (Redis/Valkey) │   │ PostgreSQL+Mongo │
              └────────┬────────┘   └──────────────────┘
                       │
            ┌──────────▼───────────────────────────────────┐
            │            Worker Nodes (Python)             │
            │  Fetch → Anti-bot → Extract → Clean → Export │
            └──────────────────────────────────────────────┘
```

---

## Comparison

How ElyaScrape positions itself against popular open-source and proprietary solutions:

| Capability                              | **ElyaScrape** | Apify | Octoparse | Bright Data | Scrapy |
|-----------------------------------------|:--------------:|:-----:|:---------:|:-----------:|:------:|
| Self-hostable (deploy on your own infra) |       Yes      |  No   |    No     |     No      |  Yes   |
| Integrated cleaning & normalization     |       Yes      | Part. |    No     |    Part.    | Part.  |
| AI post-processing (KPIs, anomalies)    |       Yes      | Part. |    No     |    Part.    |  No    |
| Self-healing selectors (AI auto-repair) |       Yes      |  No   |    No     |     No      |  No    |
| Visual LLM extraction (vision to JSON)  |       Yes      | Part. |    No     |    Part.    |  No    |
| Anti-bot difficulty scoring             |       Yes      |  No   |    No     |     No      |  No    |
| Multi-mode extraction (CSS/XPath/Regex/LLM) |   Yes      | Yes   |   Part.   |    Part.    |  Yes   |
| JavaScript rendering (SPA)              |       Yes      | Yes   |   Yes     |     Yes     | Part.  |
| Anti-bot bypass (Cloudflare, DataDome)  |       Yes      | Yes   |   Part.   |     Yes     |  No    |
| Distributed architecture (queue+workers)|       Yes      | Yes   |   Part.   |     Yes     | Part.  |
| Plugin system for custom logic          |       Yes      | Part. |    No     |     No      |  Yes   |
| No-code visual builder                  |       Yes      | Part. |   Yes     |    Part.    |  No    |
| Real-time monitoring & alerts           |       Yes      | Yes   |   Part.   |     Yes     |  No    |
| Multi-format export (JSON/CSV/DB/webhook)|      Yes      | Yes   |   Part.   |     Yes     | Part.  |
| Starting price                          |   **€0 / €19** |  €39  |    $89    |    $499+    |   €0   |

---

## Tech Stack

| Layer                  | Technologies                                  |
|------------------------|-----------------------------------------------|
| **Backend / API**      | Spring Boot / Quarkus, Python (workers)       |
| **Frontend**           | Angular (standalone components, signals)      |
| **Databases**          | PostgreSQL (SQL), MongoDB (NoSQL)             |
| **Caching & Queue**    | Redis / Valkey                                |
| **Browser automation** | Playwright (headless Chromium)                |
| **AI / LLM**           | Pluggable providers (OpenAI, Anthropic, etc.) |
| **DevOps**             | Docker, CI/CD pipelines                       |

---

## Roadmap

- [x] Schema-driven extraction (CSS / XPath / Regex / LLM)
- [x] Distributed workers with task queue
- [x] Cleaning & normalization pipeline
- [x] Real-time monitoring dashboard
- [x] No-code visual builder
- [ ] Self-healing selectors (beta)
- [ ] Visual LLM extraction (beta)
- [ ] Marketplace of community plugins
- [ ] Managed cloud offering (Pro / Enterprise)

---

## Contributing

Contributions are welcome. Whether it's a bug report, a new extractor plugin, or a documentation fix, please open an issue or a pull request. Read the contributing guidelines before getting started.

---

## Disclaimer

> This platform is provided for legitimate data-collection purposes. By using it, you agree to comply with local and international data-scraping and privacy laws. Always respect website terms of service and `robots.txt`. The authors are not responsible for any misuse.

---

## License

Proprietary software, developed at Elyadata. All rights reserved. See the `LICENSE` file for usage terms.

---

## Authors

**Intern:** Ines Tmimi
**Supervisor:** Iheb Boumaiza

---

<div align="center">

Designed & crafted with care at **Elyadata**

<sub>PFE 2026 — AI-Assisted Configurable Web Scraping Platform</sub>

</div>
