## Distributed Job Processing

### Introduction and Goal

This document describes the full lifecycle of a job, from the moment a user triggers it to the moment the ETL pipeline produces cleaned data. It focuses on the distributed crawl case, which is the most involved, but the same dispatch, worker, lease and recovery mechanism applies to plain scrape jobs as well.

The defining property of this architecture is that it is distributed. Several workers, potentially running on several machines, collaborate on crawling a single site without duplicating work. Coordination is entirely built on Redis, which acts both as a message bus (task queues) and as shared state (the frontier).

### The Three Actors

1. Node A, the principal. It receives the job, opens the frontier, recruits reinforcements, crawls, then aggregates and persists the final result.
2. Node B, the reinforcement. It joins a crawl that is already in progress to parallelize it. It crawls URLs but never finalizes or persists anything.
3. Redis. It is the message bus (the `job.run`, `crawl.join` and `etl.process` streams) and the shared crawl state (`ready`, `seen`, `done`, `pages`, `records`, `finalized`).

Node A and Node B are roles, principal and reinforcement, not necessarily two physical machines. In a single instance deployment, both roles can be carried by the same JVM through separate worker thread pools. The architecture is nonetheless designed for a real multi machine deployment.

### Redis Concepts Used

Understanding the following Redis mechanisms is necessary to follow the coordination logic.

1. Stream and `XADD`: a persistent message queue. `XADD` appends a message, for example a job to process or a URL to crawl.
2. Consumer group and `XREADGROUP`: lets several consumers share a single stream, so that a message is delivered to exactly one consumer of the group. This is what prevents duplicate work between nodes.
3. PEL, the Pending Entries List: the list of messages that were read but not yet acknowledged with `XACK`. A message that fails processing stays there and can be reclaimed later.
4. `XACK`: acknowledges a message, removing it from the PEL and confirming the work is done.
5. `XAUTOCLAIM`: reclaims orphaned messages from the PEL, meaning messages that stayed unacknowledged for too long, for example after a crash, so another consumer can pick them up.
6. DLQ, the Dead Letter Queue: the queue of messages that permanently failed after a maximum number of attempts (`max-deliveries`, 3 by default).
7. `SET NX EX`, the lease: a temporary atomic lock, described in detail below.
8. `SETNX`: sets a value only if the key does not already exist. It is used to elect the finalizer node.
9. Set, through `SADD` and `SCARD`: a collection without duplicates, used for `seen` (discovered URLs) and `done` (finished URLs).
10. Counter, through `INCR`: an atomic counter, used for `pages` (number of crawled pages).
11. Hash, through `HSET` and `HGETALL`: a key to value table, used for `records` (URL to extracted data).

### The Nine Phases

#### Phase 1, enqueue and dispatch

A user, a scheduled trigger, or a webhook asks for a job to be launched. `JobEnqueueService` applies the following logic.

1. Check that the job exists and is in an enqueueable status (`CREATED`, `PAUSED`, `WAITING_FOR_HUMAN`, `COMPLETED`, `FAILED`).
2. Move the job status to `QUEUED` in the database.
3. Publish a message on the Redis stream `stream:job.run` through `XADD`, with the job id and the enqueue timestamp.

A robustness point worth noting: if the Redis `XADD` call fails, the status is immediately rolled back to its previous value. Without this rollback, the job would stay stuck in `QUEUED` forever, and since `QUEUED` is not an enqueueable status, any manual retry would be silently ignored.

```java
jobPort.update(job.toBuilder().status(QUEUED).build());
try {
    dispatchPort.dispatch(jobId);      // XADD stream:job.run
} catch (Exception e) {
    jobPort.update(job);               // roll back to the previous status
    throw e;
}
return true;   // QUEUED and dispatched
```

#### Phase 2, worker detection through poll, lease and PEL or DLQ

`JobRunWorker` polls the stream every second (a scheduled task at a one second interval), only when it has free capacity in its worker pool. It reads new messages through `XREADGROUP job-runners > (count = free slots)`.

For each message the worker does the following.

