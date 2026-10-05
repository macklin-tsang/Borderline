# Plan: Borderline v2 — Port of Vancouver anchorage tracker

**Status**: approved 2026-10-04 · **Complexity**: Medium (~21–28 h) · **Branch**: `v2` (from `main`, merge when done)
**How we work**: you type the code, Claude guides and reviews each phase. Claude uses ponytail (simplest thing that works) when writing or reviewing code. Check boxes off as phases finish.

## What we're building
- AISStream → backend keeps one connection open for a box around English Bay, Burrard Inlet and Indian Arm.
- PostGIS decides whether a stopped ship is inside one of the port's ~28 deep-sea anchorages (centre point + swing radius).
- Each stay is one *visit* row: `arrived_at`, `departed_at`. Dwell time = departed − arrived.
- One page: Leaflet map with anchorage circles and live ships (poll every 10 s) plus a dwell-statistics table.
- Choices: Java 21 + Spring Boot 4, JdbcClient + plain SQL (no JPA), plain HTML/JS, no login, runs locally with docker compose, plus an optional local k3d cluster. No cloud (AWS lives in a different project).

## Architecture
```
AISStream (wss, binary frames of JSON)
   │  1 connection · subscription sent within 3 s · reconnect with backoff + jitter · watchdog
   ▼
AisStreamClient ─► PositionReport.parse()   drops lat 91 / lon 181 / sog 102.3 ("not available")
                        ▼
              AnchorageTracker.handle()     @Transactional, strictly one message at a time
                1. upsert vessels row (latest position)
                2. which anchorage?  ST_DWithin(geography)
                3. open/close anchorage_visits row
              @Scheduled closeStaleVisits()  every 10 min
                        ▼
                  PostGIS (3 tables)
                        ▼
ApiController   GET /api/anchorages · /api/vessels · /api/stats?days=30
                        ▼
static/index.html + app.js   Leaflet; polls vessels every 10 s, stats every 60 s
```
Spring Boot serves the page itself: one origin, so no CORS config and no nginx.

## Final layout (5 Java files, Spring Boot project at the repo root)
```
.github/workflows/ci.yml               runs ./mvnw verify on every push
src/main/java/com/borderline/
  BorderlineApplication.java           main + @EnableScheduling
  AisStreamClient.java                 connect, subscribe, buffer binary frames, reconnect, watchdog
  PositionReport.java                  record + parse/validate one AIS message
  AnchorageTracker.java                detection state machine + stale cleanup (JdbcClient + SQL)
  ApiController.java                   3 GET endpoints + response records
src/main/resources/
  application.yml
  db/migration/V1__schema.sql          3 tables + indexes
  db/migration/V2__anchorages.sql      anchorages from the Port Information Guide
  static/index.html, static/app.js
src/test/java/com/borderline/
  PositionReportTest.java              unit test: parsing and "not available" values
  AnchorageTrackerIT.java              Testcontainers, real PostGIS: arrive → wait → depart, stats
src/test/resources/position-report.json  a real captured message
k8s/postgres.yaml, k8s/app.yaml        local k3d cluster (Phase 8)
pom.xml, mvnw, mvnw.cmd, .mvn/          Maven wrapper (no Maven install needed)
Dockerfile, docker-compose.yml, .env.example, .gitignore, .editorconfig, README.md
```

## Core logic
**Tables**
- `anchorages(id 'E01', name, area, center geography(Point), radius_m)`
- `vessels(mmsi PK, name, position geography(Point), sog_knots, updated_at)`: latest position per ship, updated in place.
- `anchorage_visits(id, mmsi, anchorage_id, arrived_at, departed_at, seen_arriving)`: empty `departed_at` = still waiting.
- Partial unique index on `(mmsi) WHERE departed_at IS NULL`: the database guarantees at most one open visit per ship.

**Rules** (constants in `AnchorageTracker`; tune after watching real data)

