# 🔍 Search Typeahead System

A full-stack, production-inspired **Search Typeahead / Autocomplete** system built as a High-Level Design (HLD) assignment. The system demonstrates a real-world architecture with progressive complexity — from a simple REST API to distributed caching, consistent hashing, recency-aware ranking, and batched writes.

---

## 📐 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     React Frontend (Vite)                       │
│   http://localhost:5173  — Google-style search UI               │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTP (REST)
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│               Spring Boot Backend  :8081                        │
│                                                                 │
│   GET  /suggest?q=<prefix>&mode=basic|trending                  │
│        └─► Consistent Hash Ring (MD5, 160 vnodes/node)          │
│            └─► Redis Node 1/2/3 (cache-aside, TTL 60s)          │
│                └─► PostgreSQL (on cache MISS)                   │
│                                                                 │
│   POST /search  { "query": "..." }                              │
│        └─► In-Memory Batch Buffer (ConcurrentHashMap)           │
│            └─► @Scheduled flush every 10s (or 500 entries)      │
│                └─► Batch UPSERT → PostgreSQL                    │
│                └─► Cache invalidation (both modes, all prefixes)│
│                                                                 │
│   GET  /batch/debug   — write reduction metrics                 │
│   GET  /cache/debug   — ring routing & cache state              │
└─────────────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   Redis Node 1     Redis Node 2     Redis Node 3
     :6379             :6380             :6381
          └───────────────┴───────────────┘
                          │
                          ▼
                    PostgreSQL :5432
                   (typeahead DB)
```

---

## 🧩 Implementation Phases

| Phase | Feature |
|-------|---------|
| 1 | Project skeleton (Spring Boot + PostgreSQL + JPA) |
| 2 | Dataset loading from CSV (`queries_aggregated.csv`) |
| 3 | `GET /suggest` — prefix search, sorted by count |
| 4 | `POST /search` — atomic UPSERT (insert or increment) |
| 5 | Redis cache-aside (single node, 60s TTL) |
| 6 | 3-node Redis + Consistent Hashing (MD5, 160 virtual nodes) |
| 7 | React Frontend (Google-style UI, debounced, keyboard nav) |
| 8 | Trending/Recency ranking (exponential decay scoring) |
| 9 | Batch writes (in-memory buffer, `@Scheduled` flush) |

---

## 📋 Prerequisites

Make sure you have all of these installed before starting:

| Tool | Minimum Version | Check Command |
|------|----------------|---------------|
| Java (JDK) | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Node.js | 18+ | `node -version` |
| npm | 9+ | `npm -version` |
| Docker Desktop | Any recent | `docker -version` |

---

## 🚀 Quick Start (Step-by-Step)

### Step 1 — Clone the Repository

```bash
git clone https://github.com/AUXID-01/HLD-Assignment.git
cd HLD-Assignment/search-typeahead
```

### Step 2 — Start Infrastructure (PostgreSQL + 3 Redis Nodes)

```bash
docker-compose up -d
```

This starts 4 containers:

| Container | Service | Port |
|-----------|---------|------|
| `typeahead-postgres` | PostgreSQL 15 | `5432` |
| `typeahead-redis-1` | Redis 7 | `6379` |
| `typeahead-redis-2` | Redis 7 | `6380` |
| `typeahead-redis-3` | Redis 7 | `6381` |

Verify containers are running:
```bash
docker ps
```

### Step 3 — Start the Backend

```bash
cd backend
mvn spring-boot:run
```

Wait until you see:
```
Started SearchApplication in X.XXX seconds
```

> **First run only:** The app auto-loads the query dataset from `src/main/resources/data/queries_aggregated.csv` into PostgreSQL. This takes a few seconds. Subsequent runs skip this step.

The backend runs on **http://localhost:8081**.

### Step 4 — Start the Frontend

Open a **second terminal**:

```bash
cd frontend
npm install      # only needed once
npm run dev
```

The frontend runs on **http://localhost:5173**.

Open your browser at **http://localhost:5173** and start typing!

---

## ⚙️ Configuration

All configurable values live in [`backend/src/main/resources/application.properties`](backend/src/main/resources/application.properties):

```properties
# Server
server.port=8081

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/typeahead
spring.datasource.username=postgres
spring.datasource.password=postgres

# Redis Nodes (Consistent Hashing)
redis.nodes[0].host=localhost
redis.nodes[0].port=6379
redis.nodes[0].name=redis-node-1

redis.nodes[1].host=localhost
redis.nodes[1].port=6380
redis.nodes[1].name=redis-node-2

redis.nodes[2].host=localhost
redis.nodes[2].port=6381
redis.nodes[2].name=redis-node-3

