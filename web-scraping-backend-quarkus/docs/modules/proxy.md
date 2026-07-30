## Proxy Module

### Purpose

The Proxy module maintains a pool of HTTP and SOCKS proxies, leases them to scrape jobs according to a configurable rotation strategy and a set of filters, tracks per proxy health and usage statistics, and probes proxies against real anti bot systems such as Cloudflare and DataDome to know which ones are still usable.

### Endpoints

1. `GET /v1/proxies`. Paginated, filterable list of proxies. Filters include search text, status, protocol, source, anti bot provider and anti bot status, plus sorting.
2. `GET /v1/proxies/{id}`. Proxy detail, including its anti bot detection statuses.
3. `GET /v1/proxies/stats`. Pool statistics, active, inactive and blacklisted counts, and an anti bot breakdown.
4. `POST /v1/proxies`. Manually registers a proxy, with host, port, protocol, credentials, country and anonymity level.
5. `POST /v1/proxies/refresh`. Triggers a fetch from external proxy sources to grow the pool.
6. `POST /v1/proxies/{id}/blacklist`. Marks a proxy as blacklisted.
7. `POST /v1/proxies/{id}/reactivate`. Marks a proxy as active again.
8. `POST /v1/proxies/{id}/health-check`. Triggers a raw TCP reachability check.
9. `POST /v1/proxies/{id}/antibot-check`. Triggers anti bot detection, either for all registered providers or for one provider passed as a query parameter.
10. `GET /v1/proxies/{id}/antibot-statuses`. Last known detection status per anti bot provider for a proxy.
11. `DELETE /v1/proxies/{id}`. Deletes a proxy.
12. `GET /v1/proxies/antibot-providers`. Lists the registered anti bot detector providers.
13. `GET /v1/proxies/dashboard`. Aggregate dashboard view of the pool.
14. `GET /v1/proxies/countries`. Country codes available in the pool, with a proxy count per country.

### Rotation Strategies

A job's proxy configuration selects one of the following strategies, each of which is really just an ordering applied when a proxy is picked from the pool.

1. `FASTEST`. Orders by latency ascending, then by success rate descending.
2. `LEAST_USED`. Orders by total uses ascending, with a random tie break.
3. `RANDOM`. Picks a random proxy.
4. `ROUND_ROBIN`. Orders by last used time ascending, so proxies cycle evenly.

### Acquisition and Release

When a job with proxy support enabled starts, it acquires a proxy matching its country, protocol, anonymity, latency and success rate filters, using the selected strategy, and leases it for a configurable duration. If a strict country filter finds nothing, the acquisition can fall back to ignoring the country filter rather than failing outright. When the job finishes with that proxy, it releases it, which updates the proxy's usage statistics.

### Anti-Bot Detection

A registry of detector beans, one per anti bot provider, checks whether a given proxy is still able to reach a target without being challenged or blocked. Cloudflare and DataDome detectors are implemented today, by routing a probe request through the proxy to a known endpoint and inspecting response headers and body markers. Each check classifies the proxy as clean, challenged, blocked, unreachable, or untested.

### Health and Success Rate Tracking

A proxy is automatically deactivated after a configurable number of consecutive connectivity failures, and automatically blacklisted if an anti bot detector reports it as blocked. Success rate is maintained on every acquire and release cycle, and every usage is logged for later analysis. Scheduled jobs periodically re run anti bot checks and health checks in batches, clean up expired leases, and purge old health history and dead proxies.

### Proxy Sources

Proxies can be added manually, or fetched automatically from several free public proxy list providers. Fetched results are merged and deduplicated by host, port and protocol. Country information reported by these external sources is normalized into a consistent two letter code, since different sources report country in inconsistent formats.

### Related Modules

1. [Job Module](./job.md) references a proxy configuration on a job.
2. [Scraper Module](./scraper.md) is the consumer that actually routes browser traffic through an acquired proxy.
