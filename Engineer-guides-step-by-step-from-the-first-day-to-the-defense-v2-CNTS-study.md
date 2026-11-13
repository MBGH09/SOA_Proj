# Engineer guides: step by step from the first day to the defense (v2, CNTS study)

Oct 24, 2026 · @mongi

Each of the 7 engineers gets an ordered list of steps from the first commit to the defense. Every step says why it exists, what to do and how to check that it is finished. The steps follow the study `SOA_Blood_Donation_Project_v2_from_CNTS_study.md` (v2.1: booking from home and the donation certificate). Step IDs are the task IDs of the study (S3-04 and so on); a few steps are split into sub-steps (a, b, c).

## How to work with this guide

- **Days** follow the 3-week plan of the study: Phase 0 (Day 1), Phase 1 (Days 1 to 3), Phase 2 (Days 4 to 10), Phase 3 (Days 11 to 14), Phase 4 (Days 15 to 18), Phase 5 (Days 19 to 21).
- **One step = one GitLab issue = one branch = one Merge Request (MR).** A step is finished only when its Done when line is true and its MR is merged into `develop` after a teammate's review.
- **Contract first.** The first MR of each service contains only its contract file. Code comes after.
- **Why before how.** Read the Why line first: it tells you what breaks if you skip the step.

## 0. Setup every engineer does on Day 1

Why: the same tools and the same Git habits for 7 people avoid most integration problems later.

1. Install JDK 17, Maven 3.9, Docker Desktop (Compose v2), Git, Postman and `curl`. Add Node.js 20 (Students 1 and 7), `grpcurl` (Students 3, 4, 6, 7) and `xmllint` (Student 5).
2. Clone the repository and create your first branch:

```
git clone <repo-url> blood-donation-network
cd blood-donation-network
git checkout develop && git pull
git checkout -b service/<service>/<task-id>-<short-name>
```

3. Commit small, in Conventional Commits format, then push and open an MR to `develop`:

```
git add <files>
git commit -m 'feat(donneurs): add eligibility service'
git push -u origin service/donneurs/s3-04-eligibility
```

4. Attend the kickoff meeting (1 hour, Day 1) after reading sections 0.2 and 0.3.
5. When Student 7 publishes `.env.example`, copy it to `.env`. Never commit `.env`.
6. Every day: a 10-minute check-in (what I did, what I will do, what blocks me). A blocker older than half a day goes to the team leader.

Definition of Done for every step: reviewed MR merged into `develop`, tests pass in CI, contract updated if the API changed, no secret and no hard-coded URL, issue moved to Done.

### 0.1 The project in one table

| Service | Style | Port | Database | Owner |
| --- | --- | --- | --- | --- |
| Frontend (React) | web | 3000 | none | Student 1 |
| Gateway | REST | 8080 | none | Student 2 |
| `service-donneurs` | REST | 8081 | `donneurs-db` | Student 3 |
| `service-don-stock` | gRPC | 9090 (Actuator 8083) | `stock-db` | Student 4 |
| `service-cnts` | SOAP | 8084 (`/ws`, WSDL `/ws/cnts.wsdl`) | `cnts-db` | Student 5 |
| `service-demandes` | REST | 8082 | `demandes-db` | Student 6 |
| Compose, CI, README | none | none | none | Student 7 |

Inter-service calls: (1) Demandes to Don & Stock, gRPC `VerifierEtReserve`r; (2) Donneurs to Don & Stock, gRPC `EnregistrerPrelevement` and `DelivrerCertificat`; (3) Don & Stock to CNTS, SOAP (3 operations); (4, optional) Demandes to Donneurs, REST certificate check.

### 0.2 Who gives what to whom

| Item | Written by | Needed by | Latest day |
| --- | --- | --- | --- |
| JSON of the 8 entities and the reference lists (study section 3.1) | Everyone, at kickoff | Students 1, 3, 6 | Day 1 |
| Compose skeleton, service names, ports, variables | Student 7 | Everyone | Day 1 |
| `donstock.proto` | Student 4 | Students 3, 6, 1 | End of Day 2 |
| `cnts.xsd` | Student 5 | Student 4 | End of Day 2 |
| `donneurs.yaml` | Student 3 | Students 1, 2 | End of Day 2 |
| `demandes.yaml` | Student 6 | Students 1, 2 | End of Day 2 |
| Dockerfile template | Student 7 | Students 1 to 6 | End of Day 2 |
| Resilience4j configuration example | Student 6 | Students 3, 4 | End of Day 2 |

### 0.3 Gaps found in the study (settle them at the kickoff)

I found these while turning the study into steps. Each one blocks a screen or a retry, so decide them before the contracts are merged.

| # | Gap | Proposal | Students |
| --- | --- | --- | --- |
| 1 | The donor cannot list his own appointments and donations (needed for My appointments and My certificates) | Add `donneurId` filter to `GET /rendez-vous` and add `GET /dons?donneurId=` | 1, 3 |
| 2 | The hospital cannot see which certificate numbers were registered for compensation | Return the numbers in `GET /demandes/{id}` or add `GET /demandes/{id}/compensations` | 1, 6 |
| 3 | The place selector needs place names, but only `lieu_id` travels in the stream | Add a unary RPC `ListerLieux` to the proto, or ship a static list of the seeded places | 1, 4, 6 |
| 4 | `ReservationResponse.statut` has no value for delivered pouches or for an order stored as `A_CONFIRMER` | Add `LIVREE` and `EN_ATTENTE_CONFIRMATION` | 4, 6 |
| 5 | `demanderApprovisionnement` has no request id, so a SOAP retry could order twice | Add `demandeId` to the request; CNTS returns the same `reference` for a repeated `demandeId` | 4, 5 |
| 6 | The dashboard offers CNTS and CRTS levels, but the central stock lives in `service-cnts`, which has no read operation | Keep display rows for CNTS and CRTS in `stock-db` (seeded, moved by the simulator, not used for orders) | 1, 4, 5 |
| 7 | The JWT has no claim that tells which donor or hospital the user is | Add claims `donneurId` and `hopitalId`; the Gateway forwards `X-User-Id` and `X-User-Role` to the services | 2, 3, 6 |
| 8 | `CNTS_URL`, `DON_STOCK_GRPC_TARGET` and `DONNEURS_URL` are missing from the variable table (study section 16) | Add them to `.env.example`; add Don & Stock to the users of `RETRY_JOBS_INTERVAL_SEC` | 7 |

## 1. Student 1: Frontend engineer (React)

Mission: one React app on port 3000 with four areas (donor, centre staff, hospital, stock dashboard) that talks only to the Gateway. It starts on mocks, so you never wait for a backend.

You need: the frozen JSON (Day 1), the two OpenAPI files (Day 2), the Gateway URL and CORS rules (Phase 3), the live stock decision (kickoff).

You give: the screens for the demo scenarios D1 to D7.

### Phases 0 and 1: setup and mocks (Days 1 to 3)

**S1-01 Create the project and the mock server.** Why: with mocks you can build every screen from the frozen JSON on Day 1.

1. Create the project and install the tools:

```
npm create vite@latest frontend -- --template react
cd frontend
npm install react-router-dom
npm install -D msw vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
npx msw init public/ --save
```

2. Create the folders `src/api`, `src/mocks`, `src/pages/donor`, `src/pages/staff`, `src/pages/hospital`, `src/pages/stock`, `src/components`, `src/hooks`.
3. In `src/mocks/handlers.js`, write one handler for every route of the OpenAPI tables of the study (sections 6.1 and 6.2): donneurs (with `eligibilite` and `ajournements`), centres and `creneaux`, `rendez-vous`, `dons` (with `entretien`, `prelevement`, `repos`, `certificat`), `certificats/{numero}`, `demandes` (with `compensations`), `appels`, `stock/niveaux`.
4. Mock the error cases too: 400 with field errors, 404, 409 (slot full, not eligible, wrong step, number already used), 202 (`APPROVISIONNEMENT_EN_COURS`, `EN_PENURIE`, `EN_ATTENTE`) and 503.
5. Mirror the demo seed (study section 15): the 5 places, about 20 donors including a 48 kg donor and a donor with an active deferral, free slots for the next 7 days, one finished donation with an issued certificate.

Done when: `npm run dev` starts with mocks and every route of sections 6.1 and 6.2 has a handler.

**S1-02 Build the API layer.** Why: pages never call `fetch` directly, so moving from mocks to the Gateway is one flag.

1. Create `src/api/client.js`. It reads `VITE_API_BASE_URL` (default `http://localhost:8080`) and `VITE_USE_MOCKS`.
2. Write one `request(method, path, body)` function that returns the JSON or throws an `ApiError` with `status`, `message` and `fieldErrors`, built from the common error JSON (timestamp, status, error, message, path).
3. Write one `messageFor(error)` helper that turns 400, 404, 409, 503 into readable text. Every screen uses it.
4. Add one file per resource (`donneurs.js`, `centres.js`, `rendezVous.js`, `dons.js`, `demandes.js`, `appels.js`, `stock.js`) that calls `request`.

Done when: a mocked 404 and a mocked 409 show a readable message in a test.

**S1-03 Donor area.** Why: the donor flow is demo scenario D1 and D2, and the first thing the examiner sees.

1. **Registration form.** Fields: CIN (8 digits), nom, prenom, dateNaissance, poidsKg, groupeSanguin (select with the 8 values), ville, telephone, premierDon (checkbox). Show 400 errors under each field and a clear message for 409 (CIN already registered).
2. **Eligibility result.** Call `GET /donneurs/{id}/eligibilite`. Show `ELIGIBLE`, `AJOURNE_TEMPORAIREMENT` (with the end date) or `NON_ELIGIBLE`, and the list of reasons.
3. **Book from home.** Choose a centre (`GET /centres`, filters `type` and `ville`), choose a day, load `GET /centres/{id}/creneaux?date=` and show the slot cards with the free places. Booking calls `POST /rendez-vous` with `canal = EN_LIGNE`. Handle every 409: slot full, active deferral, not eligible, already one appointment that day.
4. **My appointments.** List them, cancel or move one with `PATCH /rendez-vous/{id}`.
5. **My certificates.** For each finished donation show the status. `EN_PREPARATION` shows the text certificate being prepared and a refresh button. `EMIS` shows the certificate card with a Print button and a PDF button (call `GET /dons/{id}/certificat` with the header `Accept: application/pdf` and download the blob).
6. **Open public appeals.** List `GET /appels` and add a Book a slot button that pre-selects the centre.