# Batch Write Tuning
batch.flush-interval-ms=10000   # flush every 10 seconds
batch.flush-size=500            # or immediately if buffer hits 500 entries
```

---

## 📡 API Reference

### `GET /suggest`

Returns up to 10 autocomplete suggestions for a given prefix.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | yes | Search prefix (e.g. `"iph"`) |
| `mode` | string | no | `basic` (default) or `trending` |

**Basic mode** — sorted by all-time search count (descending).

**Trending mode** — sorted by exponential decay score:
```
score = count × e^(−0.0289 × hours_since_last_searched)
```
A 24-hour half-life means a query not searched in 24h retains only 50% of its score.

**Example:**
```bash
# Basic (default)
curl "http://localhost:8081/suggest?q=iph"

# Trending
curl "http://localhost:8081/suggest?q=iph&mode=trending"
```

**Response (basic):**
```json
[
  { "query": "iphone 13", "count": 4821, "score": null },
  { "query": "iphone 15 pro", "count": 3107, "score": null }
]
```

**Response (trending):**
```json
[
  { "query": "iphone 15 pro", "count": 3107, "score": 3094.23 },
  { "query": "iphone 13", "count": 4821, "score": 12.07 }
]
```

**Response Headers:**
```
X-Cache: HIT | MISS
X-Cache-Node: redis-node-1 | redis-node-2 | redis-node-3
```

---

### `POST /search`

Records a user search. Updates the database (batched) and invalidates the cache.

```bash
curl -X POST http://localhost:8081/search \
  -H "Content-Type: application/json" \
  -d '{"query": "iphone 15"}'
```

**Response:**
```json
{ "message": "Searched" }
```

**Error (blank query):**
```json
{ "error": "query cannot be missing or blank" }
```

> **How it works (Phase 9):** The request returns instantly. The query is added to an in-memory buffer. Every 10 seconds (or when the buffer reaches 500 entries), a background thread flushes all buffered queries to PostgreSQL in a single batch UPSERT per unique query — reducing DB load significantly under high traffic.

---

### `GET /batch/debug`

Observe the write-reduction effectiveness of batch buffering.

```bash
curl "http://localhost:8081/batch/debug"
```

**Response:**
```json
{
  "currentBufferSize": 5,
  "totalSearchesReceived": 53,
  "totalDbWrites": 8,
  "writeReductionRatio": "6.63x",
  "summary": "53 searches in, 8 DB writes out — 6.63x reduction"
}
```

---

### `GET /cache/debug`

Inspect which Redis node owns a given key and whether it's currently cached.

```bash
curl "http://localhost:8081/cache/debug?prefix=iphone"
```

---

## 🖥️ Frontend Usage

Open **http://localhost:5173** in your browser.

| Action | How |
|--------|-----|
| Get suggestions | Start typing in the search box |
| Navigate suggestions | `↑` / `↓` arrow keys |
| Select suggestion | `Enter` or click it |
| Submit typed query | `Enter` (with no suggestion highlighted) or click **Google Search** |
| Close dropdown | `Escape` |
| Switch ranking mode | Click **Basic Mode** or **Trending Mode** toggle above the search bar |

The search count `(N)` is always shown. In Trending Mode, the decay score `⭐ X.XX` is shown alongside it so you can visually compare ranking differences.

---

## 🧪 Testing the System

### Test 1 — Verify Suggestions Work

```bash
curl "http://localhost:8081/suggest?q=play"
```

Expected: list of queries starting with "play" (e.g. "playstation", "player", etc.)

### Test 2 — Verify Cache (HIT vs MISS)

```bash
# First call → MISS (hits DB, writes to Redis)
curl -v "http://localhost:8081/suggest?q=play" 2>&1 | grep "X-Cache"
# X-Cache: MISS

# Second call → HIT (served from Redis)
curl -v "http://localhost:8081/suggest?q=play" 2>&1 | grep "X-Cache"
# X-Cache: HIT
```

### Test 3 — Verify Cache Invalidation

```bash
# 1. Prime the cache
curl "http://localhost:8081/suggest?q=samsung"

# 2. Search "samsung" (buffers the write)
curl -X POST http://localhost:8081/search -H "Content-Type: application/json" -d '{"query":"samsung"}'