| Rule | Value | Why |
|---|---|---|
| In an anchorage | `ST_DWithin(center, ship, radius_m + 150)`; nearest wins for a new arrival; an open visit is judged against its own anchorage | geography = metres not degrees; the margin was 100 m but real ships sat up to 62 m beyond the radius, so 150 m keeps a drifting ship from splitting its wait in two |
| Arrive | in an anchorage and speed < 0.5 kn | speed is measured; crew-set "at anchor" status is often stale |
| Depart | left that anchorage, or speed > 2.0 kn | 0.5–2.0 kn gap (hysteresis) stops a swinging ship flipping |
| Speed not available | change nothing | unknown speed ≠ moving |
| Stale visit | no report for 6 h → close at last-seen time, **only if** our feed is healthy: positions arriving with no gap over 5 min for at least 15 min, and one within the last 5 min | anchored ships report every ~3 min; the guard stops laptop sleep or an outage closing every visit (socket-open time alone is not enough: after sleep it still looks "connected") |
| Timestamp | server receive time | a few seconds don't matter for multi-day waits |
| `seen_arriving` | false if the ship was first seen already anchored, or was last heard from more than 30 min earlier | start unknown, so stats only use visits we saw begin |

**Concurrency**: the WebSocket listener calls `request(1)` only after the current message is fully processed → one message at a time, no locks.
**Statistics**: `/api/stats?days=N` (N 1–365) per anchorage: ships waiting now, longest current wait, completed visits, average and median dwell hours (`percentile_cont(0.5)`).

## Phases

### Phase 0 — Prerequisites (you)
- [x] Install Temurin JDK 21 → `java -version` says 21
- [x] Install Docker Desktop → `docker run hello-world`
- [x] Install k3d (k3d.io Windows installers); kubectl ships with Docker Desktop → `k3d version`, `kubectl version --client`
- [ ] Get an AISStream API key (free, sign in with GitHub at aisstream.io)

### Phase 1 — Branch and clean slate
- [x] `git switch -c v2`; delete `backend/`, `frontend/`, `Makefile`
- [x] Rewrite `.gitignore`, `.env.example`, `docker-compose.yml` (db only; `postgis/postgis:16-3.4`, healthcheck, volume, port on `127.0.0.1`), `.editorconfig`, stub `README.md`
- [x] **You**: generate the project at the repo root with start.spring.io (Maven, Java 21, Spring Boot 4.x; Spring Web, JDBC API, PostgreSQL Driver, Flyway, Testcontainers). Unzip into the repo root; keep `mvnw`, `mvnw.cmd`, `.mvn/`.
- **Check** (passed 2026-10-04): `docker compose up -d db`, then `./mvnw spring-boot:run` starts. Committed.

### Phase 2 — Schema and anchorage data
- [x] `V1__schema.sql`: the three tables
- [x] `V2__anchorages.sql`: 32 anchorages (English Bay 20, Inner Harbour 8, Indian Arm 4) from guide pp.185-187, coordinates copied as printed in DMS and converted in SQL. **Plan change**: the guide gives no swing radius, only "maximum vessel length overall", so `radius_m` = that length (first estimate; calibrate against real ship positions in Phase 4). Roberts Bank and Sandheads left out (outside the map box).
- **Check** (passed 2026-10-04): a point at E01's centre returns only `E01`; a point in Stanley Park returns no rows. Four pairs of 400 m anchorages (E01/E03, E03/E06, E06/E10, E10/E14) have overlapping zones, so the tracker picks the nearest centre.

### Phase 3 — Ingestion (fills `vessels`)
- [x] `PositionReport.parse()` + `PositionReportTest` (a real captured message and bad-value cases). Real feed notes: a `SubscriptionConfirmation` arrives first; positions come from `Message.PositionReport` (`UserID`, `Latitude`, `Longitude`, `Sog`), ship name from `MetaData.ShipName` (space-padded).
- [x] `AisStreamClient` on `java.net.http.WebSocket`: subscribe to `PositionReport` only; bounding box ≈ `[[49.20,-123.40],[49.47,-122.83]]`; buffer partial binary frames; reconnect with backoff 1 s → 60 s; skip connecting if the API key is blank. **Moved to Phase 4**: recording when the connection came up (only the stale-visit guard needs it).
- [x] **Watchdog**: every 30 s, if connected and the last message is > 2 min old, drop the socket and take the normal reconnect path (half-open connections otherwise go unnoticed)
- [x] `handle()` only updates `vessels` in this phase
- **Check** (passed 2026-10-04): `./mvnw test` passes (7 tests); 44 ships stored in 75 s of live data; a dead proxy shows the backoff growing (522, 1327, 3475, 6200 ms); with the silence limit temporarily set to 100 ms the watchdog aborts, reconnects and re-subscribes.

