## Review guidelines

Reference files for EVERY pull request review. Read both before reviewing. If anything below differs from them, the files win.

- `docs/review/SOA_Blood_Donation_Project_v2_from_CNTS_study.md`: the study v2.1 (contracts, flows, business rules R1-R27, resilience, config variables, grading checklist)
- `docs/review/engineer-guides.md`: step-by-step guides per engineer (step IDs, Definition of Done, ownership)

Start the review summary with: `Guides used: SOA study v2.1 + engineer guides`.
Cite the rule in each finding (R18, Definition of Done, contract-first, ...). Ignore code style and formatting.

### P0: must fix before merge

- A change that breaks a business rule (R1-R27, listed below).
- An API change with no update to its contract file, or code merged before its contract.
- A secret in the diff: `.env`, `JWT_SECRET`, database passwords, API keys.
- A hard-coded URL, host, port or credential. All configuration comes from environment variables.
- A call between services other than the 4 allowed ones, or access to another service's database (one database per service).
- A network call with no timeout or deadline.
- A retry on a call that is not idempotent.

### P1: should fix

- A business number hard-coded instead of read from its environment variable.
- A new environment variable missing from `.env.example`.
- Missing circuit breaker or fallback where the resilience table below requires one.
- State kept in memory instead of the database.
- Missing tests: unit tests, plus one integration test per style (REST, gRPC, SOAP).
- Dockerfile that is not multi-stage, or a service without a healthcheck.
- Branch not named `service/<service>/<task-id>-<short-name>`, commits not in Conventional Commits format, or PR not targeting `develop`.
- Work outside the step of the guide named by the task ID, or a PR that is not "one step = one issue = one branch = one PR".
- A PR that implements one of the 8 gaps in guide section 0.3 differently from the agreed proposal.

### Services, protocols, contracts

| Service | Style | Port | Contract file |
|---|---|---|---|
| Gateway | REST | 8080 | none |
| `service-donneurs` | REST | 8081 | `contracts/openapi/donneurs.yaml` |
| `service-demandes` | REST | 8082 | `contracts/openapi/demandes.yaml` |
| `service-don-stock` | gRPC | 9090 (Actuator 8083) | `contracts/proto/donstock/v1/donstock.proto` |
| `service-cnts` | SOAP | 8084 | `contracts/wsdl-xsd/cnts.xsd` (WSDL generated from it) |

Allowed inter-service calls only:
1. Demandes to Don & Stock, gRPC `VerifierEtReserver`
2. Donneurs to Don & Stock, gRPC `EnregistrerPrelevement` and `DelivrerCertificat`
3. Don & Stock to CNTS, SOAP (`demanderApprovisionnement`, `enregistrerPrelevement`, `delivrerCertificatDon`)
4. (optional) Demandes to Donneurs, REST certificate check

Per style:
- REST: URIs under `/api/v1`, correct verbs and status codes, input validation, errors through `@RestControllerAdvice`, OpenAPI up to date.
- gRPC: versioned `.proto`, correct status codes (`INVALID_ARGUMENT`), a deadline on every client call.
- SOAP: XSD written before code, Faults for errors (`PrelevementInvalideFault`, `CertificatRefuseFault`).
- A contract change needs approval from every consumer of that contract.

### Business rules

- Eligibility: R1 age 18-70; R2 first donation after 65 is `NON_ELIGIBLE` (`PREMIER_DON_APRES_65`); R3 weight at least 50 kg; R4 active deferral blocks booking and donation (`AJOURNE_TEMPORAIREMENT` until `dateFin`); R5 CIN unique (409).
- Donation visit: R6 steps in order `ACCUEIL`, `ENTRETIEN_MEDICAL`, `PRELEVEMENT`, `REPOS` else 409; R7 unfit decision ends the visit as `REFUSE`, no collection; R8 volume exactly 450 ml else `PrelevementInvalideFault`; R9 visit ends only after 15 minutes of rest else 409.
- Booking: R21 a donor can book any open slot from home (`canal = EN_LIGNE`); R22 a slot never exceeds its capacity (409); R23 one active appointment per donor per day (409).
- Certificate: R24 only after `TERMINE`, never for `REFUSE`; R25 one donation = one certificate, same `don_id` returns the same one; R26 a certificate number counts once for compensation (409); R27 no health data on the certificate.
- Requests: R11 quantity at least 1 (400); R16 nobody can supply: `EN_PENURIE`, answer 202, public appeal opens; R17 `compensationDemandee` between 2 and 3; R19 patient identified only by an anonymous reference, no name stored.
- Stock and supply: R12 hospital without a blood bank always goes to the central system; R13 hospital with a bank uses local stock first; R14 central supply tries the regional CRTS first, then the CNTS; R15 `CRITIQUE` request means emergency supply with a shorter `delaiMinutes`; R18 same `demande_id` or `don_id` never creates a second effect.
- All services: R10 blood group and component must be known values (400 or `INVALID_ARGUMENT`); R20 interview data visible only to centre staff and admin (403 otherwise).

### Resilience

| Call | Required protection | Required fallback |
|---|---|---|
| Demandes to Don & Stock | deadline `GRPC_DEADLINE_MS` (2000), circuit breaker | request stays `EN_ATTENTE`, answer 202, reconciliation job |
| Donneurs to Don & Stock | deadline, circuit breaker, idempotency by `don_id` | don `A_SYNCHRONISER` or certificate `A_EMETTRE`, retry job |
| Don & Stock to CNTS | timeout `SOAP_TIMEOUT_MS` (3000), 3 retries with exponential backoff from 500 ms, circuit breaker | `A_CONFIRMER`, `A_DECLARER` or `A_EMETTRE`, retry job |
| Gateway to services | connect and response timeouts | JSON 503 or 504 error |

Retries only on idempotent calls.

### Configuration variables (never hard-code these values)

`AGE_MIN`, `AGE_MAX`, `AGE_PREMIER_DON_MAX`, `POIDS_MIN_KG`, `VOLUME_PRELEVEMENT_ML`, `REPOS_MIN_MINUTES`, `CRENEAU_CAPACITE_DEFAUT`, `RETRY_JOBS_INTERVAL_SEC`, `COMPENSATION_MIN`, `COMPENSATION_MAX`, `GRPC_DEADLINE_MS`, `SOAP_TIMEOUT_MS`, `CNTS_LATENCY_MS`, `CNTS_FAIL_RATE`, `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.

### Definition of Done (every PR)

Reviewed PR merged into `develop`, tests pass in CI, contract updated if the API changed, no secret and no hard-coded URL.