Done when: with mocks you can register, see a 48 kg donor refused, book a slot, move it, and open a certificate. Screens 3 and 4 depend on gap 1 of section 0.3.

**S1-04 Centre staff area.** Why: the donation board is the longest flow and must follow the 4 steps exactly.

1. **Today's appointments:** `GET /rendez-vous?centreId=&date=` with a Start visit button (`POST /dons`, with the `rendezVousId` or without it for a walk-in).
2. **Donation board:** four steps, one form per step. Reception (CIN check and health questionnaire), Interview (blood pressure, haemoglobin, decision fit or unfit), Collection (volume 450 ml and duration), Rest (countdown from 15 minutes; the Finish button is enabled when the time is over).
3. Enable only the current step, but always show the server's 409 message if a wrong step is sent (the server decides, not the screen).
4. **Deferral form:** shown when the decision is unfit (`POST /donneurs/{id}/ajournements`: reason, start, end).
5. **Slot management:** create slots with a capacity (`POST /centres/{id}/creneaux`, default capacity 4).
6. Show the certificate status of each visit (`A_EMETTRE` or `EMIS`).

Done when: you can run a full visit on mocks, and an unfit decision ends with the visit marked `REFUSE` and no certificate.

**S1-05 Hospital area.** Why: it is demo scenarios D3, D4, D5 and D7.

1. Request form: motif (`CHIRURGIE`, `URGENCE`, `TRAITEMENT`), groupeSanguin, composant (`CGR`, `PLASMA`, `PLAQUETTES`), quantitePoches (at least 1), urgence (`NORMALE`, `HAUTE`, `CRITIQUE`), patientRef (add the hint: anonymous reference, never a name), compensationDemandee (2 or 3, optional).
2. Request list with a colored badge for each status: `EN_ATTENTE`, `SATISFAITE`, `APPROVISIONNEMENT_EN_COURS`, `EN_PENURIE`, `ANNULEE`.
3. Detail page: delivery status (reference, source centre, estimated minutes) and compensation progress (received out of requested).
4. Compensation entry: a field for the certificate number that calls `POST /demandes/{id}/compensations`. Show 409 (number already used) and a fallback form for the manual correction (`PATCH`) when the check is unavailable.

Done when: the five outcome rows of the study (201, 202 three times, 400) each show the right badge and message.

**S1-06 Stock dashboard.** Why: the live stock is what proves the gRPC stream in the demo.

1. Write the hook `useStockLevels(lieuId)`. For now it returns fake data that changes every few seconds.
2. Build a gauge component: one gauge per blood group (8) and component (3), colored by level `BAS`, `MOYEN`, `HAUT`.
3. Add the place selector (hospital, CRTS, CNTS). It depends on gaps 3 and 6 of section 0.3; use a static list until they are decided.

Done when: 24 gauges update live with fake data and only the hook has to change later.

### Phase 2: quality (Days 4 to 10)

**S1-07 Loading, empty and error states.** Why: the demo includes failures, so the screens must handle them. Add a loading state, an empty state and an error state to every list and form, with specific messages for 400, 404, 409 and 202 (request accepted, waiting). Done when: no screen stays blank on an error.

**S1-08 Unit tests.** Why: tests are graded and protect the forms. Use Vitest and React Testing Library to test the registration form validation, the booking flow (slot full gives a message), the donation board step order, the certificate card (`EN_PREPARATION` and `EMIS`) and the `useStockLevels` hook. Ask Student 7 for a frontend job in the pipeline. Done when: `npx vitest run` is green.

**S1-09 Login and role menus (bonus).** Why: the study defines four roles. After Student 2's login endpoint exists: keep the token in memory, send `Authorization: Bearer <token>`, redirect to the login page on 401, and show menus by role (`DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN`). Interview data is shown only to staff and admin. Done when: a donor cannot see the staff board.

### Phase 3: integration (Days 11 to 14)

**S1-10 Switch from mocks to the Gateway.** Why: integration problems must show up now, not at the defense. Set `VITE_USE_MOCKS=false`, run the whole stack with Compose and test every flow. Fix CORS problems together with Student 2. For every difference between a contract and a real answer, open an issue with the label `contract-gap` for the service owner. Done when: the scenarios D1 to D5 work through port 8080.

**S1-11 Live stock with server-sent events.** Why: the stream is the gRPC streaming feature seen by the user.

1. Open `new EventSource(base + '/api/v1/stock/niveaux/flux')` inside `useStockLevels`.
2. On each message update the matching gauge.
3. On error, close the stream and poll `GET /api/v1/stock/niveaux` every 5 seconds, then try the stream again.
4. Close the stream when the component is removed.

Done when: stopping `service-don-stock` and restarting it brings the gauges back without reloading the page.

### Phases 4 and 5: delivery (Days 15 to 21)

**S1-12 Dockerfile.** Why: Student 7 needs it for the one-command start. A Node stage builds the app, an Nginx stage serves it on port 3000 and sends every path to `index.html`. Vite variables are read at build time, so pass them as build arguments:

```
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_USE_MOCKS=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_USE_MOCKS=$VITE_USE_MOCKS
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 3000
```

The `nginx.conf` listens on 3000 and uses `try_files $uri /index.html`. Done when: `docker build -t frontend .` then `docker run -p 3000:3000 frontend` shows the app.

**S1-13 Manual tests, README, report and demo.** Write the manual test list with expected results, take screenshots, add the frontend section to the README (dev mode, variables, mock mode), write the frontend part of the report, and prepare a 3-minute UI demo.

Defense questions to prepare: why the browser talks only to the Gateway, how the mocks work, what CORS is and how you fixed it, how live stock reaches the screen, why the screen does not enforce the donation steps by itself.

## 2. Student 2: Security and Gateway engineer

Mission: one entry point on port 8080 that routes, secures and protects all traffic, including the live stock stream.

You need: service names and ports (Student 7, Day 1), the two OpenAPI files (Day 2), running Donneurs and Demandes services (Phase 3), the decision on the JWT claims (gap 7).

You give: the routes, the CORS rules and the login endpoint to Student 1.

### Phases 0 and 1: setup (Days 1 to 3)

**S2-01 Create the Gateway project.** Why: everything else is built on this skeleton.

1. Generate the project (Maven, Java 17, Spring Boot 3.3.x, Reactive Gateway, Actuator, OAuth2 Resource Server). The Spring Cloud version must match Boot 3.3 (the 2023.0.x line). If Initializr refuses the version below, use another 3.3.x:

```
curl https://start.spring.io/starter.zip -d type=maven-project -d language=java -d bootVersion=3.3.5 -d javaVersion=17 -d groupId=tn.iit.blood -d artifactId=gateway -d dependencies=cloud-gateway,actuator,oauth2-resource-server -o gateway.zip
unzip gateway.zip -d gateway
```

2. Run it with `cd gateway && ./mvnw spring-boot:run`.

Done when: `curl http://localhost:8080/actuator/health` answers `UP`.

**S2-02 Define the routes from environment variables.** Why: URLs differ between your laptop and Docker, so they cannot be written in the code.

1. In `application.yml` read `DONNEURS_URL` (default `http://service-donneurs:8081`) and `DEMANDES_URL` (default `http://service-demandes:8082`).
2. Routes to Donneurs: `/api/v1/donneurs/**`, `/api/v1/centres/**` (it already covers `/centres/{id}/creneaux`), `/api/v1/rendez-vous/**`, `/api/v1/dons/**`, `/api/v1/certificats/**`.
3. Routes to Demandes: `/api/v1/demandes/**`, `/api/v1/appels/**`, `/api/v1/stock/**` (it includes the server-sent events route).
4. For local work outside Docker, override both URLs with `localhost`.

Done when: a test shows that each prefix reaches the right backend.

**S2-03 CORS.** Why: without it the browser blocks every call from `localhost:3000`.

1. Add a global CORS configuration. Allowed origins come from `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`). Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Allowed headers: Authorization, Content-Type, Accept, X-Request-Id. Exposed headers: Location, X-Request-Id.
2. Test a preflight request:

```
curl -i -X OPTIONS http://localhost:8080/api/v1/donneurs -H 'Origin: http://localhost:3000' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type,authorization'
```

Done when: the answer contains `Access-Control-Allow-Origin: http://localhost:3000`.

**S2-04 Timeouts and the stream route.** Why: every network call needs an explicit timeout, but the live stock stream must stay open.

1. Set the connect and response timeouts of the Gateway HTTP client from variables (for example `GATEWAY_CONNECT_TIMEOUT_MS=2000`, `GATEWAY_RESPONSE_TIMEOUT_MS=10000`). A donation step makes Donneurs call Don & Stock with a 2 s deadline, so keep the response timeout longer than that.
2. For the route `/api/v1/stock/niveaux/flux`, set a negative per-route `response-timeout` in the route metadata (a negative value disables it).
3. Check that no filter buffers or rewrites the response of that route.
4. Test with `curl -N http://localhost:8080/api/v1/stock/niveaux/flux`: events must appear one by one.

Done when: the stream stays open longer than the global timeout while other routes still time out.

**S2-05 Health, logs and request id.** Why: one id per request lets the team follow a call across 4 services in the logs.

