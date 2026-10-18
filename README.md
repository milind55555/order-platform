# Order Platform — Event-Driven Microservices on Spring Boot, Kafka & Kubernetes

An e-commerce backend split into independently deployable services that coordinate through **Kafka events** using the **saga pattern** (choreography) — including automatic compensation when stock or payment fails.

![Java 17](https://img.shields.io/badge/Java-17-orange) ![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green) ![Kafka](https://img.shields.io/badge/Kafka-KRaft-black) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue) ![Redis](https://img.shields.io/badge/Redis-7-red) ![Kubernetes](https://img.shields.io/badge/Kubernetes-EKS-326ce5)

## Architecture

```
                       ┌──────────────────────────────┐
 Client ──HTTPS──────▶ │ API Gateway (Spring Cloud)   │  validates JWT, enforces roles,
                       │ :8080                        │  forwards X-User-* headers
                       └─┬─────────┬─────────┬────────┘
                         │         │         │            (all other services are ClusterIP-only)
                 ┌───────▼──┐ ┌────▼─────┐ ┌─▼────────┐ ┌──────────┐
                 │  Auth    │ │  Order   │ │Inventory │ │ Payment  │
                 │ Spring   │ │ Service  │ │ Service  │ │ Service  │
                 │ Security │ │          │ │ + Redis  │ │          │
                 └────┬─────┘ └──┬───▲───┘ └─┬──▲─────┘ └──┬──▲────┘
                      │          │   │       │  │          │  │
                   authdb     orderdb  └──── Kafka ─────────┘  │   (database per service)
                                          inventorydb      paymentdb
```

### Order saga (choreography)

```
POST /api/orders ─▶ order-service ──order.created──▶ inventory-service
                      (PENDING)                          │ reserve stock atomically, price computed server-side
                                                         ├─ ok ──inventory.reserved──▶ payment-service
                                                         │                                 ├─ ok ──payment.completed──▶ order CONFIRMED
                                                         │                                 └─ declined ──payment.failed──▶ order CANCELLED
                                                         │                                                  └──▶ inventory releases stock (compensation)
                                                         └─ fail ──inventory.failed──▶ order CANCELLED
```

| Service | Port | Responsibility | Tech |
|---|---|---|---|
| api-gateway | 8080 | Single entry point, JWT validation, RBAC, header propagation | Spring Cloud Gateway |
| auth-service | 8081 | Register / login, BCrypt hashing, JWT issuing | Spring Security, JPA |
| order-service | 8082 | Order lifecycle & saga state | JPA, Kafka |
| inventory-service | 8083 | Catalogue, stock reservation/release, Redis read-through cache | JPA, Kafka, Redis |
| payment-service | 8084 | Simulated payments (declines amounts above a limit) | JPA, Kafka |
| common | – | Event contracts, topic names, JWT utility | – |

## Engineering highlights

- **Saga with compensation** – failed payment releases reserved stock and cancels the order; no distributed transactions.
- **No overselling** – stock is decremented with a single conditional `UPDATE ... WHERE available >= qty` (atomic under concurrency); a multi-item order is all-or-nothing in one DB transaction.
- **Prices are never trusted from the client** – inventory computes the total and passes it downstream.
- **Events published only after DB commit** (`TransactionSynchronization.afterCommit`) so consumers never see rolled-back data.
- **Idempotent consumers** – duplicate `order.created` / `inventory.reserved` deliveries are detected (reservation record, unique `orderId` on payments).
- **Edge security** – gateway strips client-supplied `X-User-*` headers, validates JWT, returns 401/403; admin-only product writes are re-checked in the service (defence in depth); users can only read their own orders.
- **Redis caching** – `@Cacheable` product lookups with TTL, evicted on every stock change.
- **Database per service** on a single PostgreSQL instance locally (RDS in production).
- **Production-minded packaging** – multi-stage Docker build, non-root UID, JVM container-aware memory, liveness/readiness/startup probes, rolling updates with zero unavailability, resource limits, HPA, ALB ingress, Prometheus metrics endpoint.
- **CI/CD** – GitHub Actions: build + tests → Docker build → Trivy scan → push to GHCR; manual OIDC-based deploy workflow to EKS.

## Run locally (Docker only)

```bash
cp .env.example .env          # optional – defaults work for local dev
docker compose up --build     # first build takes a few minutes
./scripts/demo.sh             # needs curl + jq
```

Everything is reachable through the gateway at `http://localhost:8080`.
Seeded data: admin user `admin` / `Admin@12345` (override with `ADMIN_PASSWORD`) and 4 products:

| id | product | price (₹) | stock |
|---|---|---|---|
| 1 | Developer Laptop | 75,000 | 50 |
| 2 | Smartphone | 25,000 | 100 |
| 3 | Headphones | 3,000 | 200 |
| 4 | Keyboard | 1,500 | 300 |

### Manual walkthrough

```bash
BASE=http://localhost:8080
curl -X POST $BASE/api/auth/register -H 'Content-Type: application/json' -d '{"username":"alice","password":"Password@123"}'
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"Password@123"}' | jq -r .token)

curl $BASE/api/products -H "Authorization: Bearer $TOKEN"
curl -X POST $BASE/api/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"items":[{"productId":2,"quantity":1}]}'
curl $BASE/api/orders/1 -H "Authorization: Bearer $TOKEN"          # PENDING -> CONFIRMED within a second or two
curl $BASE/api/payments/order/1 -H "Authorization: Bearer $TOKEN"

# Trigger the compensation path: 2 laptops = 150,000 > 100,000 payment limit
curl -X POST $BASE/api/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"items":[{"productId":1,"quantity":2}]}'                 # ends CANCELLED, laptop stock restored
```

### API summary

| Method & path | Auth | Description |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | public | Create account / get JWT |
| `GET /api/products`, `GET /api/products/{id}` | JWT | Browse catalogue (cached) |
| `POST /api/products` | JWT + ADMIN | Create product |
| `POST /api/orders` | JWT | Place order |
| `GET /api/orders`, `GET /api/orders/{id}` | JWT | My orders (admins can read any) |
| `GET /api/payments/order/{orderId}` | JWT | Payment for an order |
| `GET /actuator/health`, `/actuator/prometheus` | per service | Probes & metrics |

## Tests

```bash
mvn verify
```

JUnit 5 + Mockito unit tests for saga/business logic (order, inventory, payment, auth), JWT utility, gateway filter (reactive `MockServerWebExchange`) and `@WebMvcTest` controller slices with validation and role checks.

## Deploy to Kubernetes / EKS

1. Push the repo to GitHub – CI publishes images to `ghcr.io/<owner>/order-platform-<service>`.
2. Edit `k8s/base/kustomization.yaml`: replace `YOUR_GITHUB_USERNAME`. (Make the GHCR packages public, or add an `imagePullSecret`.)
3. Create the namespace and secrets, then apply:

```bash
kubectl apply -f k8s/base/namespace.yaml
kubectl -n order-platform create secret generic platform-secrets \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=ADMIN_PASSWORD='<strong-password>' \
  --from-literal=DB_PASSWORD='<db-password>'

kubectl apply -k k8s/infra      # demo-grade Postgres, Redis, Kafka in-cluster
kubectl apply -k k8s/base       # the 5 services, HPA and ALB ingress
kubectl -n order-platform get pods,ingress
```

For production swap `k8s/infra` for managed services: **Amazon RDS** (PostgreSQL), **ElastiCache** (Redis), **Amazon MSK** (Kafka) – only the `DB_URL`, `REDIS_HOST` and `KAFKA_BOOTSTRAP_SERVERS` values change. The ingress needs the AWS Load Balancer Controller; metrics are exposed for Prometheus at `/actuator/prometheus`.

The `Deploy to EKS` workflow does the same from GitHub Actions using OIDC (no long-lived AWS keys).

## Design decisions & known trade-offs

- **Choreography over orchestration** keeps services decoupled; with more steps an orchestrator (Temporal / Camunda) would be clearer.
- **Kafka publish after commit** can still lose an event if the process dies between commit and send. The production fix is the **transactional outbox** pattern (listed below).
- Schema is managed with `ddl-auto: update` for brevity; use **Flyway** migrations in production.
- JWT uses a shared HS256 secret; production would use RS256/JWKS or an external IdP (Keycloak/Cognito).
- Downstream services trust gateway headers because they are ClusterIP-only; add NetworkPolicies / mTLS (service mesh) for zero-trust.

### Ideas to extend

- [ ] Transactional outbox + Debezium
- [ ] Kafka dead-letter topics & retry with backoff
- [ ] Testcontainers integration tests (PostgreSQL, Kafka, Redis)
- [ ] Flyway migrations
- [ ] OpenTelemetry tracing + Grafana dashboards
- [ ] Terraform for EKS/RDS/MSK/ElastiCache
- [ ] Argo CD (GitOps) instead of `kubectl apply`

## Project layout

```
common/             shared events, topics, JwtUtil
api-gateway/        Spring Cloud Gateway + JWT filter
auth-service/       users, BCrypt, token issuing
order-service/      orders + saga state machine
inventory-service/  products, reservations, Redis cache
payment-service/    payments (simulated)
docker/             shared multi-stage Dockerfile, DB init script
k8s/base, k8s/infra Kubernetes manifests (kustomize)
.github/workflows/  CI and EKS deploy
scripts/demo.sh     end-to-end demo
```

## Resume bullets (copy / adapt)

- Designed and built an event-driven **Spring Boot 3 / Java 17 microservices** platform (API Gateway, Auth, Order, Inventory, Payment) with database-per-service on **PostgreSQL** and asynchronous communication over **Apache Kafka**.
- Implemented a **saga pattern with compensating transactions**, idempotent consumers and after-commit event publishing; prevented overselling with atomic conditional stock updates.
- Secured the platform with **JWT + Spring Security** (BCrypt, RBAC at the gateway and service layer) and improved read latency with a **Redis** cache-aside layer.
- Containerised services with multi-stage **Docker** builds (non-root) and deployed to **Kubernetes/EKS** using Kustomize manifests with health probes, HPA, rolling updates and an ALB ingress.
- Built a **GitHub Actions** pipeline (Maven tests → Docker build → Trivy scan → GHCR push → OIDC deploy to EKS); covered business logic with **JUnit 5 / Mockito** and MockMvc tests.
