# Blood donation network

Java 21 and Spring Boot 4.1.2. Four services and a Gateway: REST (`service-donneurs`, `service-demandes`), gRPC (`service-don-stock`), SOAP (`service-cnts`). Pull requests target `develop`. GitHub Actions runs build, test, package and a Compose smoke test.

`service-partenaire` is not part of this design and is not started by Compose.

## Run

```bash
cp .env.example .env
docker compose up -d --build --wait
```

`.env` stays on your machine. The values in `.env.example` are local placeholders.

| Published on the host | URL |
|---|---|
| Gateway | http://localhost:8080 |
| Don & Stock gRPC | localhost:9090 |
| CNTS | http://localhost:8084 |

Donneurs (8081), Demandes (8082) and the Don & Stock Actuator (8083) stay on `bloodnet`. Call them through the Gateway.

Databases are on the `bloodnet` network and are not published on the host. For a local Maven run against Postgres, add the host ports:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

Health: `http://localhost:8080/actuator/health`.

The Compose `demo` profile loads fictional centres, donors, a deferral, free slots for the next 7 days, and sample stock. CNTS stock is loaded by its Flyway seed. None of these rows are real people or real inventory.

Register a donor and create a request through the Gateway:

```bash
curl -fsS -X POST http://localhost:8080/api/v1/donneurs -H "Content-Type: application/json" --data-binary @tests-manuels/donneur.json
curl -fsS -X POST http://localhost:8080/api/v1/demandes -H "Content-Type: application/json" --data-binary @tests-manuels/demande.json
```

Stop and delete data:

```bash
docker compose down -v
```

## Failure demo

With the stack already healthy:

```bash
bash scripts/demo-resilience.sh
```

On Windows: `powershell -File scripts/demo-resilience.ps1`.

The script stops Don & Stock and creates a request (202, `EN_ATTENTE`), recreates CNTS with `CNTS_LATENCY_MS=5000` and then resets it, repeats the same request, and stops then starts CNTS. Repeating `VerifierEtReserver` with one `demande_id` needs `grpcurl` and the gRPC method.

## Tests

```bash
mvn -f service-donneurs/pom.xml -B test
mvn -f service-demandes/pom.xml -B test
mvn -f service-don-stock/pom.xml -B test
mvn -f service-cnts/pom.xml -B test
mvn -f gateway/pom.xml -B test
```

Do not pass `-DskipTests` for this check. Image builds skip tests because Actions already ran them.

## Pull requests

Use a branch `service/<service>/<task-id>-<short-name>`, Conventional Commits, and a pull request into `develop`. The template in `.github/pull_request_template.md` is the Definition of Done. `.github/workflows/ci.yml` builds and tests the services a pull request touches, packages their images, and runs `scripts/smoke.sh` when services, contracts, Compose or the workflow change. Pushes to `main` and `develop` run every service.
