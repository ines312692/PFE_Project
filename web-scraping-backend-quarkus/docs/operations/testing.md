## Testing

### Test Types

1. Unit tests, named `*Test.java`. These use JUnit 5 and Mockito, and typically mock every collaborator so a single class's logic can be verified in isolation. They run under the `%test` profile.
2. Integration tests, named `*IT.java`. These use `@QuarkusTest` and RestAssured to exercise a REST controller end to end within a running Quarkus test instance, generally mocking only the service layer immediately behind the controller.
3. Distributed end to end tests, under `src/test/java/com/elyadata/feature/job/e2e`. These run the real Redis Streams dispatch and worker pipeline, with no service layer mocks, driving a job from creation through a real headless browser scrape or crawl against a local HTTP fixture, all the way to a persisted result. See [Distributed Job Processing](../architecture/distributed_jobs.md) for the pipeline these tests exercise.

### The Test Profile

The `%test` profile, defined in `application.yml`, uses an in memory H2 database recreated on every test run, disables Flyway, disables OIDC, and disables the Redis Streams workers by default, so ordinary unit and controller tests are not affected by background polling threads. `quarkus.redis.hosts` is intentionally left unset in this profile, so Quarkus Dev Services starts a short lived Redis container automatically for any test that needs one, which requires Docker to be available.

### Running Unit Tests

`make test`, or `./mvnw test`, runs the unit test suite through the Surefire plugin. Surefire's default include pattern only picks up `*Test.java` classes, so integration tests are not run by this command.

### Running Integration Tests

Integration tests are wired to the Failsafe plugin, which normally runs during `mvn verify`. This project sets `skipITs` to true by default in `pom.xml`, so integration tests are skipped unless that flag is explicitly turned off, for example `./mvnw verify -DskipITs=false`. Passing `-DskipTests=true` at the same time disables Failsafe as well, since both plugins share that property, so it should not be combined with `-DskipITs=false`. The most direct way to run one specific integration test class, regardless of its `*IT` suffix, is to pass it explicitly to Surefire, for example `./mvnw test -Dtest=JobControllerIT`.

### Running the Distributed End to End Tests

The two classes under `job/e2e` also use the `*IT` naming convention, and are best run the same way, for example `./mvnw test -Dtest=DistributedScrapeJobE2EIT` and `./mvnw test -Dtest=DistributedCrawlJobE2EIT`. They need Docker, for the ephemeral Redis instance, and a Playwright Chromium install. They are slower than ordinary tests, since each one drives a real headless browser against a real local HTTP server, and the crawl test in particular needs a generous timeout since it launches two concurrent browser backed drain loops, a principal and a reinforcement, coordinating over the shared Redis frontier described in [Distributed Job Processing](../architecture/distributed_jobs.md).

A dedicated `@TestProfile` re enables `scraper.stream.enabled` only for these two classes, so every other test keeps running with the workers disabled.

### Coverage

Code coverage is measured with the JaCoCo Maven plugin, configured to exclude generated DTOs, mappers, domain models, and JPA entities from the report, and to produce a report during the `verify` phase.
