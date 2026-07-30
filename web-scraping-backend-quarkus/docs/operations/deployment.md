## Deployment

### Local Development

1. `make dev`, or `./mvnw quarkus:dev`, runs the application in Quarkus development mode against the `%dev` profile, with live reload.
2. `docker-compose --profile dev up -d` builds and runs the development container defined by the `dev` target of the `Dockerfile`, mounting the source tree so changes are picked up without a rebuild.
3. The application expects a running Redis instance and a running PostgreSQL instance. Redis is provided by the `redis` service in `docker-compose.yml`. PostgreSQL, and identity services such as Keycloak, are expected to be available separately, for example through a shared development environment, and are not bundled in this repository's compose file.

### Production Container

1. `docker-compose --profile prod up -d` builds and runs the production container, defined by the `prod` target of the `Dockerfile`. It runs with a read only root filesystem, drops all Linux capabilities, and disables privilege escalation.
2. Every connection detail, database, Redis, Keycloak, session encryption key, is supplied through environment variables, with no development style defaults. See [Configuration](./configuration.md) for the full list.
3. A health check calls `GET /q/health/live` to determine container readiness.

### Building

1. `make build`, or `./mvnw compile`, compiles the application.
2. `make package`, or `./mvnw package -DskipTests`, produces the runnable artifact.
3. `./mvnw package -Dnative -DskipTests` produces a native image build, through the `native` Make target.

### Everyday Make Targets

1. `make test`. Runs the unit test suite.
2. `make format`. Applies code formatting through Spotless, when configured.
3. `make dependency-check`. Runs a dependency vulnerability check.
4. `make up` and `make down`. Start and stop the development compose stack.
5. `make logs`. Tails the development container's logs.

### Related Documents

1. [Configuration](./configuration.md) lists the environment variables read by the profiles described here.
2. [Testing](./testing.md) describes how the test suite runs, separately from a normal deployment.