1. If the job id is invalid, or the job is already in a terminal status, `handleFailure` routes the message to the PEL for retry, or to the DLQ once `max-deliveries` (3) is exceeded.
2. Otherwise, the worker tries to acquire a lease (a lock) on the job.
3. If the lease is held by another node, the worker skips this message without acknowledging it, so it stays in the PEL and can be reclaimed later.
4. If the lease is acquired, the worker runs the job synchronously, from `runJobSync` down to `runScrape(request)`.

##### The lease, a temporary expiring lock

The lease is the mechanism that guarantees only one worker processes a given job at any point in time, even if several nodes see the same message. It relies on a single atomic Redis command.

```
SET job:lease:{jobId} = "nodeA-54114"  NX  EX 90
```

1. The key `job:lease:{jobId}` is a lock unique to that job.
2. The value, the node identity, indicates who holds the lock, for example `nodeA-54114`.
3. `NX`, not exists, only sets the lock if it does not already exist, so exactly one node wins and the others fail.
4. `EX 90` makes the lock expire on its own after 90 seconds.

Automatic expiration is essential. If the owning node crashes mid crawl, the lock disappears by itself after 90 seconds and another node can pick up the job. Without this, the job would be stuck forever.

Since a crawl can run for more than 90 seconds, the lease is renewed every 30 seconds, as long as the worker is still active and still owns the lock.

```java
if (identity.getName().equals(values.get(key))) {  // still the owner?
    keys.expire(key, 90);                          // push back the expiration
} else {
    activeLeases.remove(jobId);                    // lease was lost, give up
}
```

At the end, the lease is released with `DEL`, but only after verifying that the node is indeed still the owner, so it never erases another node's lock by mistake.

An analogy: booking a meeting room by writing your name on the door. You only book if the slot is free (`NX`), the booking expires by itself if you forget to extend it (`EX`), you regularly walk by to push back the end time (renewal), and you erase your name when you leave (release).

#### Phase 3, routing and opening the frontier, node A

After acquiring the lease, node A runs `ScrapeEngine.runScrape`.

1. A security check, `SsrfGuard.evaluate(targetUrl)`, blocks internal or otherwise dangerous URLs.
2. If the job type is `CRAWLER` and distributed crawling is enabled, the request is routed to `DistributedCrawlCoordinator.run(...)`.
3. The frontier is opened. `ensureGroup(jobId)` creates the `ready` stream and its `crawlers` consumer group through `XGROUP CREATE ... MKSTREAM`. A `BUSYGROUP` error is ignored, since the operation is idempotent.
4. The initial seed happens. The target URL, and any additional seed URLs, are added to the frontier.

##### The frontier, the shared crawl state

The frontier is the set of Redis structures that describe how far a crawl has progressed. It is manipulated through the `DistributedCrawlFrontier` facade.

1. `ready`, a stream with a consumer group: the queue of URLs still to visit. The `crawlers` consumer group distributes them between node A and node B without duplication.
2. `seen`, a set: URLs already discovered, so the same page is never crawled twice.
3. `done`, a set: URLs whose processing has finished.
4. `pages`, a counter: total number of crawled pages, shared between A and B, compared against `maxPages`.
5. `records`, a hash: URL to extracted data, stored as JSON.
6. `finalized`, set through `SETNX`: an election flag. Only one node can set it, and that node becomes the finalizer.
7. `attempts`, a hash: a per URL retry counter, used to bound retries when the circuit breaker is open.

Adding a URL, through the `enqueueIfNew` method.

```java
long added = sets.sadd(seen(jobId), url);   // already seen?
if (added == 0) return false;               // yes, skip it
streams.xadd(ready(jobId), payload(node));  // no, add it to the queue
keys.expire(ready, TTL);  keys.expire(seen, TTL);   // a six hour TTL
```

#### Phase 4, publishing reinforcements

Node A computes the desired number of reinforcements as `joins = parallelism - 1`. For each reinforcement, it publishes a message on `stream:crawl.join`. If `parallelism` is 1, meaning solo mode, no reinforcement is published. Node A then starts its own drain loop, logged as the principal drain.

#### Phase 5, a reinforcement joins the crawl, node B

`CrawlJoinWorker` on node B reads `stream:crawl.join` every second. When it receives a message it does the following.