1. Expose `/actuator/health` and log to stdout.
2. Write a global filter: if `X-Request-Id` is missing, create a UUID. Add it to the forwarded request and to the response, and write it in every log line.

Done when: a call without the header gets one back, and the same id appears in the backend logs.

**S2-06 Standard JSON errors.** Why: the frontend expects the same error JSON from every layer: timestamp, status, error, message, path.

1. Write an `ErrorWebExceptionHandler` with a high priority.
2. Map: connection refused to 503, response timeout to 504, unknown route to 404.

Done when: stopping a service gives a JSON 503, never an HTML page.

### Phase 2: security and protection (Days 4 to 10)

**S2-07 Login endpoint with JWT (bonus).** Why: the study defines four roles and rule R20 (interview data only for staff).

1. Add variables for four demo users (donor, centre staff, hospital, admin) with their passwords, and `JWT_SECRET` (at least 32 characters, set in `.env`).
2. Add `POST /api/v1/auth/login` in the Gateway. It returns a JWT signed with HS256, valid 1 hour, with the claims `sub`, `role` (`DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN`) and, if gap 7 is accepted, `donneurId` or `hopitalId`.
3. Configure the resource server to check the token with the same secret.
4. Permit `/actuator/health` and `/api/v1/auth/**`. Everything else needs a token, except the public routes of S2-08.

Done when: a call without a token gives a JSON 401 and a call with a token goes through.

**S2-08 Role rules.** Why: the study asks for privacy (R20, R27) and for donors to see only their own data.

| Route | Who may call it |
| --- | --- |
| `GET /centres`, `GET /centres/{id}/creneaux`, `GET /appels` | everyone (public) |
| `GET /certificats/{numero}` | everyone (it returns validity, date and centre only) |
| `POST /donneurs` (registration) | everyone (proposal, so a new donor can register) |
| `/donneurs/{id}/**`, `/rendez-vous/**` | `DONOR` for his own data, `STAFF_CENTRE`, `ADMIN` |
| `POST /dons`, steps `entretien`, `prelevement`, `repos`, `POST /donneurs/{id}/ajournements`, `POST /centres/{id}/creneaux` | `STAFF_CENTRE`, `ADMIN` |
| `GET /dons/{id}` (it contains interview data) | `STAFF_CENTRE`, `ADMIN` |
| `GET /dons/{id}/certificat` | the donor of that donation, `STAFF_CENTRE`, `ADMIN` |
| `POST /demandes`, `PATCH /demandes/{id}`, `POST /demandes/{id}/compensations`, `POST` and `PATCH /appels` | `HOPITAL`, `ADMIN` |
| `GET /stock/**` | `HOPITAL`, `STAFF_CENTRE`, `ADMIN` |

1. Remove any `X-User-Id` and `X-User-Role` header that arrives from outside.
2. After the token is checked, add `X-User-Id` and `X-User-Role` to the forwarded request. Donneurs uses them for the ownership rules (a donor reads only his own donation or certificate), because only Donneurs knows who owns a donation.
3. Return a JSON 401 (no token) or 403 (wrong role).

Done when: a donor token gets 403 on `POST /dons`, and a staff token gets 200.

**S2-09 Rate limiter (optional).** Why: it protects the entry point, and it is a small bonus topic for the defense. Write a filter with an in-memory counter per IP (`RATE_LIMIT_PER_MIN`, for example 120). Skip the health and the stream routes, and answer 429 in the JSON format. Write in the README that an in-memory limit is not shared between several Gateway instances.

### Phase 3: tests and integration (Days 11 to 14)

**S2-10 Tests with a mock backend.** Why: the Gateway has no business logic, so its tests are about routing and security.

1. Use WireMock as the backend and check: forwarding of each prefix, the CORS preflight, 401 without a token, 403 with the wrong role, 200 with the right role, 503 when the backend is stopped, 504 when it is slow, the `X-Request-Id` header, and the pass-through of a streamed response (the first event arrives before the end of the response).
2. Then test with the real services in Compose: one curl per route, with and without a token. Update the routes table in the README.

Done when: the tests pass in CI and the table of routes in the README matches the real behavior.

### Phases 4 and 5: delivery (Days 15 to 21)

**S2-11 Dockerfile, failure demo, README and report.**

1. Build the Dockerfile from Student 7's template and check that every setting comes from environment variables.
2. Failure demo: stop a service and show the clean JSON 503 from the Gateway.
3. README: how to get a token and call the API:

```
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"staff","password":"<password>"}' | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/rendez-vous
```

4. Give Student 7 a Postman variable `{{token}}`, and write the security part of the report (the four roles, the table above).

Defense questions to prepare: reverse proxy versus gateway, how a JWT is checked without calling another service, what CORS protects, why the services are not published directly, why the Gateway removes `X-User-Id` from incoming requests, why the stream route has no response timeout.

## 3. Student 3: Donneurs engineer (REST)

Mission: `service-donneurs` on port 8081. It owns donors, eligibility, deferrals, centres, slots, appointments (from home or at the centre), the 4-step donation visit and the donation certificate. It calls Don & Stock by gRPC (inter-service call 2).

You need: the frozen JSON (Day 1), the database settings (Student 7, Day 1), `donstock.proto` (Student 4, Day 2), the Resilience4j example (Student 6, Day 2).

You give: `donneurs.yaml` to Students 1 and 2 by the end of Day 2.

### Phases 0 and 1: contract, skeleton, schema (Days 1 to 3)

**S3-01 Write the OpenAPI contract first.** Why: Students 1 and 2 work from this file on Day 2, and the Git history must show the contract before the code.

1. Create `contracts/openapi/donneurs.yaml` (OpenAPI 3) with every route of study section 6.1: donors, `eligibilite`, `ajournements`, centres, `creneaux`, `rendez-vous`, `dons` with the three steps, `certificat` and `certificats/{numero}`.
2. Describe the schemas `Donneur`, `DonneurRequest`, `Eligibilite` (statut, reasons, end date), `Ajournement`, `Centre`, `Creneau` (with the remaining places), `RendezVous`, `Don`, the four step requests, `Certificat` (number, national donation number, donor name, masked CIN, date, centre, volume, issuing authority, status `EN_PREPARATION` or `EMIS`), `CertificatVerification` (valid, date, centre) and `Error`.
3. Add every response code with an example, and the gap routes if accepted at the kickoff (`GET /dons?donneurId=`, filter `donneurId` on `GET /rendez-vous`).
4. Check the file: `npx @redocly/cli lint contracts/openapi/donneurs.yaml`.
5. Open an MR with only this file. Students 1, 2 and 6 review it.

Done when: the MR is merged before the end of Day 2.

**S3-02 Create the project.** Why: the layers you choose now decide how easy the rest is.

