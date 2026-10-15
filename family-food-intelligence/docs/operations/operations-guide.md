# Operations Guide

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §43/44/46/57/82). Verbatim content, reorganized. Concrete metric set: to be authored alongside Phase 6 (see `docs/engineering/implementation-plan.md` Phase 6).

---

## Observability

Implement structured logs and metrics. Monitor: API latency · error rates · database errors · OCR failures · AI failures · product lookup failures · search failures · authentication events · consent changes · recommendation failures · data quality issues. Never place sensitive health/child data in ordinary logs.

---

## Error Handling

Errors should be understandable · actionable · non-destructive · safely retryable. Do not show users stack traces, SQL errors, internal service names, secrets, implementation details.

Bad: `NullPointerException at ProductScoreService.java:83`

Good: "We couldn't analyze this product yet. Try capturing the nutrition label again."

---

## Offline / Degraded States

Gracefully handle: slow network · disconnected device · OCR unavailable · AI provider unavailable · database unavailable · external product API unavailable. The application should clearly distinguish "Product not found" from "Service temporarily unavailable." Do not interpret service failures as absence of data.

---

## Analytics

Product analytics must measure behavior without unnecessarily collecting sensitive information.

- **Discovery:** search attempts · successful product lookup · scan success · OCR success
- **Decision:** product detail views · comparison · alternative views · alternative selection
- **Behavioral value:** recommendation acceptance · product substitution · repeat use · household retention

The critical north-star concept is **Better Food Decisions**. A useful derived metric may be **Healthy Decision / Recommendation Acceptance Rate**. Do not optimize for vanity metrics such as total scans, total AI messages, raw page views, without connecting them to user value.

---

## Cost Control

AI and OCR calls can become expensive. Do not call AI repeatedly for the same deterministic information. Use: caching · normalized product data · deterministic rules · provider abstraction · asynchronous processing · retry limits · usage monitoring. Track costs per important workflow.

---

## Caching (D-056)

**Choosing a provider.** Set `CACHE_TYPE`:

| Value | Where entries live | Use it when |
|---|---|---|
| `caffeine` (default) | Each instance's heap | One instance |
| `redis` | Shared Redis (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`) | More than one instance |
| `none` | Nowhere; every read goes to the database | Ruling the cache in or out of a bug |

Locally, `docker compose --profile redis up -d` starts Redis, then run the backend with `CACHE_TYPE=redis` — or `scripts\start-dev.ps1 -WithRedis` does both in one step (README.md, "Running it").

**What is cached.** Public catalogue data only:

| Cache | Content | TTL |
|---|---|---|
| `catalog.product` | Product detail by id; evicted when its image changes | 30 min |
| `catalog.categories` | The category list | 1 h |

Nothing about a person is cached (`CachingBoundaryTest` enforces this).

**Keys.** `ffi:<cache>:<version>::<id>`, for example `ffi:catalog.product:3fa1b2c4::0b6e…`.
- The version segment changes whenever the cached type or the applied schema version changes. Keys from before a deploy or a migration are never read again; they just expire.
- To purge by hand: `redis-cli --scan --pattern 'ffi:catalog.product:*' | xargs redis-cli del`. Never run `KEYS` on a shared Redis.

**When Redis is down.** The application keeps working from the database.
- The first failing request waits at most `spring.data.redis.timeout` (500 ms) per cache operation.
- The instance then skips Redis for `familyfood.cache.retry-after` (30 s) and tries again.
- `/actuator/health` reports `DEGRADED` with HTTP 200. Liveness and readiness stay `UP`, so an orchestrator does not restart or drain the instance over the cache.
- One `cache_unavailable` WARN is logged per 30 s window.

Evictions lost during an outage are not replayed. An affected entry is served until its TTL expires (at most 30 minutes). If that matters, purge the affected cache by hand (see Keys above).

**Metrics** (Micrometer):
- `cache.gets{cache,result=hit|miss}`, `cache.puts`, `cache.evictions`;
- `familyfood.cache.errors{cache,operation}`;
- `familyfood.cache.bypassed` (1 while Redis is skipped);
- `lettuce.command.completion`.

`/actuator/prometheus` is denied by `SecurityConfig` today, so exposing these is an open operations decision (D-056).

**Running Redis in production.**
- Use it as a cache only: no persistence, a `maxmemory` limit with `allkeys-lru`.
- Require AUTH, and use TLS wherever the network is shared.
- Keep it reachable only from the application.
- Write access to Redis amounts to write access to the product facts users see, including allergens.

---

## Concurrency (D-057)

**Threads.**
- Requests and `@Scheduled` jobs run on virtual threads.
- Argon2 hashing runs on its own pool of max(2, cores/2) platform threads, named `argon2-*`.
- Concurrency is bounded by:
  - the database pool, `DB_POOL_MAX` (default 10), for database work;
  - Tomcat's `server.tomcat.max-connections` (default 8192) for requests in flight.
- Size the database pool to Postgres (instances × pool < `max_connections`, with headroom), never to request volume.

**What to watch.**

| Metric | Healthy | If not |
|---|---|---|
| `hikaricp.connections.pending` | Near 0 | Requests are queueing for a connection |
| `hikaricp.connections.timeout` | 0 | Something holds connections too long. Known holders: sign-in and registration (Argon2 inside a transaction) and image attach (S3 upload inside a transaction) |
| `jvm.threads.virtual.pinned` | Near 0 | A virtual thread blocked while pinned to its carrier; JFR's `jdk.VirtualThreadPinned` event shows where |
| `jvm.threads.virtual.submit.failed` | 0 | The virtual-thread scheduler rejected work |

---

## JVM runtime and diagnostics

**Runtime.**
- Run on Java 21, a current update.
- On the Windows development machine, 21.0.7 (Oracle and JetBrains builds) died with `0xC0000005` and no hs_err file whenever ~15 requests arrived at once. Temurin 21.0.12 survived the same burst.
- Whether virtual threads are involved in that crash is unproven. The one experiment that would tell is the same burst on 21.0.7 with `spring.threads.virtual.enabled=false`.

**Development machine: native memory exhaustion.**
- All seven `hs_err_pid*.log` files from 2026-09-05 to 09-09 are native allocation failures, not Java heap OOMs. They are C2 compiler arenas, G1 heap reservation and metaspace commit.
- In each one the system page file had 0 MB available, and three coincide with Windows "low virtual memory" events (Resource-Exhaustion-Detector, event 2004).
- The JVMs themselves were small (heap ~150 MB of an ergonomic 1.88 GB). The rest of the machine was full: the WSL/Docker VM, IDEs, local database servers and parallel sessions, with C: at 100% so the page file could not grow.
- The crashed JVMs are victims. Free memory or disk before blaming the application.

**`mvn spring-boot:run` flags (pom property `spring-boot.run.jvmArguments`).** Replace them with `-Dspring-boot.run.jvmArguments=...`. All output goes under `target/` and is bounded.
- `-Xmx1g`, plus `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=target`;
- `-XX:ErrorFile=target/hs_err_pid%p.log`;
- rotated GC and safepoint logs, `target/gc.log`, at most 5 × 10 MB;
- continuous JFR, `target/ffi.jfr` on exit, at most 2 h or 100 MB;
- `-XX:NativeMemoryTracking=summary`.

Test forks write crash logs to `target/` too.

**Production flags.** Choose these per deployment; nothing in the repository sets them.
- **Heap:** `-XX:MaxRAMPercentage=75` in a container, or an explicit `-Xmx`.
- **Heap dumps:** `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=<restricted volume>`. **A heap dump contains personal and health data.** Store it with production-data access control, and delete it after analysis.
- **Fail fast:** `-XX:+ExitOnOutOfMemoryError`, so the orchestrator replaces a JVM that has run out of heap.
- **Logs:** `-XX:ErrorFile=<log volume>/hs_err_pid%p.log`, and `-Xlog:gc*:file=<log volume>/gc.log:time,uptime,level,tags:filecount=5,filesize=20m`.
- **Optional:** `-XX:StartFlightRecording=maxage=6h,maxsize=250m,dumponexit=true,filename=<log volume>/`. Continuous JFR costs about 1%.

**Thread dumps with virtual threads.** `jstack` and `jcmd <pid> Thread.print` show carrier threads only. To include virtual threads, use `jcmd <pid> Thread.dump_to_file -format=json <file>`. For native memory: `jcmd <pid> VM.native_memory summary` (requires `NativeMemoryTracking`).