### Phase 4 — Anchorage detection and dwell time
- [x] Arrive/depart rules, `seen_arriving`, scheduled stale-visit cleanup. `handle(report, at)` takes the time as a parameter so tests can travel through hours of waiting.
- [x] `AnchorageTrackerIT` (15 tests, runs in `./mvnw verify` via the failsafe plugin; `./mvnw test` skips it) against real PostGIS: outside → no visit; stopped in E01 → visit opens; 1 kn drift → stays open; 3 kn → closes with correct duration; stale rule with and without the 15-min guard; first seen already anchored → `seen_arriving = false`
- **Check** (passed 2026-10-04): `./mvnw verify` passes (22 tests). Three deliberate code breaks (nearest-zone rule, 1.5 kn arrival threshold, no feed warm-up) were each caught by the intended test. After 8 min live, 22 ships were waiting: 15 English Bay, 4 Inner Harbour, 3 Indian Arm.
- **Known data noise** (for later): tugs and work boats that sit still near an anchorage (e.g. CATES VIII, TRIDENT WARRIOR) count as visits. A ship-type filter (AIS `ShipStaticData`, cargo/tanker types) is the fix; it is on the "left out" list.

### Phase 5 — Containerize, start collecting, CI
- [x] `Dockerfile`: two-stage (`./mvnw package` → `eclipse-temurin:21-jre`), non-root user (uid 999); `.dockerignore` keeps `.env` out of the image. Image is 511 MB, almost all base image (jar is 23 MB)
- [x] Add `app` service to `docker-compose.yml`: port 8080 (bound to 127.0.0.1), `restart: unless-stopped`, waits for the database healthcheck, settings from `.env`
- [x] **Start collecting**: `docker compose up --build -d` and leave it running (waits last days; stats need data). Started 2026-10-04 ~20:53 local; it only collects while the laptop and Docker are on.
- [x] `.github/workflows/ci.yml`: `./mvnw verify` on every push (passes actionlint; has not run on GitHub yet because `v2` is not pushed). Also fixed: `mvnw` was committed without the executable bit, so CI's `./mvnw` would have failed with "Permission denied".
- **Check**: `docker compose up --build` starts everything (passed 2026-10-04: 55 ships and 8 open visits within 2 minutes, container runs as non-root, no `.env` in the image). CI passing on GitHub is still to be confirmed after the first push.

### Phase 6 — REST API
- [x] `ApiController` (JdbcClient, records): `GET /api/anchorages`, `GET /api/vessels` (seen in last 30 min, with current anchorage, anchored-since and whether we saw the arrival), `GET /api/stats?days=30` (one row per anchorage; waiting-now and longest-wait use every open visit, dwell average and median use only visits whose start we saw)
- [x] Errors: `spring.mvc.problemdetails.enabled=true` (RFC 9457); bad `days` (outside 1-365, or not a number) → 400; no stack traces. Unknown URL → 404, POST → 405, all as problem+json.
- [x] Stats cases with known visits → known median and average, in a new `ApiControllerIT` (9 tests, MockMvc against real PostGIS) instead of `AnchorageTrackerIT`
- **Check** (passed 2026-10-04): `./mvnw verify` passes (31 tests); three deliberate SQL breaks (`count(*)` instead of `count(v.id)`, counting visits whose start was unseen, p90 instead of the median) were each caught. On the live container: 122 ships heard in 30 min, 27 at anchor, stats show 27 waiting; `days=0` → 400 `application/problem+json`.

### Phase 7 — Map page
- [x] `index.html` (Leaflet 1.9.4 from unpkg, pinned with integrity hashes) + `style.css` + `app.js` (~190 lines), plus an "At anchor now" list and a 7/30/90-day window for the statistics. The API now also returns `reachM` (radius + detection margin) so the circles show exactly where the detector counts a ship as anchored: anchorages as `L.circle`; markers in a `Map<mmsi, marker>` updated every 10 s; anchored ships labelled "waiting 2d 4h at E05"; stats table every 60 s
- [x] Ship names are untrusted radio text → `textContent`/DOM nodes, never HTML strings. Backed by a Content-Security-Policy (no inline scripts, CSS in its own file) and `StaticPageTest`, which fails the build if `app.js` ever uses `innerHTML` and friends.
- **Check** (passed 2026-10-04, in Chrome): circles line up with English Bay and anchored ships sit inside their zones; a fake ship named `<img src=x onerror=...><b>BOLD</b>` showed as plain text in the list and the tooltip (no `<img>` or `<b>` created, handler never ran); a forced inline handler was blocked by the CSP; the console is clean; the phone layout (390 px) has no sideways scroll. `./mvnw verify` passes (33 tests).