# 3. Wait 10s for flush, then check suggest — should be a MISS again (cache was invalidated)
curl -v "http://localhost:8081/suggest?q=samsung" 2>&1 | grep "X-Cache"
```

### Test 4 — Verify Batch Write Reduction

```powershell
# Fire 30 searches, then immediately check debug (writes should still be 0 or low)
for ($i = 0; $i -lt 30; $i++) {
    Invoke-RestMethod -Uri "http://localhost:8081/search" -Method Post `
        -ContentType "application/json" -Body '{"query": "xbox"}'
}
Invoke-RestMethod -Uri "http://localhost:8081/batch/debug" -Method Get

# Wait 12s, check again — buffer should be 0, DB writes increased by just 1
Start-Sleep -Seconds 12
Invoke-RestMethod -Uri "http://localhost:8081/batch/debug" -Method Get
```

Expected: 30 searches collapsed into **1 DB write**.

### Test 5 — Compare Basic vs Trending

```bash
# Search "iphone 15" several times to boost its recency score
for i in {1..5}; do
  curl -s -X POST http://localhost:8081/search \
    -H "Content-Type: application/json" \
    -d '{"query":"iphone 15"}'
done

# Wait for flush (10s), then compare
curl "http://localhost:8081/suggest?q=iphone&mode=basic"
curl "http://localhost:8081/suggest?q=iphone&mode=trending"
```

"iphone 15" should appear higher in trending mode than in basic mode.

---

## 📁 Project Structure

```
search-typeahead/
├── docker-compose.yml              # PostgreSQL + 3 Redis nodes
│
├── backend/                        # Spring Boot application
│   ├── pom.xml
│   └── src/main/java/com/typeahead/search/
│       ├── SearchApplication.java          # Main class (@EnableScheduling)
│       ├── entity/
│       │   └── Query.java                  # JPA entity (id, query, count, last_searched_at)
│       ├── repository/
│       │   └── QueryRepository.java        # JPA repo + native UPSERT queries
│       ├── dto/
│       │   └── SuggestResponse.java        # Response shape (query, count, score)
│       ├── config/
│       │   ├── RedisConfig.java            # One StringRedisTemplate per Redis node
│       │   ├── RedisProperties.java        # Typed config (redis.nodes[])
│       │   └── WebConfig.java              # CORS (allows localhost:5173)
│       ├── component/
│       │   ├── ConsistentHashRouter.java   # MD5 ring, 160 vnodes/node
│       │   ├── DataLoader.java             # CSV → PostgreSQL on first boot
│       │   ├── BufferedEntry.java          # AtomicLong count + volatile timestamp
│       │   ├── SearchBuffer.java           # AtomicReference<ConcurrentHashMap> buffer
│       │   └── BatchFlusher.java           # @Scheduled flush + size threshold flush
│       └── controller/
│           ├── SuggestController.java      # GET /suggest (cache-aside, mode routing)
│           ├── SearchController.java       # POST /search (delegates to BatchFlusher)
│           ├── BatchDebugController.java   # GET /batch/debug
│           └── DebugController.java        # GET /cache/debug
│
└── frontend/                       # React + Vite application
    ├── package.json
    └── src/
        ├── main.jsx
        ├── App.jsx                 # Search component (debounced, keyboard nav, mode toggle)
        └── App.css                 # Google-style UI
```

---

## 🔑 Key Design Decisions

### Consistent Hashing (Phase 6)
- **Hash function:** MD5 (stable, uniform distribution; cryptographic strength not needed)
- **Virtual nodes:** 160 per physical node = 480 ring positions total
  - Provides ~5–10% standard deviation in key distribution across 3 nodes
  - Same default used by Memcached and Jedis
- **Deterministic routing:** `suggest:basic:iph` always maps to the same Redis node on every request

### Exponential Decay Ranking (Phase 8)
- **Formula:** `score = count × e^(−0.0289 × hours_elapsed)`
- **Half-life:** 24 hours — a query not searched in 24h retains 50% of its score
- **Computed in PostgreSQL** using `EXP()` and `EXTRACT(EPOCH FROM ...)` at query time
- Basic and trending results use **separate cache keys** (`suggest:basic:*` vs `suggest:trending:*`) so they never bleed into each other

### Batch Write Buffer (Phase 9)
- **Buffer:** `AtomicReference<ConcurrentHashMap<String, BufferedEntry>>`
- **Atomic drain:** `getAndSet(new ConcurrentHashMap<>())` — the live map is swapped in a single CAS operation; no locks held during DB writes
- **Double-flush safety:** `flush()` is `synchronized` — size-based and time-based triggers can never process the same entries twice
- **Known trade-off:** Entries in the buffer at crash time are lost (no WAL/durable queue). Documented in `SearchBuffer.java`.

---

## 🐛 Troubleshooting

| Problem | Likely Cause | Fix |
|---------|-------------|-----|
| `Connection refused` on startup | Docker containers not running | `docker-compose up -d` |
| `Port 8081 already in use` | Another process on 8081 | Kill it or change `server.port` in `application.properties` |
| `/suggest` returns empty list | Dataset not loaded | Check logs for "Data load complete" on first boot |
| Cache always MISS | Redis containers stopped | `docker ps` and restart containers |
| Frontend shows "Something went wrong" | Backend not running | Start backend with `mvn spring-boot:run` |
| `mvn` not found | Maven not installed or not in PATH | Install Maven or use `./mvnw` |

---

## 📜 License

This project is for academic/educational purposes as part of an HLD (High-Level Design) course assignment.