1. If the job is missing, terminal, or not of type `CRAWLER`, it acknowledges immediately and provides no reinforcement.
2. Otherwise, it calls `buildRequestForJoin(jobId)`, acquiring a proxy if needed, then calls `joinExistingCrawl(...)`.

The reinforcement also calls `ensureGroup(jobId)`, which is idempotent since the stream already exists, then starts its own drain loop, logged as the reinforcement drain.

#### Phase 6, concurrent drain, the core of the system

Node A and node B now run the exact same `drain()` loop, pulling from the same `ready` queue. The `crawlers` consumer group guarantees that a given URL is only delivered to one of the two consumers, which is the key to parallelizing work without duplication. Only finalization differs, since node B never finalizes.

The loop continues while `ready` exists and `pages` is below `maxPages`. On every iteration the following happens.

1. Periodic reclaim. If the `reclaim-every-ms` delay has elapsed, the worker reclaims orphaned messages through `XAUTOCLAIM`. An anti resurrection guard first checks that `ready` still exists, so a crawl that was already cleaned up is not accidentally revived.
2. Claiming a batch. The worker claims a batch of URLs through `XREADGROUP crawlers <consumer> > (count = batchSize)`. If the batch is empty and `ready` has disappeared, the frontier was finalized elsewhere and the drain loop stops. If the batch is empty and the crawl is quiescent, meaning `SCARD done >= SCARD seen`, there is nothing left to do and the loop stops. Otherwise the worker waits briefly, the `poll-delay-ms` delay, and tries again.
3. Processing each URL, in `processOne`. First, an immediate abort if the frontier has disappeared, since the crawl was already finalized, to avoid reviving a finished crawl. Then a `robots.txt` and SSRF pre-check. If the domain circuit breaker is open, meaning the site is currently blocking, the `attempts` counter is incremented. Below `maxCbRetries`, the URL is left in the PEL, without `XACK`, for a later retry. Above that threshold, it is abandoned to avoid looping forever. Then navigation and a page snapshot happen through `RetryExecutor`, with success recorded through `CB.recordSuccess` and failure through `CB.recordFailure`. Data extraction follows, and if it is not empty, `HSET records url json` stores it. The `pages` counter is incremented through `INCR`. Child links are harvested, when the depth is below `maxDepth` and the page cap allows it, and each new link is added to the frontier through `expand`. Finally the URL is marked done through `SADD done url`, and the message is acknowledged through `XACK`.

A key correctness guard concerns the `expand` method: it only re-adds a URL to `ready` if that stream still exists. Without this guard, a reinforcement harvesting links after node A has already cleaned up the frontier would recreate the stream through `XADD` and restart a phantom crawl that never stops, with the reinforcement crawling forever.

In parallel, node B runs exactly the same logic on distinct URLs. At the end of its drain loop, it does not finalize. It logs that the reinforcement finished with no finalization, returns an empty result, and acknowledges its own reinforcement message.

#### Phase 7, finalizer election and aggregation, node A

When the crawl ends, exactly one node must aggregate and save the result. Nodes compete for that right through `tryBecomeFinalizer`, which calls `SETNX finalized 1`.

1. If the flag is already set by another node, the call returns false and an empty, delegated result is returned.
2. If the node wins, it calls `EXPIRE finalized` with a six hour TTL, and logs that it was elected finalizer.

The finalizer then reads all records through `HGETALL records`, counts pages through `GET pages`, and determines the final status, `completed` if records exist, `failed` otherwise.

#### Phase 8, persistence, ETL dispatch and cleanup

The finalizer calls `persistAndDispatch(jobId, result)`.

1. The `ScrapeResult` is saved to the database, the job status is updated, and the user is notified.
2. If the status is `completed`, the ETL step is dispatched through `XADD stream:etl.process` with the job id. This dispatch happens outside the persistence transaction, so the ETL worker can never read the job before its `COMPLETED` status has actually been committed.
3. Cleanup runs. `cleanup(jobId)` deletes `ready`, `seen`, `done`, `pages`, `records`, `attempts` and `finalized`.