1. Spring Initializr: Boot 3.3.x, Java 17, Maven, Web, Validation, Data JPA, PostgreSQL driver, Flyway, Actuator. Add springdoc-openapi, a gRPC client starter that works with Boot 3, `resilience4j-spring-boot3`, AOP and an OpenPDF library for the certificate.
2. Configure the protobuf Maven plugin to generate the Java stubs from `../contracts/proto`. Note for Student 7: the Docker build context must be the repository root so that `contracts/` is available.
3. Create the packages `controller`, `service`, `repository`, `model`, `dto`, `exception`, `client`.
4. Read the database from `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

Done when: the application starts against a local PostgreSQL and `/actuator/health` is `UP`.

**S3-03 Flyway schema and seed.** Why: the database is the memory of the service, and the demo needs data.

1. `V1__init.sql` creates: `donneur` (unique `cin`), `ajournement`, `centre`, `creneau`, `rendez_vous`, `don` (with `statut_synchro`, `numero_don_national`, `certificat_statut`, `numero_certificat`, `date_certificat`) and `etape_don` (step data as JSON).
2. Add a rule that one donor has at most one `PLANIFIE` appointment per day (a partial unique index on donor and day).
3. `V2__seed.sql` inserts the centres: CNTS Tunis, CRTS Sfax, CHU Habib Bourguiba Sfax, CHU Hedi Chaker Sfax and a mobile collection with start and end dates.
4. Slots must be relative to today, so create them in a `demo` profile runner: free slots for the next 7 days at the CRTS Sfax, the hospital banks and the mobile collection, with capacity `CRENEAU_CAPACITE_DEFAUT` (4).

Done when: a fresh database starts with the centres and a list of future slots.

### Phase 2: implementation (Days 4 to 10)

**S3-04 Eligibility service (rules R1 to R4).** Why: the first demo scenario D2 shows reasons, so the rules must be exact and testable.

1. Read `AGE_MIN` (18), `AGE_MAX` (70), `AGE_PREMIER_DON_MAX` (65) and `POIDS_MIN_KG` (50) from configuration. Inject a `Clock` so tests can fix today's date.
2. Compute the result: any age or weight problem or a first donation over 65 gives `NON_ELIGIBLE` with the reasons; otherwise an active deferral (today between `dateDebut` and `dateFin`) gives `AJOURNE_TEMPORAIREMENT` with the end date; otherwise `ELIGIBLE`.
3. Write a table-driven unit test: ages 17, 18, 65 and 66 for a first donation, 70 and 71; weights 48, 49 and 50; a deferral that is active, ended or starts tomorrow.

Done when: every row of the table passes.

**S3-05 Donor CRUD.** Why: the other features start from a donor.

1. DTOs with validation: CIN (8 digits), names not blank, `dateNaissance` in the past, `poidsKg` positive, blood group in the enum, phone (8 digits, optional +216).
2. Create returns 201 with a `Location` header. A duplicate CIN returns 409 (check first, and also catch the database constraint error).
3. Search by `groupe` and `ville` with pagination; read, update (PUT) and delete (204).
4. Never write CIN or phone in the logs.

Done when: each code of section 6.1 (201, 400, 404, 409, 204) is returned by a test.

**S3-06 Deferrals, centres and slots.**

1. `POST /donneurs/{id}/ajournements`: reason in the list (`TATOUAGE`, `PIERCING`, `CHIRURGIE`, `ANTIBIOTIQUES`, `INFECTION`), `dateFin` not before `dateDebut`. The doctor chooses the end date because the study gives no durations.
2. `GET /centres` with filters `type` and `ville`.
3. `GET /centres/{id}/creneaux?date=`: slots of that day with the remaining places; the donor sees only slots with a free place.
4. `POST /centres/{id}/creneaux`: create slots with `debut`, `fin` and a capacity (default 4); `fin` after `debut`, and inside the dates of a mobile collection.

Done when: a slot created by staff appears in the donor's list.

**S3-07 Appointments from home or at the centre (R21 to R23).** Why: this is the booking feature added in v2.1.

1. `POST /rendez-vous` with `donneurId`, `creneauId`, `canal` (`EN_LIGNE` or `SUR_PLACE`) and an optional `demandeId`.
2. Steps in the service: load the donor, check eligibility (not eligible gives 409 with the reasons), lock the slot row (`PESSIMISTIC_WRITE`) so two donors cannot take the last place, count the `PLANIFIE` appointments against the capacity (409 when full), check that the donor has no other active appointment that day (409), then save.
3. `PATCH /rendez-vous/{id}`: cancel (`ANNULE`) or move to another slot with the same checks.
4. `GET /rendez-vous` with filters `centreId`, `date`, `statut` (and `donneurId` if gap 1 is accepted).
5. A small scheduled job marks as `ABSENT` the appointments whose slot has ended without a visit.

Done when: a test books the last place, a second booking returns 409, and a cancellation frees the place.

**S3-08 Donation visit state machine (R6 to R9).** Why: the 4 steps come from the study and must never be skipped or repeated.

1. Keep a step table: current step, allowed next step. Every endpoint first checks the table, and a wrong order returns 409.
2. `POST /dons` (step 1, reception): `donneurId`, `centreId`, optional `rendezVousId`, the CIN typed by the staff (it must match the donor) and the questionnaire answers. Check eligibility again.
3. `POST /dons/{id}/entretien` (step 2): blood pressure, haemoglobin, decision. Unfit ends the visit as `REFUSE` (R7) and can add a deferral. Fit moves to the collection.
4. `POST /dons/{id}/prelevement` (step 3): volume must be `VOLUME_PRELEVEMENT_ML` (450), then duration. Save the step, then call Don & Stock (S3-09).
5. `POST /dons/{id}/repos` (step 4): refuse with 409 if less than `REPOS_MIN_MINUTES` (15) passed since the end of the collection. When accepted the visit becomes `TERMINE` and the appointment becomes `TERMINE`.
6. Interview data stays in `etape_don`, and `GET /dons/{id}` hides it from donors (R20).

Done when: a test tries every wrong order and gets 409, and a full visit ends in `TERMINE`.

**S3-09 gRPC calls, retry jobs and the certificate (inter-service call 2).** Why: this connects your service to the stock and to the CNTS system, and it proves two protocols in the demo.

1. **Client.** Build the channel from `DON_STOCK_GRPC_TARGET` (default `service-don-stock:9090`, plaintext inside the Docker network) and give every call a deadline of `GRPC_DEADLINE_MS` (2000). Wrap each call in a Resilience4j circuit breaker (use Student 6's configuration example). Work against the fake gRPC server until Student 4's service exists.
2. **Declare the pouch (after step 3).** Call `EnregistrerPrelevement` with `don_id`, `donneur_id`, `centre_id`, `groupe_sanguin` (read from the donor), `volume_ml` and `date_prelevement`. On success store `numeroDonNational` and `statutSynchro = SYNCHRONISE`. On `UNAVAILABLE`, `DEADLINE_EXCEEDED` or an open breaker, the visit continues and the don stays `A_SYNCHRONISER`.
3. **Retry job.** A job every `RETRY_JOBS_INTERVAL_SEC` (60) calls `EnregistrerPrelevement` again for every `A_SYNCHRONISER` donation. The call is idempotent by `don_id`, so no pouch is created twice.
4. **Certificate (after `TERMINE`).** Call `DelivrerCertificat` with `don_id`, `numero_don_national`, `donneur_ref`, `centre_id`, `date_don`. Send a reference that has no health data (the donor id is enough; the name stays in your database). Only call it when the donation is `SYNCHRONISE`. Success sets `certificatStatut = EMIS` and stores `numeroCertificat` and `dateCertificat`. Failure keeps `A_EMETTRE`, and the same retry job issues it later.
5. **Read the certificate.** `GET /dons/{id}/certificat`: 404 if unknown, 409 if the visit is not finished, otherwise JSON with status `EN_PREPARATION` (no number yet) or `EMIS`. With `Accept: application/pdf` return a PDF made with OpenPDF. The donor name comes from your database, the number from the CNTS system. A donor can read only his own certificate (check `X-User-Id`); staff can read any. Show no health data (R27).
6. **Verify a certificate.** `GET /certificats/{numero}` is public and returns only valid, date and centre. Student 6 calls it for the compensation check.

Done when: with the fake server stopped a visit still ends, the don is `A_SYNCHRONISER`, and after restarting the fake server the job fixes it and the certificate is issued.

**S3-10 Central error handling.** Why: all services must answer errors in the same JSON.

1. `@RestControllerAdvice` returns timestamp, status, error, message, path.
2. Map validation errors to 400 with the list of fields, not found to 404, business conflicts (CIN, slot full, wrong step, not eligible) to 409, gRPC failures to the right code, and anything else to 500 without a stack trace.

Done when: no endpoint can return an HTML page or a stack trace.

### Phase 3: tests (Days 11 to 14)

**S3-11 Unit and integration tests.** Why: tests are graded, and the state machine is easy to break.

1. Unit tests (JUnit 5, Mockito): eligibility, state machine (every wrong order), slot capacity, certificate rules (never for `REFUSE`, never before `TERMINE`).
2. Integration tests with Testcontainers PostgreSQL (`postgres:16-alpine`) and the fake gRPC server: a full donation from reception to certificate, slot full gives 409, fake server unavailable gives `A_SYNCHRONISER`, certificate failure gives `A_EMETTRE` then `EMIS` after the retry job, repeated failures open the circuit breaker.
3. Test with the real Don & Stock service together with Student 4 and fix any field name difference through an issue.

Done when: `mvn verify` is green in CI.

### Phases 4 and 5: delivery (Days 15 to 21)

**S3-12 Dockerfile, Postman, README and report.**

1. Dockerfile from Student 7's template (build context is the repository root). Check that the service starts in Compose after its database and Don & Stock are healthy.
2. Postman requests and curl examples for all routes, including a full donation and the certificate download.
3. README section and the REST part of the report: resource design, status codes, validation, error format, the state machine and the certificate flow.

Defense questions to prepare: why these URIs and verbs, PUT versus PATCH, what each status code means, how the last place of a slot is protected (pessimistic lock), what happens when Don & Stock is down during a donation, why the certificate has no health data, why `don_id` makes retries safe.

## 4. Student 4: Don & Stock engineer (gRPC)

Mission: `service-don-stock` (gRPC port 9090, Actuator port 8083). It owns the stock per place, the reservations, the collected pouches and the live stock stream. It is called by Demandes (call 1) and by Donneurs (call 2), and it calls the CNTS service by SOAP (call 3).

You need: the frozen Protobuf messages (kickoff), `cnts.xsd` (Student 5, Day 2), the database settings (Student 7), the decisions on gaps 3, 4, 5 and 6.

You give: `donstock.proto` by the end of Day 2. Students 1, 3 and 6 wait for it.

### Phases 0 and 1: contract, skeleton, schema (Days 1 to 3)

**S4-01 Write `donstock.proto` first.** Why: three engineers generate code from this file, and the Git history must show it before your code.

1. Create `contracts/proto/donstock/v1/donstock.proto` with `package donstock.v1`, and the Java options `java_package` and `java_multiple_files`.
2. Define the enums: blood group (8 values), component (`CGR`, `PLASMA`, `PLAQUETTES`), urgency, reservation status (`RESERVEE_STOCK_LOCAL`, `APPROVISIONNEMENT_COMMANDE`, `STOCK_INSUFFISANT_PARTOUT` and, if gap 4 is accepted, `LIVREE` and `EN_ATTENTE_CONFIRMATION`) and level (`BAS`, `MOYEN`, `HAUT`). Every enum starts with an `UNSPECIFIED` value at 0.
3. Define the 5 RPCs and the messages of study section 6.3: `VerifierEtReserver`, `ConsulterReservation`, `EnregistrerPrelevement`, `DelivrerCertificat` (unary) and `SuivreNiveauxStock` (server streaming).
4. Write comments: what each field means, which field is the idempotency key (`demande_id`, `don_id`) and which gRPC status each RPC can return.
5. Never reuse a field number; use `reserved` for removed fields.
6. Check the syntax: `protoc --proto_path=contracts/proto --java_out=/tmp contracts/proto/donstock/v1/donstock.proto`.
7. Open an MR with only this file. Students 3 and 6 review it, Student 1 reads it.

Done when: the MR is merged by the end of Day 2.

**S4-02 Create the project.** Why: the starter versions must work together with Boot 3, and a wrong choice costs a day.

1. Spring Initializr: Boot 3.3.x, Java 17, Maven, Web (only for Actuator), Data JPA, PostgreSQL, Flyway, Actuator, Spring Web Services, AOP. Add `resilience4j-spring-boot3`.
2. Add a gRPC server starter that is compatible with Boot 3 (check its compatibility table before you choose it) and set the gRPC port to 9090. Set `server.port=8083` for Actuator.
3. Configure the protobuf Maven plugin with the grpc-java plugin, reading `../contracts/proto`. The Docker build context will be the repository root.
4. Prepare the JAXB plugin for the SOAP client classes (`jakarta.*`), to be used in S4-12.

Done when: the service starts and `curl http://localhost:8083/actuator/health` answers `UP`.

