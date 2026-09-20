# UBRR — Uber-Clone (Spring Boot Microservices + Temporal + React + Keycloak)

A production-shaped **Uber-style ride-sharing platform** built as a resume-ready reference project. Implements the full ride flow — fare estimation with tiered pricing → ride request → durable Temporal-based driver matching → live driver notifications → post-trip ratings → Prometheus/Grafana observability — using industry-standard patterns: distributed locking, Redis geospatial search, OAuth2 (Keycloak), an API gateway, adaptive location heartbeat, and a WebSocket push channel.

> **Tech Stack**
**Java 21** · Spring Boot 3.2 · Spring Cloud Gateway · Spring Security (OAuth2 Resource Server) · Spring Data JPA · Spring Data Redis · WebSocket · **Temporal 1.24 (durable workflows)** · **Apache Kafka (KRaft mode)** · PostgreSQL 16 · Redis 7 · Keycloak 24 · **Prometheus + Grafana** (Micrometer) · Docker Compose · React 18 · Google Maps JS + Distance-Matrix APIs

---

## 1. Architecture

For a concise system map and deployment notes, see [docs/architecture.md](docs/architecture.md).

Every service is behind an **API gateway** and communicates with peers via a **Kafka event bus** — not point-to-point REST. This keeps services loosely coupled: any consumer can crash and catch up on restart without dropping work.

```
                                                ┌─────────────────────┐
                                                │  Temporal Server    │
                                                │  (durable workflow) │
                                                └──────────┬──────────┘
                                                           │
┌────────────┐    ┌──────────────────────┐    ┌────────────┴─────────┐
│  React App │───▶│  API Gateway :8080   │───▶│  Matching Svc :8083  │◀───┐
│  (Riders + │    │  (Spring Cloud GW +  │    │  Temporal worker +   │    │
│   Drivers) │    │   Keycloak JWT auth) │    │  Redis distributed   │    │
└─────┬──────┘    │                      │    │  lock (10s TTL)      │    │
      │           │                      │    └──────────────────────┘    │
      │           │                      │                                │
      │           │                      │    ┌──────────────────────┐    │
      │           │                      │───▶│  Ride Service :8081  │──┐ │
      │           │                      │    │  fare tiers, surge   │  │ │
      │           │                      │    │  scheduling, rides   │  │ │
      │           │                      │    └──────────────────────┘  │ │
      │           │                      │                              │ │
      │           │                      │    ┌──────────────────────┐  │ │  ride.requested
      │           │                      │───▶│  Payments Svc :8086  │◀─┤ │  ride.scheduled
      │           │                      │    │  Stripe PaymentIntent│  │ │  ride.accepted
      │           │                      │    └──────────────────────┘  │ │  ride.completed
      │           │                      │                              ▼ │  ride.cancelled
      │           │                      │    ╔══════════════════════════╗│  payment.charged
      │           │                      │    ║  Kafka (KRaft, no ZK)   ║◀┘
      │           │                      │    ║  6 topics · 1 broker    ║
      │           │                      │    ╚═════════▲════════════════╝
      │           │                      │              │
      │           │                      │    ┌──────────────────────┐
      │◀────WS────┼──────────────────────┼───▶│  Notification :8084  │
      │           │                      │    │  WebSocket push      │
      │           │                      │    └──────────────────────┘
      │           │                      │
      │           │                      │    ┌──────────────────────┐
      │           │                      │───▶│  Location Svc :8082  │──▶ Redis GEO
      │           │                      │    │  GEOADD / GEOSEARCH  │      (geohashing)
      │           │                      │    └──────────────────────┘
      │           │                      │
      │           │                      │    ┌──────────────────────┐
      │           │                      │───▶│  Ratings Svc :8085   │──▶ Postgres
      │           │                      │    │  post-trip ratings   │
      │           │                      │    └──────────────────────┘
      └───────────┴──────────────────────┘

Observability:  Prometheus (:9090)  →  Grafana (:3001) — every service
                exposes /actuator/prometheus, scraped every 15s.
Auth:           Keycloak (:8180)     realm=uber-realm, seeded users.
Temporal UI:    :8233                inspect running matching workflows.
Kafka UI:       :8090                inspect topics & messages.
```

### Design highlights