A data safety point: if persistence fails, cleanup is skipped and the records are preserved in Redis so the crawl can be resumed. State is never destroyed before the result has been saved.

Finally, `runScrape` returns, `JobRunWorker` acknowledges the `stream:job.run` message, and the lease is released.

#### Phase 9, ETL processing, asynchronous

`EtlWorker` reads `stream:etl.process` every second, acquires its own lease (`etl:lease:{jobId}`), then runs the cleaning, normalization and enrichment steps configured for the job, or skips processing if none are configured, and finally acknowledges the message. This worker follows exactly the same robustness pattern as `JobRunWorker`, an atomic lease, PEL and DLQ handling, and a dedicated recovery scheduler.

### Summary of the Flow

In one sentence: a job is enqueued (phases 1 and 2), picked up by a worker that locks it (phases 2 and 3), which opens a shared URL queue and recruits reinforcements (phases 3 to 5); all workers pull from the same queue without duplication (phase 6); exactly one worker aggregates at the end, saves the result and triggers cleanup (phases 7 to 9).

1. Phase 1, enqueue: status becomes `QUEUED` and a message is added to `stream:job.run`, with a rollback if dispatch fails.
2. Phase 2, detection: a one second poll, an atomic lease through `SET NX EX 90`, PEL and DLQ handling.
3. Phase 3, routing: the SSRF check, opening the frontier (`ready` plus its consumer group), and the initial seed.
4. Phase 4, reinforcements: `XADD stream:crawl.join`, once per unit of `parallelism - 1`.
5. Phase 5, join: a reinforcement joins the same crawl, running the reinforcement drain loop.
6. Phase 6, concurrent drain: node A and node B pull from `ready`, extract data, expand the frontier, and mark URLs done.
7. Phase 7, finalizer: `SETNX finalized` elects exactly one node to aggregate the records.
8. Phase 8, persistence and ETL: the result is saved, ETL is dispatched, and cleanup runs, unless persistence failed.
9. Phase 9, ETL: the configured cleaning and enrichment steps run asynchronously.

### Properties Guaranteed by the Architecture

1. Exclusivity. The lease prevents two nodes from running the same job. The consumer group prevents two nodes from processing the same URL.
2. Fault tolerance. A crash releases the lease through expiration. Unacknowledged messages stay in the PEL and are reclaimed through `XAUTOCLAIM`. Beyond three failed attempts, a message moves to the DLQ.
3. No data loss. The frontier is only cleaned up after a successful persistence.
4. Guaranteed termination. Bounds on `maxPages`, on circuit breaker retries, and anti resurrection guards prevent infinite reinforcement loops.
5. Elastic parallelism. The number of reinforcements is driven by the `parallelism` setting, and the crawl also works in solo mode.

### Reliability Fixes Already Integrated

The sequence described above already includes several robustness fixes compared to an earlier version of the system.

1. Enqueue: the `QUEUED` status is rolled back if the Redis dispatch fails, and a relaunch attempt returns a real error if the job is not currently enqueueable.
2. Lease, for both `job.run` and `etl`: a single atomic `SET NX EX` command replaces a previous two step `SETNX` then `EXPIRE`, and renewal verifies ownership first.
3. Distributed ETL: the ETL step is now dispatched for distributed crawls as well, through `persistAndDispatch`, outside the persistence transaction.
4. Circuit breaker: skipped URLs are no longer wrongly marked as done, and retries are bounded by `maxCbRetries`.
5. Attempt counters: the `attempts` key is now scoped per stream, removing collisions between `job.run`, `etl` and `crawl.join`.
6. Crawl join recovery: a dedicated recovery scheduler was added for orphaned reinforcement messages.
7. Infinite reinforcement: `frontierExists` guards on claim and reclaim, and an anti resurrection guard on `expand`, make sure a reinforcement stops as soon as the crawl is finalized.
8. Browser leaks: `browser.start()` was moved inside the try block so `browser.close()` is always guaranteed to run.

### Related Diagrams

The `docs/diagrams/distributed` folder contains PlantUML sequence and component diagrams that mirror the nine phases described here, plus a C4 style set of context, container, component and deployment diagrams.