**S4-03 Flyway schema and seed.** Why: the demo scenarios D3 to D5 need places and stock with a few groups intentionally low.

1. Tables: `lieu` (id, nom, type, region, `stock_local`); `stock_niveau` (place, group, component, `disponible`, `reserve`, `en_qualification`, `en_transit`, `seuil_alerte`; key = place + group + component); `reservation` (`demande_id` unique, place, group, component, quantity, source, status, reference, source centre, ETA); `prelevement` (`don_id` unique, donor id, centre id, group, volume, national donation number, status, certificate number, issue date).
2. Seed the places: CNTS Tunis, CRTS Sfax, CHU Habib Bourguiba Sfax and CHU Hedi Chaker Sfax (hospital banks, `stock_local = true`), Clinique exemple Sfax (`CLINIQUE_SANS_STOCK`, `stock_local = false`) and a mobile collection attached to the CRTS of its region.
3. Seed the levels for 8 groups x 3 components at the CNTS, the CRTS and each hospital bank (gap 6 for the CNTS and CRTS rows), with alert thresholds. Make a few groups low on purpose, for example O negative at one hospital.

Done when: each place with stock has 24 rows and the low groups exist.

### Phase 2: gRPC server (Days 4 to 10)

**S4-04 `VerifierEtReserver` (local path).** Why: it is the main business flow of the project, and the study requires that two hospitals never take the same pouches.

1. Validate: quantity below 1, unknown group, component or urgency give `INVALID_ARGUMENT`; an unknown place gives `NOT_FOUND`.
2. Idempotency (R18): insert the reservation row first with the unique `demande_id`. If it already exists, return its stored answer.
3. Local path (R13, place has a bank): in one transaction lock the `stock_niveau` row (`SELECT ... FOR UPDATE`). If `disponible` is enough, decrease it, increase `reserve`, set the reservation to `RESERVEE_STOCK_LOCAL` and commit.
4. Otherwise go to the central path (S4-05). Never hold the database lock during the SOAP call.
5. After the commit, publish a stock-change event for the stream (S4-10).

Done when: a test sends two parallel requests for the last pouches and only one is reserved, and the same `demande_id` twice returns the same answer.

**S4-05 Central path (R12, call 3).** Why: a clinic without a bank always uses it, and a hospital uses it when its stock is short.

1. Call SOAP `demanderApprovisionnement` with the hospital id, the region of the place, group, component, quantity, urgency and the `demandeId` (gap 5).
2. If the accepted quantity equals the requested one (v2 treats a central order as all or nothing): reservation `APPROVISIONNEMENT_COMMANDE` with the reference, the source centre and the ETA in minutes, and increase `en_transit` at the hospital place.
3. On `StockCentralInsuffisantFault`: reservation `STOCK_INSUFFISANT_PARTOUT`.
4. If the CNTS service stays slow or down after the retries: store the reservation as `A_CONFIRMER` and answer `UNAVAILABLE` (proposal: Demandes then keeps the request `EN_ATTENTE`; agree it with Student 6). A small job re-sends the `A_CONFIRMER` orders every `RETRY_JOBS_INTERVAL_SEC`, with the same `demandeId`.

Done when: tests cover accepted, insufficient everywhere and CNTS down.

**S4-06 Delivery simulator.** Why: without it a central order never becomes stock, and D4 cannot end.

1. A scheduled job takes the `APPROVISIONNEMENT_COMMANDE` reservations older than `DELAI_LIVRAISON_SEC` (30 in demo).
2. It decreases `en_transit` and puts the pouches in `reserve` for that request, so that nobody else can take them (this refines the study line that says `enTransit` to `disponible`).
3. It sets the status `LIVREE` (gap 4) and publishes a stream message.

Done when: 30 seconds after an order, the dashboard changes and `ConsulterReservation` answers `LIVREE`.

**S4-07 `EnregistrerPrelevement` (call 2).** Why: it turns a collected pouch into stock and a national donation number.

1. Validate: the volume must be `VOLUME_PRELEVEMENT_ML` (450), the group known; otherwise `INVALID_ARGUMENT`.
2. Idempotency by `don_id`: if the row exists return its answer. If its status is `A_DECLARER` (an earlier SOAP failure), retry the SOAP call now.
3. Insert the row as `A_DECLARER` and call SOAP `enregistrerPrelevement`.
4. On success save the national number, set `QUALIFICATION_EN_COURS`, and for each planned component (CGR, PLASMA, PLAQUETTES) add 1 to `en_qualification` at the collecting place (a mobile collection feeds the CRTS of its region).
5. On SOAP failure the row stays `A_DECLARER` and you answer `UNAVAILABLE`. Donneurs' retry job calls again.

Done when: the same `don_id` twice never creates two pouches.

**S4-08 Qualification simulator.** Why: the study says the stock rises only after qualification. A job takes the `QUALIFICATION_EN_COURS` pouches older than `DELAI_QUALIFICATION_SEC` (20 in demo), moves each component from `en_qualification` to `disponible`, sets the status `QUALIFIE` and publishes the stream messages. Optional: an expiry job with a configurable shelf life per component.

**S4-09 `ConsulterReservation` and `DelivrerCertificat`.**

1. `ConsulterReservation(demande_id)`: return the stored answer with the current status, or `NOT_FOUND`. Student 6's reconciliation job uses it.
2. `DelivrerCertificat`: find the pouch by `don_id`. If it has no national number, answer `FAILED_PRECONDITION`. If the certificate is already stored, return it (R25). Otherwise call SOAP `delivrerCertificatDon`, store the certificate number and the date on the row, and answer. On SOAP failure answer `UNAVAILABLE` (Donneurs retries). On `CertificatRefuseFault` answer `FAILED_PRECONDITION`.

Done when: a repeated `don_id` returns the same certificate number.

**S4-10 `SuivreNiveauxStock` (server streaming).** Why: it is the streaming RPC required by the assignment, and the live dashboard depends on it.

1. Keep a registry of subscribers with their optional filters (`lieu_id`, `groupe_sanguin`).
2. On subscription, send the current rows first, then each change.
3. Publish changes with an after-commit event listener after every stock update (reservation, delivery, qualification, expiry).
4. Compute `niveau` from `seuil_alerte`: `BAS` at or under the threshold, `MOYEN` up to twice the threshold, `HAUT` above (the study gives no thresholds, so keep them configurable).
5. Remove a subscriber when the client cancels (cancel handler on the server call observer).

Done when: a client receives the first snapshot and then several changes, and a cancel frees the subscriber.

**S4-11 Status mapping in one place.** Put all gRPC status decisions in one exception advice: `INVALID_ARGUMENT` (quantity below 1, unknown group), `NOT_FOUND` (unknown place or reservation), `FAILED_PRECONDITION` (inconsistent state), `UNAVAILABLE` and `DEADLINE_EXCEEDED` (technical).

### Phases 2 and 3: SOAP client (Days 5 to 12)

**S4-12 SOAP client with resilience.** Why: it is call 3 and the core of the resilience demo.

1. When Student 5 has merged the XSD, generate the client classes. Configure a `WebServiceTemplate` with a Jaxb2 marshaller, the URL from `CNTS_URL` (default `http://service-cnts:8084/ws`) and the timeouts from `SOAP_TIMEOUT_MS` (3000).
2. Add Resilience4j: a retry (3 attempts, exponential backoff from 500 ms) and a circuit breaker. All three operations are safe to retry because they carry `demandeId`, `donIdLocal` or `don_id`.
3. Fallbacks: an order is stored as `A_CONFIRMER`, a declaration as `A_DECLARER`, a certificate is left to the caller's retry.
4. Map the Faults: `PrelevementInvalideFault` to `INVALID_ARGUMENT`, `StockCentralInsuffisantFault` to `STOCK_INSUFFISANT_PARTOUT`, `CertificatRefuseFault` to `FAILED_PRECONDITION`. A Fault is never retried; only timeouts and unavailability are.
5. Reuse the Resilience4j example from Student 6.

Done when: with the CNTS service slowed down, the logs show 3 attempts and the fallback status.

### Phase 3: tests (Days 11 to 14)

**S4-13 Tests.**

1. Unit tests for the stock logic, the status mapping and the stream level computation.
2. In-process gRPC tests (in-process server and channel): every RPC, every status, the stream receives several messages then cancels.
3. `MockWebServiceServer` tests for the SOAP client: success, Fault and timeout.
4. A concurrency test with parallel reservations, and idempotency tests for `demande_id` and `don_id`.
5. Run the real service with Students 6 and 3 and compare statuses and field names with the contract.

Done when: `mvn verify` is green in CI.

### Phases 4 and 5: delivery (Days 15 to 21)

**S4-14 Reflection, Dockerfile, examples, report.**

1. Enable gRPC reflection in the `demo` profile.
2. Dockerfile from Student 7's template; expose 9090 and 8083; the Compose healthcheck uses the Actuator port 8083.
3. Add `grpcurl` examples to the README:

```
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext -d '{"demandeId":"d-1","lieuId":"<place-id>","groupeSanguin":"O_NEG","composant":"CGR","quantite":3,"urgence":"HAUTE"}' localhost:9090 donstock.v1.DonStockService/VerifierEtReserver
grpcurl -plaintext -d '{}' localhost:9090 donstock.v1.DonStockService/SuivreNiveauxStock
```

4. Failure demo: stop `service-cnts`, call the RPC and show the retries in the logs and the fallback status.
5. Write the gRPC part of the report: why gRPC for internal calls, streaming, deadlines, status codes, contract evolution, idempotency.

Defense questions to prepare: unary versus streaming, deadline versus timeout, why Protobuf is fast, how you keep the contract compatible, what a retry can break and how `demande_id` and `don_id` prevent it, why the SOAP call is never inside a database transaction.