| Requirement | How it's satisfied |
|---|---|
| Low-latency matching < 60s | Temporal workflow with per-driver 10s acceptance timers, Redis GEOSEARCH |
| **Strong consistency** in driver assignment | Redis `SET NX PX` distributed lock scoped per driver — only one outstanding request per driver at a time |
| **Zero dropped rides on crash** | Matching workflow runs on **Temporal** — if the service dies, Temporal replays the workflow from persistent event history on another worker |
| **Scheduled rides** | Second Temporal workflow (`ScheduledRideWorkflow`) sleeps until the pickup time via a durable timer, then activates the ride into the normal matcher |
| High throughput driver locations | Redis geo set via `GEOADD`; no DB writes on the hot path |
| Stale-driver cleanup | Companion heartbeat ZSET + periodic scheduled cleanup (`@Scheduled`) |
| Tiered pricing | UberX / UberXL / Comfort computed in one fare call, rider picks a tier at request time |
| **Surge pricing** | Sliding-window demand tracker keyed by geo cell in Redis; 1.0× / 1.3× / 1.7× / 2.5× multipliers based on requests/minute |
| **Automatic payments** | Ride completion triggers a Stripe PaymentIntent (off-session, `pm_card_visa` in test mode); 80/20 driver/platform split recorded in `payments_db` |
| **Driver earnings** | Rolling 7-day earnings dashboard on the driver UI backed by the payments-service |
| Post-trip feedback | Ratings-service records star + comment for both directions; rider sees driver avg rating on match |
| Adaptive location updates | Driver client dynamically pings every 3s / 5s / 15s based on GPS-reported speed |
| Observability | Every service exposes Prometheus metrics; pre-provisioned Grafana dashboard with RPS, P95 latency, JVM heap, error rate |
| **CI** | GitHub Actions workflow (`.github/workflows/build.yml`) runs `mvn verify` + `yarn build` on every PR |
| **Event-driven** | Ride-service publishes 5 event types to Kafka (`ride.requested`, `ride.scheduled`, `ride.accepted`, `ride.completed`, `ride.cancelled`). Matching-service and Payments-service consume them independently — no synchronous coupling. Payments then publishes `payment.charged` for future consumers (notifications, analytics). |
| **Responsive UI** | Frontend adapts to phone / tablet / laptop / landscape — the side panel morphs into a bottom-sheet at ≤768px. |
| **Geospatial indexing** | Driver locations live in a Redis geo-set (`drivers:geo`) which internally stores each member as a **52-bit geohash score** in a sorted set. Proximity queries use `GEOSEARCH` (radius or bounding box) in O(log N) — that's true geospatial indexing, not a table scan. Surge pricing separately buckets fare requests by 3-decimal lat/lng cells (~110m). |
| Security | All `/api/**` behind Keycloak-issued JWTs; user identity extracted from token (never trusted from body) |

---

## 2. Repository layout

```
uber-clone/
├── pom.xml                  ← Maven parent (aggregator)
├── mvnw / mvnw.cmd          ← Maven wrapper
├── docker-compose.yml       ← Postgres · Redis · Keycloak · Temporal · Prometheus · Grafana
├── keycloak/realm-export.json  ← Pre-seeded realm + rider/driver users
├── infra/
│   ├── postgres/init-multiple-dbs.sh
│   ├── prometheus/prometheus.yml
│   └── grafana/{provisioning,dashboards}   ← Pre-provisioned Grafana dashboard
├── common/                  ← Shared DTOs (LatLng, RideStatus, RideCategory), JWT converter, exception handler
├── api-gateway/             ← Spring Cloud Gateway, JWT validation, route table
├── ride-service/            ← Fare estimation (tiered), ride lifecycle (JPA/Postgres)
├── location-service/        ← Redis-backed real-time driver geo tracking
├── matching-service/        ← Temporal worker + workflow + Redis lock
├── notification-service/    ← WebSocket push channel to drivers
├── ratings-service/         ← Post-trip ratings, avg-star summary
├── payments-service/        ← Stripe-backed ride charges, driver earnings
└── frontend/                ← React 18 + Google Maps + Keycloak SPA
```

---

## 3. Prerequisites

| Tool | Version                                                   |
|---|-----------------------------------------------------------|
| **JDK** | 21 (Temurin, OpenJDK, or another compatible distribution) |
| **Docker Desktop** | 4.x (or Docker Engine + Compose v2)                       |
| **Node.js** | 18+ (only for the React frontend)                         |
| **IntelliJ IDEA** | 2023.3+ (Community edition is fine)                       |

Optional: Maven CLI. The bundled `./mvnw` wrapper works if you don't install Maven globally.

---

## 4. Running the project

### Step 1 — Start infrastructure

```bash
docker compose up -d
docker compose logs -f keycloak     # wait until you see "Realm 'uber-realm' imported"
```

Verify:

| URL | What |
|---|---|
| http://localhost:8180 | Keycloak admin (admin/admin) |
| http://localhost:8233 | Temporal Web UI — inspect running workflows |
| http://localhost:8090 | **Kafka UI** — inspect topics + tail messages |
| http://localhost:9090 | Prometheus |
| http://localhost:3001 | Grafana (admin/admin) — pre-provisioned "UBRR – Microservices Overview" dashboard |

### Step 2 — Start the 6 microservices

**Option A · From IntelliJ (recommended)**

1. `File → Open…` → select the repo root (`uber-clone/pom.xml`) → **Open as Project**.
2. IntelliJ auto-imports all 7 Maven modules.
3. Add a *Spring Boot* run config for each of these main classes (or just right-click → *Run*):
   * `ApiGatewayApplication` — port 8080
   * `RideServiceApplication` — port 8081
   * `LocationServiceApplication` — port 8082
   * `MatchingServiceApplication` — port 8083
   * `NotificationServiceApplication` — port 8084
   * `RatingsServiceApplication` — port 8085
   * `PaymentsServiceApplication` — port 8086
4. Run them all. Group them in a *Compound* run config called **All Services** for one-click startup.

**Option B · Command line**

```bash
./mvnw -pl api-gateway spring-boot:run
./mvnw -pl ride-service spring-boot:run
./mvnw -pl location-service spring-boot:run
./mvnw -pl matching-service spring-boot:run
./mvnw -pl notification-service spring-boot:run
./mvnw -pl ratings-service spring-boot:run
./mvnw -pl payments-service spring-boot:run
```

### Step 3 — Start the React frontend

```bash
cd frontend
npm install        # or: yarn
npm start          # http://localhost:3000
```

Open **http://localhost:3000** → click **Sign in with Keycloak** → log in as `rider1` or `driver1` (password `password`).

---

## 5. End-to-end demo script

Open **two browser sessions** (regular + incognito) so you can be *rider1* on one and *driver1* on the other.

1. **Driver window** — sign in as `driver1` → **Go Online**. Watch the *Adaptive heartbeat* chip: it starts at 15s (IDLE), switches to 5s (CITY) once you move, 3s (HIGHWAY) at highway speed.
2. **Rider window** — sign in as `rider1` → enter pickup + destination → **Get Fare Estimate**.
3. **Pick a tier** — UberX / UberXL / Comfort — each with its own price and passenger capacity. Click **Request UberX**.
4. Within ~2s the **Incoming Ride Request modal** with the 10s countdown pops in the driver window. In the meantime, Temporal is orchestrating the whole thing durably. Open http://localhost:8233 to watch the workflow. **Accept**.
5. Rider window shows the driver's user id and their **star average** (if any). Ride progresses `ACCEPTED → IN_PROGRESS → COMPLETED`.
6. On completion, both windows show a **Rate the driver/rider** modal. Submit stars + comment. The rider's next match now shows the fresh average.

Try the negative flows too:
* Let the driver's 10s timer expire — Temporal moves to the next driver automatically. **Kill matching-service mid-workflow** (`docker/kill -9`) — restart it, Temporal replays and finishes the match.
* Request a ride with no driver online → after all candidates timeout the rider gets `NO_DRIVERS_FOUND`.

---

## 6. Public API cheatsheet (via the Gateway)

All endpoints require `Authorization: Bearer <jwt>`. Get a token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/uber-realm/protocol/openid-connect/token \
  -d "grant_type=password" -d "client_id=uber-frontend" \
  -d "username=rider1" -d "password=password" | jq -r .access_token)
```

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/api/fares/estimate` | RIDER | Returns UberX / XL / Comfort options |
| POST | `/api/rides`          | RIDER | Confirm a fare (by `fareId`) and trigger the Temporal matching workflow |
| GET  | `/api/rides/{id}`     | Any | Retrieve a ride |
| GET  | `/api/rides/mine`     | RIDER | Rider history |
| GET  | `/api/rides/driver/mine` | DRIVER | Driver history |
| PATCH| `/api/rides/{id}/accept`  | DRIVER | Accept a ride request |
| PATCH| `/api/rides/{id}/decline` | DRIVER | Decline a ride request |
| PATCH| `/api/rides/{id}/status`  | DRIVER | IN_PROGRESS / COMPLETED / CANCELLED |
| POST | `/api/locations/driver`   | DRIVER | Adaptive heartbeat with current lat/lng |
| DELETE | `/api/locations/driver` | DRIVER | Go offline |
| GET  | `/api/locations/nearby`   | Any | GEOSEARCH drivers |
| GET  | `/api/locations/driver/{id}` | Any | Current position |
| POST | `/api/ratings`            | Any | Submit a rating |
| GET  | `/api/ratings/user/{id}/summary?role=DRIVER` | Any | Average stars + count |
| GET  | `/api/ratings/ride/{id}`  | Any | All ratings for a ride |
| POST | `/api/rides/schedule`     | RIDER | Book a ride for later (`scheduledFor` ISO instant) |
| GET  | `/api/rides/scheduled/mine` | RIDER | Upcoming scheduled rides |
| GET  | `/api/payments/ride/{id}` | Any | Payment record for a ride |
| GET  | `/api/payments/driver/mine/earnings?days=7` | DRIVER | Rolling earnings + daily breakdown |