### Phase 8 — Local Kubernetes (k3d) with health probes
- [x] Actuator (+ `ActuatorIT` guard test: probes UP, env/heapdump/metrics/beans/configprops/loggers/threaddump return 404): add `spring-boot-starter-actuator`; `management.endpoint.health.probes.enabled: true`; `management.endpoint.health.group.readiness.include: readinessState,db`; only `health` exposed over HTTP
- [x] `k8s/postgres.yaml`: StatefulSet (1 replica, volume) + Service `db`; probes run `pg_isready`
- [x] `k8s/app.yaml`: Deployment + Service; `replicas: 1`, `strategy: Recreate`; image `borderline:0.1` (fixed tag, never `latest`); env from Secret `borderline-secrets` + `DB_URL=jdbc:postgresql://db:5432/borderline`; memory request 256Mi / limit 512Mi, `-XX:MaxRAMPercentage=75`

| Probe | Endpoint | Timing | Question | If it fails |
|---|---|---|---|---|
| startup | `/actuator/health/liveness` | every 5 s, 24 tries | finished starting (JVM + Flyway)? | liveness waits; restart only after 2 min |
| liveness | `/actuator/health/liveness` | every 10 s, 3 failures | JVM stuck beyond repair? | container restarted |
| readiness | `/actuator/health/readiness` | every 5 s | can it serve (DB reachable)? | removed from Service, **not** restarted |

Liveness ignores the database and AISStream on purpose: restarting can't fix either, so checking them would only cause restart loops. No init container; one `CrashLoopBackOff` on first start is expected.

Run:
```
k3d cluster create borderline
docker build -t borderline:0.1 .
k3d image import borderline:0.1 -c borderline
kubectl create secret generic borderline-secrets --from-env-file=.env
kubectl apply -f k8s/
kubectl get pods -w
kubectl port-forward svc/borderline 8081:8080     # http://localhost:8081
```
**Experiments** (each proves one concept). Rehearsed by Claude on 2026-10-05 in a throwaway trial cluster: all six passed. Run by you on the real cluster (self-reported). Rehearsal numbers: cluster ready 30 s, images imported 26 s, pods 1/1 about 21 s after apply with 2 expected startup restarts (app starts before the database is ready); liveness break gave restarts 1, 2, 3 about every 30 s; at most one app container ever running during a rollout.
1. both pods reach `1/1 Running`; map loads via port-forward
2. `kubectl scale statefulset postgres --replicas=0` → app pod `0/1` Ready, restart count stays 0; `kubectl get endpointslices -l kubernetes.io/service-name=borderline` shows it removed (port-forward bypasses this); scale back → Ready
3. point liveness at `/actuator/health/nope`, apply → restart count climbs; revert
4. `kubectl describe pod` shows startup probe holding off liveness during migrations
5. `kubectl delete pod postgres-0` → returns on the same volume, visits intact
6. `kubectl rollout restart deployment/borderline` → old pod gone before new one starts; logs show one AISStream connection at a time

Keep collecting in compose; the k3d database starts empty. Both running = 2 of AISStream's 3 connections.

### Phase 9 — Finish
- [ ] README: what it does, architecture diagram, detection rules table, how to run (compose first, k3d second), concepts list, screenshot/GIF
- [ ] PR `v2` → `main`, merge
- **Check**: fresh clone runs with `cp .env.example .env` + `docker compose up --build`; CI passes on `main`.

## Technologies by the end
Java 21, Spring Boot 4 + Actuator, JdbcClient/SQL, PostgreSQL + PostGIS, Flyway, Java's built-in WebSocket client, REST/JSON, HTML/JS + Leaflet, JUnit 5 + Testcontainers, Docker + docker compose, Kubernetes (k3d/k3s, kubectl), GitHub Actions CI.