## 5. Student 5: CNTS engineer (SOAP)

Mission: `service-cnts` on port 8084. It simulates the legacy national information system (CNTS and CRTS central stock) with three operations: declare a collection, supply a hospital order, issue the donation certificate. Contract first with XSD and WSDL.

You need: the frozen SOAP names (kickoff), the database settings (Student 7), the decisions on gaps 5 and 6.

You give: `cnts.xsd` by the end of Day 2. Student 4 waits for it.

### Phases 0 and 1: contract, skeleton, schema (Days 1 to 3)

**S5-01 Write the XSD first.** Why: the grading grid checks that the XSD was written before the code, and the Git history is the proof.

1. Create `contracts/wsdl-xsd/cnts.xsd` with the target namespace agreed at the kickoff (for example `http://iit.tn/soa/cnts`) and qualified elements.
2. Add the simple types: blood group, component, urgency.
3. Add request and response elements for the three operations:
   - `enregistrerPrelevement`: `donIdLocal`, `groupeSanguin`, `volumeMl`, `centreId`, `datePrelevement` to `numeroDonNational`, `statut`, `composantsPrevus`.
   - `demanderApprovisionnement`: `hopitalId`, `region`, `groupeSanguin`, `composant`, `quantite`, `urgence` and `demandeId` (gap 5) to `reference`, `quantiteAccordee`, `centreSource`, `delaiMinutes`, `statut`.
   - `delivrerCertificatDon`: `donIdLocal`, `numeroDonNational`, `donneurRef`, `centreId`, `dateDon` to `numeroCertificat`, `dateEmission`, `statut`.
4. Add the detail elements of the three Faults: `PrelevementInvalideFault`, `StockCentralInsuffisantFault`, `CertificatRefuseFault`.
5. Check the file and a sample payload (without the SOAP envelope):

```
xmllint --noout contracts/wsdl-xsd/cnts.xsd
xmllint --noout --schema contracts/wsdl-xsd/cnts.xsd tests-manuels/enregistrerPrelevement-payload.xml
```

6. Open an MR with only this file, before any code. Student 4 reviews it.

Done when: the MR is merged by the end of Day 2.

**S5-02 Create the project.** Why: the generated classes must use `jakarta.*`, otherwise nothing compiles with Boot 3.

1. Spring Initializr: Boot 3.3.x, Java 17, Maven, Spring Web Services, Data JPA, PostgreSQL, Flyway, Actuator. Add `wsdl4j`.
2. Add a JAXB Maven plugin that generates classes from the XSD with `jakarta.xml.bind` (for example `org.jvnet.jaxb:jaxb-maven-plugin` 4.x).
3. Check after the build:

```
grep -R 'javax.xml.bind' target/generated-sources && echo 'FAIL: javax found' || echo 'OK: jakarta only'
```

Done when: the build is green and the check prints OK.

**S5-03 Spring-WS configuration and the generated WSDL.** Why: the WSDL is generated from the XSD, never written by hand.

1. Add `@EnableWs`, a `MessageDispatcherServlet` mapped to `/ws/*` with `setTransformWsdlLocations(true)`, and a `DefaultWsdl11Definition` bean named `cnts` with the port type `CntsPort`, the location `/ws` and your namespace.
2. Start the service, then save and commit the WSDL:

```
curl -s http://localhost:8084/ws/cnts.wsdl -o contracts/wsdl-xsd/cnts.wsdl
```

Done when: the WSDL lists the three operations and is committed.

**S5-04 Flyway schema and seed.** Why: the supply operation needs central stock, and the demo needs a finished donation with a certificate.

1. Tables: `stock_central` (centre, group, component, quantity), `prelevement_declare` (national number, local donation id, group, volume, centre, status), `approvisionnement` (reference, request id, hospital, group, component, requested and granted quantity, source, delay, status) and `certificat` (certificate number, national number, local donation id, centre, date, issue date, status).
2. Add unique constraints on the local donation id (declared collection and certificate) and on the request id (supply order), and sequences for the numbers.
3. Seed the central stock for the CNTS Tunis and for each CRTS by region (Sfax at least), 8 groups x 3 components.
4. Seed one declared collection and one certificate that match the finished donation seeded by Student 3 (same numbers).

Done when: a fresh database starts with stock and one valid certificate.

### Phase 2: implementation (Days 4 to 10)

**S5-05 `enregistrerPrelevement` and `delivrerCertificatDon`.** Why: they are the declaration and the proof of a donation (R8, R24, R25).

1. `enregistrerPrelevement`: reject an unknown group, a volume different from `VOLUME_PRELEVEMENT_ML` (450) or a future date with `PrelevementInvalideFault`. If the `donIdLocal` already exists, return the same national number. Otherwise create a national number from a sequence (for example `DN-2026-000001`), save, and answer `QUALIFICATION_EN_COURS` with the planned components CGR, PLASMA, PLAQUETTES.
2. `delivrerCertificatDon`: check that a declared collection exists with that national number and `donIdLocal`, otherwise throw `CertificatRefuseFault`. If the same `donIdLocal` already has a certificate, return it. Otherwise create a unique number (for example `CERT-2026-000001`), status `VALIDE`, issue date today.

Done when: each rule has a unit test and a repeated call returns the same number.

**S5-06 `demanderApprovisionnement`.** Why: it is the central supply path used in D4 and D5 (R14, R15).

1. Idempotency by `demandeId`: a repeated request returns the same `reference`.
2. Try the CRTS of the request region first, then the CNTS.
3. All or nothing (v2 decision): take the first source that can supply the whole quantity with an atomic update (`UPDATE stock_central SET quantite = quantite - :q WHERE ... AND quantite >= :q`) and check that one row was changed.
4. Answer a reference (for example `APP-2026-000001`), the granted quantity, the source centre and the delay in minutes. Use configurable delays, shorter for `CRITIQUE`.
5. If no source can supply, throw `StockCentralInsuffisantFault`.

Done when: tests cover CRTS first, CNTS second, nobody, critical delay and the repeated request.

**S5-07 SOAP Faults.** Create exception classes with `@SoapFault` (fault code, fault string) and a detail element defined in the XSD. Check the real XML of each Fault with curl.

**S5-08 XSD validation.** Add a `PayloadValidatingInterceptor` using the XSD for requests and responses, so invalid XML gets a Fault before it reaches your code.

**S5-09 Simulation switches.** Add an endpoint interceptor that waits `CNTS_LATENCY_MS` (default 0) and fails a share `CNTS_FAIL_RATE` (0 to 1, default 0) of the calls with a SOAP server fault. Log every simulated failure. Students 4 and 7 use these switches in the demo.

**S5-10 Refill job (optional).** A scheduled job slowly refills the central stock so the demo never runs dry.

### Phase 3: tests (Days 11 to 14)

**S5-11 Tests.**

1. Unit tests for every rule above.
2. `MockWebServiceClient` tests: a valid call for each operation, each Fault, an invalid XML message, a repeated identifier, and a check that the WSDL is served and lists the three operations.
3. Test your service with Student 4's client in Compose.

Done when: `mvn verify` is green in CI.

### Phases 4 and 5: delivery (Days 15 to 21)

**S5-12 Samples, Dockerfile, README and report.**

1. Put sample SOAP requests in `tests-manuels/` and call them:

```
curl -s -H 'Content-Type: text/xml' -d @tests-manuels/enregistrerPrelevement.xml http://localhost:8084/ws
```

2. Dockerfile from Student 7's template with an Actuator healthcheck.
3. README section (operations, Faults, the two simulation variables) and the SOAP part of the report: contract-first steps, XSD to WSDL, Faults, why SOAP fits a legacy national system, and the Git proof that the XSD came first.

Defense questions to prepare: what an XSD and a WSDL are and how they relate, a SOAP Fault versus an HTTP error, why contract-first, how the namespace works, how to show in Git that the XSD came first, why the supply operation needs a request id, why the stock update is atomic.

## 6. Student 6: Demandes engineer (REST)

Mission: `service-demandes` on port 8082. It owns hospital requests, public appeals, compensation and the REST bridge for the live stock. It calls Don & Stock by gRPC (call 1) and, optionally, Donneurs by REST (call 4).

You need: `donstock.proto` (Student 4, Day 2), the frozen JSON (Day 1), the database settings (Student 7), the decisions on gaps 2, 3, 4 and 7.

You give: `demandes.yaml` to Students 1 and 2, and the Resilience4j configuration example to Students 3 and 4, both by the end of Day 2.

### Phases 0 and 1: contract, skeleton, fake server (Days 1 to 3)

**S6-01 Write the OpenAPI contract first.** Why: Students 1 and 2 build from it, and the Git history must show the contract first.

1. Create `contracts/openapi/demandes.yaml` with the routes of study section 6.2: `POST` and `GET /demandes`, `GET` and `PATCH /demandes/{id}`, `POST /demandes/{id}/compensations`, `POST` and `GET /appels`, `PATCH /appels/{id}`, `GET /stock/niveaux`, and `GET /stock/niveaux/flux` (response type `text/event-stream`).
2. Describe the schemas `Demande`, `DemandeRequest`, `AppelDon`, `Compensation`, `NiveauStock`, `Error`, and the outcome table (201, 202, 400) with examples. Add the certificate numbers if gap 2 is accepted.
3. Check the file with `npx @redocly/cli lint contracts/openapi/demandes.yaml`.
4. Open an MR with only this file. Students 1 and 2 review it.

Done when: the MR is merged by the end of Day 2.

**S6-02 Create the project.** Why: it has the most dependencies, so start it early.

1. Spring Initializr: Boot 3.3.x, Java 17, Maven, Web, Validation, Data JPA, PostgreSQL, Flyway, Actuator, AOP. Add springdoc-openapi, a gRPC client starter that works with Boot 3 and `resilience4j-spring-boot3`.
2. Configure the protobuf Maven plugin to generate the stubs from `../contracts/proto`.
3. Variables: `DON_STOCK_GRPC_TARGET` (default `service-don-stock:9090`), `GRPC_DEADLINE_MS` (2000), `DONNEURS_URL` (default `http://service-donneurs:8081`).