Ride-service Swagger UI: <http://localhost:8081/swagger-ui.html>

---

## 7. Observability — Grafana dashboard

After starting the stack + at least one service, open **Grafana → Dashboards → UBRR – Microservices Overview**. Panels included out-of-the-box:

* **Services UP** — count of services responding to Prometheus
* **HTTP Requests / sec** by service
* **HTTP P95 latency (seconds)**
* **JVM heap used (MB)**
* **HTTP error rate (5xx / sec)**

Raw Prometheus queries are also available at <http://localhost:9090>. Every service exposes `/actuator/prometheus`, e.g. <http://localhost:8081/actuator/prometheus>.

---

## 8. Temporal — durable workflow orchestration

The matcher is implemented as a Temporal workflow (`MatchingWorkflowImpl`) with 7 activities:

* `fetchRide`, `pollRide` — polls ride status via ride-service
* `fetchNearbyDrivers` — Redis GEOSEARCH via location-service
* `tryLockDriver`, `releaseDriverLock` — Redis distributed lock
* `notifyDriver`, `notifyNoDrivers` — WebSocket push via notification-service

Because Temporal persists the entire workflow event history, **any service crash or restart replays the workflow from its last completed step on another worker.** No rides are dropped.

Try it: while a matching workflow is running (visible at http://localhost:8233), stop matching-service. Restart it. The workflow resumes exactly where it left off.

Task queue: `UBER_MATCHING_TASK_QUEUE` · Namespace: `default` · gRPC endpoint: `127.0.0.1:7233`

---

## 9. Extending the project

* **Push notifications for offline devices:** add FCM/APNs alongside the current WebSocket channel.
* **Rider stored payment methods:** collect real cards via Stripe Elements instead of using `pm_card_visa`.
* **Stripe Connect (real driver payouts):** wire in Stripe Connect Express and use `transfer_data` on the PaymentIntent to auto-payout the driver's share.

---

## 9a. Stripe setup

The **payments-service** uses the official Stripe Java SDK. In `payments-service/application.properties`:

```properties
stripe.api-key=${STRIPE_API_KEY:}
stripe.test-payment-method=${STRIPE_TEST_PAYMENT_METHOD:pm_card_visa}
```

1. Grab a free **test-mode secret key** at <https://dashboard.stripe.com/test/apikeys> (starts with `sk_test_…`).
2. Export it before starting the service:
   ```bash
   export STRIPE_API_KEY=sk_test_xxx
   ./mvnw -pl payments-service spring-boot:run
   ```
3. Without a key, ride completions still work — payment records are inserted with `status=MOCKED` so the ride flow isn't blocked.
4. The demo uses Stripe's `pm_card_visa` test PaymentMethod so no card-collection UI is needed. Charges auto-confirm off-session; you'll see real PaymentIntents in your Stripe test dashboard.
5. **80/20 split**: `uber.payments.driver-share=0.80` — the driver's earnings are recorded in the DB and rolled into the 7-day earnings dashboard on the driver UI.

---

## 9b. Scheduled rides (Temporal timer)

Riders can pick **Schedule** instead of **Ride Now** and enter a future pickup time. Under the hood:

1. Ride is created with `status=SCHEDULED` and a `scheduledFor` timestamp.
2. Ride-service calls `matching-service /api/matching/internal/schedule` which starts a `ScheduledRideWorkflow` on Temporal.
3. The workflow `Workflow.sleep(delay)` — durable! Survives service restarts.
4. When the timer fires, the workflow calls back into ride-service (`/api/rides/internal/{id}/activate`) which flips the status to `REQUESTED` and kicks off the normal matching workflow.

Watch it happen live at http://localhost:8233 — you'll see the workflow sitting in the sleeping state.

---

## 9d. Event bus (Kafka)

Ride-service is the source of truth; every other service reacts to events.

| Topic | Producer | Consumers | When |
|---|---|---|---|
| `ride.requested` | ride-service | matching-service (starts MatchingWorkflow) | Rider hits *Request* |
| `ride.scheduled` | ride-service | matching-service (starts ScheduledRideWorkflow) | Rider hits *Schedule* |
| `ride.accepted`  | ride-service | (notification/analytics — extensible) | Driver taps *Accept* |
| `ride.completed` | ride-service | payments-service (triggers Stripe charge) | Driver taps *Complete* |
| `ride.cancelled` | ride-service | (notification/analytics — extensible) | Any cancel |
| `payment.charged` | payments-service | (notification/analytics — extensible) | After Stripe returns |

Inspect topics + messages live at **http://localhost:8090** (Kafka UI). Kafka runs in **KRaft mode** — no Zookeeper needed.

Because ride-service just fires-and-forgets, if matching-service or payments-service is down, the events accumulate in the broker and are processed on recovery. Try it: `docker stop uber-matching-service` (or just kill the process in IntelliJ), issue several rides, restart the service — every workflow starts.

---

## 9e. Geospatial indexing (yes — really indexed, not scanned)

**Redis geo-set (`drivers:geo`) is a true geospatial index:**

* Every `GEOADD` encodes lat/lng into a **52-bit geohash** and stores it as the *score* of a sorted-set member.
* `GEOSEARCH … BYRADIUS 5 km ASC LIMIT 10` runs in `O(log N + K)` where K is result count — no full scan, no B-tree over 2D data (which doesn't work well anyway).
* Adjacent points share prefix bits, so Redis internally checks only the relevant geohash cells + neighbours.

**Where else geo indexing is used:**
* **Surge pricing** — separate Redis sorted-set per 3-decimal-place lat/lng cell (~110m). Not for lookups; for demand-density windowing.
* **Design-ready for horizontal sharding** — swap the cell key format to an H3 or S2 cell id and you can shard the geo set by cell id across a Redis cluster with no query changes. Called out as a P2 backlog item.

The docs page linked from Redis on how geo commands work: <https://redis.io/docs/latest/develop/data-types/geospatial/>

---

## 9c. Surge pricing

Ride-service uses Redis as a sliding-window request tracker per ~110m geo cell:

| Requests / minute in cell | Multiplier | Band |
|---|---|---|
| < 10 | 1.0× | NORMAL |
| 10-19 | 1.3× | MODERATE |
| 20-39 | 1.7× | HIGH |
| ≥ 40 | 2.5× | EXTREME |

The multiplier is applied on top of the per-tier price. When active, the rider sees a **Surge active** banner and the tier prices include the bump. Simulate a surge by hitting `/api/fares/estimate` in a loop with the same pickup:
```bash
for i in {1..25}; do curl -s -X POST "$API/api/fares/estimate" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"pickup":{"lat":37.7749,"lng":-122.4194},"pickupAddress":"SF","destination":{"lat":37.775,"lng":-122.42},"destinationAddress":"X"}'; done
```

---

## 10. Troubleshooting

| Symptom | Fix |
|---|---|
| `401 Unauthorized` | Confirm Keycloak is up (`docker compose logs keycloak`) and the JWT hasn't expired. |
| Fare shows `mocked=true` | Google Maps Distance Matrix API unreachable or key invalid; the service falls back to haversine. |
| Driver never gets ride push | Verify driver is **online** and the WS is connected (devtools → Network → WS). Also check the Temporal workflow at :8233 for errors. |
| Grafana empty | Ensure the JVM services are running on the host (Prometheus scrapes via `host.docker.internal`). |
| `Temporal worker failed to start` | Wait 20-30s for Temporal to finish its schema migrations, or restart matching-service. |
| Payment shows `MOCKED` | `STRIPE_API_KEY` env var not set. Grab a free test key at https://dashboard.stripe.com/test/apikeys and re-run payments-service. |
| Surge multiplier stuck at 1.0× | Only 10+ requests/min in the same 110m cell triggers a bump. Use the curl loop in §9c. |
| Events not being consumed | Check the Kafka UI at http://localhost:8090. Ensure `spring.kafka.bootstrap-servers=localhost:9092` in each service and the docker `kafka` container is healthy. |
| Frontend layout broken on phone | Hard-refresh the page and confirm the viewport meta tag is present (see §11). The bottom-sheet layout kicks in at ≤768px. |

---

## 11. License

MIT. Not affiliated with Uber Technologies, Inc.