## Concepts by the end
| Concept | Where | How you'd explain it |
|---|---|---|
| REST API | `ApiController` | read-only GET endpoints returning JSON, checked inputs, correct status codes |
| WebSockets (client) | `AisStreamClient` | reads a pushed feed; binary frames, partial messages, pacing via `request(1)` |
| Error handling / resilience | client, tracker | backoff reconnect, watchdog, bad message skipped not fatal, stale-visit guard, RFC 9457 errors, no stack traces |
| Input validation | `PositionReport`, `ApiController` | AIS radio data is untrusted; query params range-checked |
| PostGIS / spatial SQL | V1, V2, tracker | geography vs geometry, `ST_DWithin`, SRID 4326, `ST_MakePoint(lon, lat)` order trap |
| Data modelling, migrations | V1, V2, Flyway | latest-state table + episode table; partial unique index; versioned schema |
| SQL analytics | stats query | median vs mean (`percentile_cont`), time windows |
| State machine, hysteresis | tracker | smoothing a noisy signal |
| Transactions, concurrency | tracker | updates commit together; one message at a time = no locks |
| Upsert, scheduled jobs | tracker | `ON CONFLICT DO UPDATE`; `@Scheduled` with a guard |
| Data quality | `seen_arriving` | waits with unseen start excluded from stats |
| Security | throughout | API key server-side only; parameterized SQL; safe rendering of untrusted text; single origin; DB port on localhost; non-root container; only `health` exposed; secrets from `.env`/Secret, never committed |
| Auth | none, on purpose | public read-only data; first write endpoint would add Spring Security |
| Testing | unit + integration | integration tests against real PostGIS, not mocks |
| Containers, CI | Dockerfile, compose, Actions | two-stage build, restart policy, every push tested |
| Frontend | `index.html`, `app.js` | Leaflet, fetch polling, markers by ship ID, no framework |
| Kubernetes workloads | `k8s/` | Deployment for the app, StatefulSet + volume for the DB, Services for DNS names |
| Health probes | `app.yaml`, `application.yml` | startup vs liveness vs readiness; liveness never checks dependencies |
| Singleton rollout | `app.yaml` | `replicas: 1` + `Recreate`: never a second AISStream consumer |
| Resource limits, Secrets | `app.yaml` | requests vs limits, `OOMKilled`, JVM sizes itself to the container |
| Self-healing | watchdog + k8s | app fixes what it can (reconnect); Kubernetes restarts what it can't |

**No longer claimable after v2**: JWT login, BCrypt, API keys/HMAC, Redis rate limiting, STOMP to the browser, JPA/Hibernate, React/TypeScript. Resume wording for k8s: "ran on a local k3d cluster with liveness, readiness and startup probes", not "deployed on Kubernetes".

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| Stats need days of data; collection only runs while the laptop is on | Certain | start collecting in Phase 5; 15-min guard survives sleep; "waiting now" works from day one |
| AISStream is beta: no uptime guarantee, no replay | Medium | backoff reconnect, watchdog, stale-visit guard |
| Anchorage coordinates transcribed wrong | Medium | compare against where ships actually sit on the map in Phase 7 |
| E05 → E10 move counts as two visits | Low | known limit; merge consecutive visits later in the query |
| Spring Boot 4 differs from most tutorials (written for Boot 3) | Medium | generate from start.spring.io; Claude flags Boot 4-specific code |
| Compose and k8s settings drift apart | Medium | same env var names in both; compose stays the main path |
| Laptop memory (Docker Desktop + k3d + compose) | Medium | k3s ≈ 0.5 GB; stop the compose app while experimenting; check Docker Desktop memory |
| Wrong probe settings cause restart loops | Low | liveness ignores DB and AISStream; experiment 3 shows what a loop looks like |

## Estimate (~21–28 h)
Phase 0: 1 · Phase 1: 1–2 · Phase 2: 2–3 · Phase 3: 3.5–4.5 · Phase 4: 4–5 · Phase 5: 2 · Phase 6: 1–2 · Phase 7: 3–4 · Phase 8: 3–4 · Phase 9: 1–2

**Left out**: cloud deployment, HTTPS, Ingress, Helm/Kustomize, autoscaling, ship types, track history, Gulf Islands overflow anchorages.

## Acceptance
- [ ] All phases done and every phase's **Check** passes
- [ ] You can explain every file and every row of the rules table without notes
- [ ] `./mvnw verify` green locally and in CI
- [ ] Fresh clone runs with two commands