Done when: the application starts and `/actuator/health` is `UP`.

**S6-03 Build a fake Don & Stock server.** Why: you must not wait for Student 4 to test your flow.

1. Write a class that extends the generated server base class, used in tests and in a `mock` profile on port 9090.
2. Let a property or a request header choose the behavior: reserved locally, central order accepted, nobody has the blood, technical error, slow (longer than the deadline), `ConsulterReservation` answering `LIVREE`, and a stream that sends a sample `NiveauStock` every second.

Done when: each behavior can be switched on and each outcome row of the study can be reproduced.

**S6-04 Flyway schema.** Why: it fixes what the service remembers.

1. Tables `demande`, `appel_don` and `compensation` with the fields of study section 3.2 (the certificate number is unique in `compensation`).
2. There is no patient name column, only `patient_ref` (R19).

Done when: the application starts on an empty database.

### Phase 2: implementation (Days 4 to 10)

**S6-05 Request creation.** Why: it is the main flow of the project and the first inter-service call.

1. Validate: quantity at least 1 (R11), known group, component, motif and urgency, `compensationDemandee` between 2 and 3 when present (R17), and a `patientRef` made of letters and digits only.
2. First transaction: save the request as `EN_ATTENTE` and commit.
3. Call `VerifierEtReserver` with the request id as `demande_id`, the hospital as `lieu_id`, and a deadline of `GRPC_DEADLINE_MS`. No database transaction is open during this call.
4. Second transaction: save the new status and the `referenceApprovisionnement`.

Done when: the five outcome rows of the study give the right status.

**S6-06 Outcome mapping and errors.**

| Don & Stock answer | Request status | HTTP |
| --- | --- | --- |
| `RESERVEE_STOCK_LOCAL` | `SATISFAITE` | 201 |
| `APPROVISIONNEMENT_COMMANDE` | `APPROVISIONNEMENT_EN_COURS` | 202 |
| `STOCK_INSUFFISANT_PARTOUT` | `EN_PENURIE`, appeal opened | 202 |
| technical failure or open breaker | `EN_ATTENTE` | 202 |
| invalid data | not saved | 400 |

Add `@RestControllerAdvice` with the common error JSON (timestamp, status, error, message, path), and put this table in the README.

**S6-07 Public appeals.** Why: a shortage must reach the donors.

1. When a request ends in `EN_PENURIE`, open an appeal for that group and component. If an open appeal already exists for the same group and component, reuse it.
2. `POST /appels` opens one by hand, `PATCH /appels/{id}` closes it, `GET /appels?statut=OUVERT` is public.

Done when: D5 creates an appeal that the donor area can list.

**S6-08 Compensation (optional call 4).** Why: it links the certificate to the family donors asked by the hospital (2 or 3 per patient).

1. `POST /demandes/{id}/compensations` with a certificate number. Return 404 for an unknown request, 400 if the request asks for no compensation.
2. Check the number with `GET /certificats/{numero}` on Donneurs (a direct call with `DONNEURS_URL`, timeout 2 s, Resilience4j circuit breaker). An invalid number gives 400.
3. The `compensation` table has a unique number, so a number used twice gives 409 (R26). Each valid number increases `compensationRecue`.
4. If Donneurs is down, return 503 and let staff correct the counter with `PATCH /demandes/{id}`.

Done when: a number counted once works, the same number twice gives 409, and with Donneurs stopped the manual correction works.

**S6-09 gRPC client and the shared Resilience4j example.** Why: the three services must use the same protection pattern, and the others need your example on Day 2.

1. Build the channel from `DON_STOCK_GRPC_TARGET` and give every call a deadline of `GRPC_DEADLINE_MS`.
2. Wrap the call in a circuit breaker named `donStock`. A first configuration to share on Day 2:

```
resilience4j.circuitbreaker.instances.donStock.sliding-window-size=10
resilience4j.circuitbreaker.instances.donStock.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.donStock.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.donStock.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.donStock.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.donStock.automatic-transition-from-open-to-half-open-enabled=true
```

3. The fallback runs on `UNAVAILABLE`, `DEADLINE_EXCEEDED` and an open breaker. It keeps the request `EN_ATTENTE` and answers 202.
4. Give the example to Students 3 and 4 on Day 2.

Done when: five failures in a row open the breaker and the sixth call is answered immediately by the fallback.

**S6-10 Reconciliation job.** Why: a request left in `EN_ATTENTE` or in transit must end by itself.

1. A job every `RETRY_JOBS_INTERVAL_SEC` (60) takes the requests in `EN_ATTENTE` and `APPROVISIONNEMENT_EN_COURS`.
2. It calls `ConsulterReservation`. `NOT_FOUND` means the stock service never saw the request: send `VerifierEtReserver` again (same `demande_id`). `LIVREE` moves the request to `SATISFAITE`.
3. It closes an open appeal when the stock level of that group and component is back above its threshold.

Done when: after restarting a stopped Don & Stock, waiting requests reach `SATISFAITE`.

**S6-11 Stock bridge for the browser.** Why: the browser cannot speak gRPC, and the stream has no end-of-snapshot marker.

1. At startup open `SuivreNiveauxStock` and keep the latest `NiveauStock` per place, group and component in a small in-memory cache, rebuilt from the stream. Reconnect with a backoff if Don & Stock restarts.
2. `GET /api/v1/stock/niveaux` reads the cache (filters `lieuId`, `groupe`) and returns 503 if the cache is empty and the stream is down.
3. `GET /api/v1/stock/niveaux/flux` returns server-sent events (`SseEmitter`): the current cache first, then each change. Send a comment line every 15 seconds so no proxy closes the connection.

Done when: `curl -N http://localhost:8082/api/v1/stock/niveaux/flux` prints events as the simulators change the stock.

### Phase 3: tests and integration (Days 11 to 14)

**S6-12 Tests.**

1. Unit tests with a mocked stock client: every outcome and error mapping, the 2 to 3 compensation rule, the same number twice.
2. Integration tests (MockMvc or RestAssured, Testcontainers PostgreSQL, the fake gRPC server): the 201 and 202 paths, an appeal after `EN_PENURIE`, repeated failures that open the circuit breaker, the reconciliation job.

**S6-13 Real service, Dockerfile, README, report.**

1. Replace the fake server by the real service in Compose and compare field names and statuses with the contract (open issues for Student 4).
2. Dockerfile from Student 7's template. Expose the breaker state: `management.endpoints.web.exposure.include=health,circuitbreakers`, then check `/actuator/circuitbreakers`.
3. Failure demo: `docker compose stop service-don-stock`, create a request, show 202 and `EN_ATTENTE`, show the open breaker, restart the service and show the recovery.
4. Postman requests, the README section, and the report part on integration and resilience.

Defense questions to prepare: deadline versus timeout, the three states of a circuit breaker, why the fallback keeps the request instead of failing, what a retry can break and how `demande_id` prevents it, why no database transaction is open during the gRPC call, why the stock bridge keeps a cache.

## 7. Student 7: DevOps and cloud-native engineer

Mission: the whole application starts with one command, `docker compose up`, on any machine that has Docker, and the GitLab pipeline proves it.

You need: each service's port, variables and health path (from the owners), the answers to gap 8.

You give: the repository, the Compose skeleton and the table of names and ports on Day 1; the Dockerfile template on Day 2.

### Phase 0: Day 1

**S7-01 Create the GitLab project.** Why: nobody can start before the repository, the board and the rules exist.

1. Create a private project `blood-donation-network`. Add the 7 members (the leader as Maintainer, the others as Developers) and the teacher as Reporter.
2. Protect `main` and `develop`: no direct push, merge only through an MR. Enable Pipelines must succeed before merge. Require one approval if your GitLab plan allows it; otherwise write the rule in the MR template.
3. Create the issue board with the labels `Backlog`, `In progress`, `In review` and `Done`, one label per service and the label `contract-gap`, and one milestone per phase with its dates.
4. Add `.gitlab/merge_request_templates/default.md` with the Definition of Done checklist.
5. Create one issue per task ID of the study (S1-01 to S7-13) by importing a CSV (Issues, Import CSV, columns title and description), and assign each by name.

Done when: a direct push to `main` is refused and the board shows every task.

**S7-02 Commit the repository skeleton.**

```
mkdir -p gateway service-donneurs service-demandes service-don-stock service-cnts frontend contracts/openapi contracts/proto/donstock/v1 contracts/wsdl-xsd tests-manuels scripts
touch README.md .env.example docker-compose.yml .gitlab-ci.yml
```

Add a `.gitignore` with `.env`, `target/`, `node_modules/`, `.idea/`, `*.log` and `.m2/`, and a README stub. Commit and push to `develop`.

**S7-03 Write the first `docker-compose.yml`.** Why: four independent databases and one network are the base of the architecture.

1. Create one network `bloodnet` and four PostgreSQL services (`donneurs-db`, `demandes-db`, `stock-db`, `cnts-db`). Each one has its own named volume and a healthcheck. Do not publish the database ports. Example for the first one:

```
services:
  donneurs-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${DONNEURS_DB_NAME:-donneursdb}
    volumes:
      - donneurs-data:/var/lib/postgresql/data
    networks: [bloodnet]
    healthcheck:
      test: pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}
      interval: 5s
      timeout: 3s
      retries: 10
```

2. Repeat it for the other three databases and declare the four volumes and the network at the bottom.

Done when: `docker compose up -d` starts four healthy databases.

**S7-04 Write `.env.example` and the names table.** Why: all variables must be in one place, and the team needs the DNS names on Day 1.

1. Put in `.env.example`: `POSTGRES_USER`, `POSTGRES_PASSWORD`, the four database names, every variable of study section 16 (`AGE_MIN`, `AGE_MAX`, `AGE_PREMIER_DON_MAX`, `POIDS_MIN_KG`, `VOLUME_PRELEVEMENT_ML`, `REPOS_MIN_MINUTES`, `CRENEAU_CAPACITE_DEFAUT`, `RETRY_JOBS_INTERVAL_SEC`, `COMPENSATION_MIN`, `COMPENSATION_MAX`, `GRPC_DEADLINE_MS`, `SOAP_TIMEOUT_MS`, `DELAI_QUALIFICATION_SEC`, `DELAI_LIVRAISON_SEC`, `CNTS_LATENCY_MS`, `CNTS_FAIL_RATE`, `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`) and the missing ones of gap 8 (`DONNEURS_URL`, `DEMANDES_URL`, `DON_STOCK_GRPC_TARGET`, `CNTS_URL`, the Gateway timeouts, `VITE_API_BASE_URL`, `VITE_USE_MOCKS`, the demo users).
2. Before the end of Day 1, send the team a table of service names (they are the DNS names), ports and database URLs.

Done when: every variable read by a service exists in `.env.example`.

### Phase 1: Days 2 and 3

**S7-05 Dockerfile template and reviews.** Why: six Dockerfiles that look alike are easier to review and keep images small.

1. The build context is the repository root, because services read `contracts/`. Template (replace `<service>` and the port):

```
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY contracts ./contracts
COPY <service>/pom.xml <service>/pom.xml
RUN mvn -f <service>/pom.xml -B dependency:go-offline
COPY <service>/src <service>/src
RUN mvn -f <service>/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/<service>/target/*.jar app.jar
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -qO- http://localhost:<actuator-port>/actuator/health | grep -q UP
ENTRYPOINT ["java", "-jar", "app.jar"]
```

2. In Compose, build each service with `context: .` and `dockerfile: <service>/Dockerfile`.
3. Add a `.dockerignore`, share the template on Day 2 and review each owner's Dockerfile.

Done when: one service builds with the template and its health check turns healthy.

**S7-06 Pipeline skeleton.** Why: the pipeline is graded and protects `develop`.

1. Create `.gitlab-ci.yml` with the stages `build`, `test`, `package`, `smoke-test`, a Maven cache and one test job per service with `rules:changes` (the service folder and `contracts/`). Docker-in-Docker is needed for Testcontainers. Example for one service:

```
stages: [build, test, package, smoke-test]
variables:
  MAVEN_OPTS: '-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository'
cache:
  paths: [.m2/repository]
test:donneurs:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  services: [docker:27-dind]
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ''
    TESTCONTAINERS_HOST_OVERRIDE: docker
  script: mvn -f service-donneurs/pom.xml -B verify
  rules:
    - changes: [service-donneurs/**/*, contracts/**/*]
  artifacts:
    reports:
      junit: service-donneurs/target/surefire-reports/TEST-*.xml
```

2. Copy the job for each service and add a frontend job with Student 1.

Done when: a merge request runs only the jobs of the services it changed.

### Phase 2: Days 4 to 10

**S7-07 Add every service to Compose.** Why: the start order decides whether the demo starts cleanly.

1. Add each service when its owner is ready, with its environment variables, `restart: unless-stopped` and `depends_on` with `condition: service_healthy`.
2. Start order: each service after its own database; `service-cnts` first, then `service-don-stock`, then `service-donneurs` and `service-demandes`, then the Gateway, then the frontend.
3. Publish only the ports needed: 8080 (Gateway), 3000 (frontend), and for demos 9090 (`grpcurl`) and 8084 (SOAP curl).

Done when: `docker compose up --build` reaches a state where every container is healthy.

**S7-08 Smoke-test job.** Why: it proves the one-command start on every merge.

1. Script (inside Docker-in-Docker the services are on the host `docker`, on your laptop they are on `localhost`):

```
docker compose up -d --build --wait
curl -fsS http://docker:8080/actuator/health
curl -fsS -X POST http://docker:8080/api/v1/donneurs -H 'Content-Type: application/json' -d @tests-manuels/donneur.json
curl -fsS -X POST http://docker:8080/api/v1/demandes -H 'Content-Type: application/json' -d @tests-manuels/demande.json
docker compose down -v
```

2. If the JWT bonus is on, get a token first.

Done when: the job is green on `develop`.

**S7-09 Demo data profile.** Why: the demo scenarios need the seed data of study section 15 in all four databases.

1. Set `SPRING_PROFILES_ACTIVE=demo` in Compose and ask each owner to load their part: Student 3 the centres, donors and slots (relative to today), Student 4 the places and levels, Student 5 the central stock.
2. Agree on one finished donation seen by three databases (Donneurs, Don & Stock, CNTS): the same donation id, national donation number and certificate number everywhere, so that the certificate download and the verification work in the demo.

Done when: after `docker compose up` the demo scenarios D1 to D4 can start without typing any data.

**S7-10 Fresh-machine test and secrets check.**

1. On a machine with only Docker: clone, `cp .env.example .env`, `docker compose up --build`. Note the time and every manual step, then remove each manual step.
2. Secrets: no password in the repository or the image layers, `.env` ignored, CI variables masked. Optional scan: `gitleaks detect --source . -v`.

Done when: a teammate follows the README word by word and the application starts.

### Phase 4: Days 15 to 18

**S7-11 Failure demo scripts and a reliable pipeline.** Why: scenario D6 is graded live, so it must work every time.

Write `scripts/demo-resilience.sh` with a pause between scenarios (`read -p 'next? '`) and run it three times:

1. Stock service down: `docker compose stop service-don-stock`, create a request (202, `EN_ATTENTE`), repeat until the breaker opens (`/actuator/circuitbreakers` on Demandes), `docker compose start service-don-stock`, wait and show `SATISFAITE`.
2. CNTS slow: `CNTS_LATENCY_MS=5000 docker compose up -d service-cnts`, create a central order, show the retries and the fallback in the logs, then reset the variable.
3. Same call twice: send the same `demandeId` twice with `grpcurl` and show the same answer.
4. CNTS down at the end of a visit: `docker compose stop service-cnts`, finish a donation (certificate shown as being prepared), start the service and show the certificate issued by the retry job.

Also fix flaky jobs and keep the pipeline under about 10 minutes.

Done when: the script works three times in a row.

### Phase 5: Days 19 to 21

**S7-12 Final README, Postman, release and report.**

1. Merge each owner's section into the README and run every command exactly as written (ports, URLs, examples). Add the pipeline badge and make it green.
2. Collect the Postman collection from all owners.
3. After the code freeze, tag the release and test `docker compose up` on two machines (for example Linux and Windows):

```
git tag -a v1.0.0 -m 'release v1.0.0'
git push origin v1.0.0
```

4. Write the Docker, CI and cloud-native parts of the report and plan the demo script with timings.

**S7-13 Optional bonus.** Add RabbitMQ (healthcheck `rabbitmq-diagnostics -q ping`) with the event `RendezVousPlanifie` published by Donneurs and a small consumer that prints a simulated SMS, or add OpenTelemetry with Jaeger for tracing.

Defense questions to prepare: what multi-stage builds give, how the start order works with healthchecks, how the services find each other (Docker DNS), what stays out of the repository, why the build context is the repository root, how the pipeline proves the one-command start.

## 8. Final phase for everyone (Days 19 to 21)

**F-01 Report (8 to 12 pages, PDF).** Each student writes about their own service (Student 1 frontend and user flows, 2 Gateway and security, 3 REST and the certificate flow, 4 gRPC and stock, 5 SOAP, 6 integration and resilience, 7 architecture overview, Docker and CI). The team leader writes the introduction, the study summary, the table of protocol choices, the difficulties and the limits.

**F-02 Fresh-machine test.** A teammate who did not write the code clones the repository on a machine with only Docker and follows the README word by word. Fix every gap.

**F-03 Git history check.** Each member runs `git log --author='<name>'` and checks that their work is visible.

**F-04 Two timed rehearsals** of the 15-minute demo (D1 to D7 below). Each person presents their own part, one person drives the screen, and a screen recording is kept as a backup.

**F-05 Questions practice.** Each person writes five likely questions and answers about their own service, then the team swaps and quizzes each other. Individual answers are graded.

### Demo scenarios and who makes them work

| Scenario | What it shows | Students |
| --- | --- | --- |
| D1 | Donor registers, books from home, the 4 steps, certificate download, stock rises after qualification | 1, 2, 3, 4, 5 |
| D2 | A 48 kg donor refused, a donor with a recent tattoo deferred | 1, 3 |
| D3 | Hospital with a bank asks for 3 pouches: local reservation, 201 | 1, 4, 6 |
| D4 | Clinic without a bank: central order to the CRTS of Sfax, 202, delivery, `SATISFAITE` | 1, 4, 5, 6 |
| D5 | Nobody has the blood: `EN_PENURIE`, public appeal, a donor books | 1, 3, 4, 6 |
| D6 | Failures: stop Don & Stock, slow CNTS, same call twice, CNTS down at the end of a visit | 4, 5, 6, 7 |
| D7 | A family certificate number registered on a request, counted once | 1, 3, 6 |

## 9. Team leader steps

1. Day 1: run the kickoff meeting. Freeze the JSON of the 8 entities and the reference lists, the Protobuf messages, the SOAP XML, and decide the 8 gaps of section 0.3 and the six decisions of the study (stream by SSE or polling, urgency and status lists, compensation by certificate check, where the PDF is made, all-or-nothing supply, SOAP namespace).
2. Review every contract MR first, because it blocks other people.
3. At the end of each phase, check its exit criteria before the next one starts.
4. Keep the board up to date and remove any blocker within half a day.
5. At the end of each phase and at the code freeze, check the project against the grading grid.
6. Organize the two rehearsals and the final report.
