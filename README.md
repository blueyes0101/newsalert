# NewsAlert

A keyword-based news monitoring system that crawls the web every 15 minutes, notifies users by email when new articles match their saved keywords, and pushes live alerts to a desktop JavaFX client via WebSocket.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Docker Compose                           │
│                                                                 │
│  ┌──────────────┐   REST    ┌───────────────┐                  │
│  │ news-service │──────────▶│ alert-service │                  │
│  │  (port 8080) │◀──────────│  (port 8081)  │                  │
│  │              │  Kafka    │               │                  │
│  │  PostgreSQL  │──────────▶│  PostgreSQL   │                  │
│  │  Elasticsearch│          │  Elasticsearch│                  │
│  │  SearXNG     │           │  Email (SMTP) │                  │
│  └──────────────┘           └──────┬────────┘                  │
│                                    │ WebSocket                  │
│  ┌─────────────────┐               │ /ws/notifications          │
│  │  Infrastructure │               ▼                            │
│  │  • PostgreSQL   │        ┌──────────────┐                   │
│  │  • Elasticsearch│        │ JavaFX Client│                   │
│  │  • Kafka        │        │ System Tray  │                   │
│  │  • SearXNG      │        │ Popup Alerts │                   │
│  └─────────────────┘        └──────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

**news-service** — Quarkus 3 service (port 8080)
- Crawls SearXNG every 15 minutes for all active keywords
- Persists results to PostgreSQL; indexes them in Elasticsearch
- Exposes full-text search API (`GET /api/search`)
- Publishes Kafka events when new results are found

**alert-service** — Quarkus 3 service (port 8081)
- Manages users (JWT auth), alerts (keyword subscriptions), notification logs
- Consumes Kafka events and sends per-user email notifications
- Broadcasts real-time alerts to connected WebSocket clients

**client** — JavaFX 21 desktop application
- First-run setup wizard (register account + first keyword)
- System tray icon with keyword manager
- Receives WebSocket push notifications; shows bottom-right popup

---

## How to Run

### Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Java 21 + Maven 3.9 (to build the Quarkus services)
- An SMTP server accessible from the alert-service container (configure in `alert-service/src/main/resources/application.properties`)

### Steps

1. **Build the Quarkus services:**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Start all services:**
   ```bash
   docker compose up --build -d
   ```
   Services start in order: PostgreSQL + Elasticsearch + Zookeeper + SearXNG → Kafka → alert-service → news-service.
   Allow ~2 minutes for everything to become healthy.

3. **Verify all services are healthy:**
   ```bash
   docker compose ps
   ```

4. **Run the JavaFX client:**
   ```bash
   cd client
   mvn javafx:run
   ```
   On first launch, the setup wizard will guide you through creating an account and your first keyword alert.

5. **Stop everything:**
   ```bash
   docker compose down
   ```
   To also remove stored data:
   ```bash
   docker compose down -v
   ```

---

## How to Test

### news-service (port 8080)

Swagger UI: http://localhost:8080/q/swagger-ui

```bash
# Full-text search
GET http://localhost:8080/api/search?q=climate

# Search filtered by keyword and date
GET http://localhost:8080/api/search?q=climate&keyword=climate&from=2024-01-01&page=0&size=10

# Search with fuzzy matching (tolerates typos)
GET http://localhost:8080/api/search?q=climat&fuzzy=true

# Search with highlighted results
GET http://localhost:8080/api/search/highlighted?q=climate

# Get aggregation stats (count by source)
GET http://localhost:8080/api/search/stats

# Get stats filtered by keyword
GET http://localhost:8080/api/search/stats?keyword=climate

# Elasticsearch health
GET http://localhost:8080/api/search/health

# Trigger a manual crawler run (no need to wait 15 minutes)
POST http://localhost:8080/api/crawler/run

# Reindex all results in Elasticsearch
POST http://localhost:8080/api/search/reindex
```

### alert-service Search API (port 8081)

```bash
# Search alerts by keyword (with fuzzy matching)
GET http://localhost:8081/api/alerts/search?q=quarkus&fuzzy=true

# Search alerts with pagination
GET http://localhost:8081/api/alerts/search?q=quarkus&page=0&size=10
```

### alert-service (port 8081)

Swagger UI: http://localhost:8081/q/swagger-ui

```bash
# Register a user
POST http://localhost:8081/api/auth/register
{"email":"user@example.com","password":"secret"}

# Login (returns JWT)
POST http://localhost:8081/api/auth/login
{"email":"user@example.com","password":"secret"}

# Create a keyword alert (use JWT from login as Bearer token)
POST http://localhost:8081/api/alerts
Authorization: Bearer <token>
{"keyword":"climate change"}

# List your alerts
GET http://localhost:8081/api/alerts
Authorization: Bearer <token>

# List all active keywords (internal, no auth)
GET http://localhost:8081/api/internal/keywords
```

### WebSocket

Connect to `ws://localhost:8081/ws/notifications` with any WebSocket client.
After a crawler run finds new results, you will receive messages like:
```json
{"keyword":"climate change","count":3,"titles":["Title 1","Title 2","Title 3"]}
```

---

## Aufgabenstellung

Dieses Projekt ist eine Erweiterung des bestehenden NewsAlert-Backends aus dem Kurs *Verteilte Systeme 2*. Das Ziel dieser Erweiterung war es, das Thema **Hibernate Search mit Elasticsearch** praxisnah umzusetzen und dabei tief genug einzusteigen, dass Kommilitonen anhand des Codes und der Dokumentation verstehen, wie Volltextsuche in einer Quarkus-Anwendung funktioniert — und warum man dafür nicht einfach SQL-`LIKE` verwendet.

Konkret wollte ich folgende Funktionen nachweislich zum Laufen bringen:

- Volltextsuche über mehrere Felder (`title`, `snippet`) mit Fuzzy-Toleranz für Tippfehler
- Datumsbasierte Filterung und Sortierung nach Erscheinungsdatum
- Aggregationen: Trefferanzahl gruppiert nach Quelle (vergleichbar mit Facetten-Navigation in Online-Shops)
- Highlighting: Elasticsearch liefert Fragmente mit `<em>`-Tags um die gefundenen Begriffe zurück
- Multi-Entity-Indexierung: sowohl `SearchResult`- (news-service) als auch `Alert`-Entities (alert-service) sind indexiert und durchsuchbar
- Eigener Elasticsearch-Analyzer (`news_analyzer`) mit Lowercase, ASCII-Folding und English-Stemming für bessere Suchqualität
- Automatisches Reindexieren beim Neustart, falls der Index leer ist (z.B. nach einem `docker compose down -v`)

Erfolgskriterium war für mich: Alle Suchendpunkte antworten korrekt, die Integrationstests bestehen, und jemand der das Repo klont kann den Stack mit `docker compose up` starten und über die Swagger-UI alle Features selbst ausprobieren — ohne dass ich danebenstehen muss.

---

## Key Features

- **Automated crawling** — SearXNG meta-search runs every 15 minutes across Bing, Google, DuckDuckGo, Brave, and news-specific engines
- **Full-text search** — Elasticsearch-backed fuzzy search with date filtering and pagination
- **Search result highlighting** — Returns highlighted fragments showing matched terms in context
- **Search aggregations** — Stats endpoint shows document counts grouped by source
- **Multi-entity search** — Both SearchResult and Alert entities are indexed and searchable
- **Custom analyzer support** — Configurable token filters (lowercase, asciifolding, stemming) for better search quality
- **Startup auto-reindex** — Automatically rebuilds Elasticsearch index when empty
- **Per-user deduplication** — Each user only receives notifications for results they have not seen before
- **Email notifications** — HTML emails via Quarkus Mailer with article titles, snippets, and links
- **Real-time WebSocket push** — Instant desktop alerts without polling
- **JWT authentication** — RS256-signed tokens; BCrypt password hashing
- **Schema migrations** — Flyway manages the PostgreSQL schema
- **Resilient startup** — Docker health checks and ordered `depends_on` ensure services start safely
- **Desktop client** — JavaFX system-tray app with setup wizard, keyword manager, and toast-style notifications
- **Comprehensive tests** — Integration tests covering search, fuzzy matching, date filters, pagination, highlighting, and aggregations

---

## Hibernate Search

### Why Hibernate Search?

Standard SQL `LIKE` queries are inadequate for user-facing full-text search. They cannot rank results by relevance, handle typos, analyse language (stemming, stop-word removal), or scale to large datasets without full-table scans. Hibernate Search bridges the JPA entity model and a dedicated search engine — in this project Elasticsearch — so that entities are indexed automatically and queries are expressed in a type-safe Java API rather than raw JSON DSL.

### Entity Mapping

The `SearchResult` entity lives in PostgreSQL as a JPA entity and simultaneously in Elasticsearch as a search document. Three annotation families control how each field is indexed:

```java
@Entity
@Indexed                          // marks this entity for Hibernate Search indexing
public class SearchResult extends PanacheEntity {

    @FullTextField                // analysed: tokenised, lowercased, stemmed
    public String title;          //   → supports fuzzy / partial matching

    @FullTextField
    public String snippet;        // same analysis pipeline as title

    @KeywordField                 // NOT analysed: stored as a single token
    public String url;            //   → exact-match and deduplication only

    @KeywordField
    public String source;         // e.g. "bing", "duckduckgo" — exact filter

    @KeywordField
    public String keyword;        // the crawl keyword — exact filter in queries

    @GenericField(sortable = Sortable.YES)   // numeric/date field; no text analysis
    public LocalDateTime discoveredAt;       //   → used for sort(f.field("discoveredAt").desc())
}
```

**`@FullTextField`** applies the default analyser (standard tokeniser → lowercase filter → stop-word filter). This means a search for `"intelligence"` matches documents containing `"artificial intelligence"`, and a fuzzy search for `"inteligence"` still finds them.

**`@KeywordField`** skips analysis entirely. The value is indexed verbatim, making it suitable for structured filters (`keyword = "artificial intelligence"`) and deduplication by URL.

**`@GenericField(sortable = Sortable.YES)`** tells Hibernate Search to maintain a doc-values column in Elasticsearch for the field, which is required for efficient `sort()` operations. Sortable fields cannot be used for full-text predicates.

### Indexing Flow

Hibernate Search attaches JPA event listeners to the persistence context. When a `SearchResult` is persisted inside a `@Transactional` method, no explicit indexing call is needed:

```
SearXNG REST API
      │
      ▼
CrawlerService.persistNewResults()   ← @Transactional
      │  searchResultRepository.persist(entity)
      │
      ▼
Hibernate ORM (PostgreSQL write)
      │
      ▼ (on transaction commit)
Hibernate Search listener
      │
      ▼
Elasticsearch HTTP PUT /search_results/_doc/{id}
```

The index is updated synchronously on transaction commit (`write-sync` strategy), so by the time the Kafka event is published the document is already searchable.

### MassIndexer

The `POST /api/search/reindex` endpoint triggers a **MassIndexer** run. This is needed when:

- The Elasticsearch index is dropped and recreated (e.g. mapping change)
- The service starts with data already in PostgreSQL but an empty index
- An Elasticsearch node was unavailable and missed some live updates

```java
searchSession.massIndexer(SearchResult.class)
    .threadsToLoadObjects(4)   // parallel JDBC read threads
    .startAndWait();           // blocks until all documents are re-sent to ES
```

MassIndexer reads all rows from PostgreSQL in parallel batches and bulk-indexes them into Elasticsearch. It bypasses the JPA event-listener mechanism for performance, making it far faster than re-persisting entities one by one.

### Search Implementation

Queries are built through `SearchSession`, which is injected as a CDI bean by the `quarkus-hibernate-search-orm-elasticsearch` extension:

```java
@Inject
SearchSession searchSession;

var result = searchSession
    .search(SearchResult.class)
    .where(f -> f.bool()
        // fuzzy(1): tolerate 1 character difference (edit distance)
        .must(f.match()
            .fields("title", "snippet")
            .matching(queryText)
            .fuzzy(1))
        // exact filter on the @KeywordField — not scored, just a hard constraint
        .filter(f.match()
            .field("keyword")
            .matching(keyword)))
    // sort on the @GenericField(sortable=YES) field
    .sort(f -> f.field("discoveredAt").desc())
    // paginate: skip `page * size` hits, return at most `size`
    .fetch(page * size, size);

long totalHits = result.total().hitCount();
List<SearchResult> hits = result.hits();
```

Key points:
- `.must()` — the predicate must match; contributes to the relevance score
- `.filter()` — the predicate must match; does **not** affect scoring (faster)
- `.fuzzy(1)` — Damerau-Levenshtein edit distance of 1; catches single-character typos
- The query targets `SearchResult.class` directly; Hibernate Search translates field names and types, preventing mapping errors at compile time

### Schema Management

`application.properties` sets:

```properties
quarkus.hibernate-search-orm.schema-management.strategy=create-or-validate
```

On startup Hibernate Search inspects the Elasticsearch index:

| Index state | Action |
|-------------|--------|
| Does not exist | Create it from the entity mapping |
| Exists and mapping matches | Do nothing |
| Exists but mapping conflicts | Throw an exception — prevents silent data corruption |

The `drop-and-create-and-drop` strategy used in the `%dev` profile additionally drops and recreates the index on shutdown, keeping the development environment clean between restarts.

### Why Elasticsearch Over the Lucene Backend?

Hibernate Search supports two backends: an embedded Lucene engine and a remote Elasticsearch cluster. This project uses Elasticsearch for several practical reasons:

| Concern | Lucene backend | Elasticsearch backend |
|---------|---------------|----------------------|
| Deployment | In-process, no extra container | Separate Docker service |
| Index visibility | Only the JVM that owns it | Any REST client, Kibana, curl |
| Scalability | Single JVM | Distributed shards and replicas |
| Operations | Restarts lose in-memory state | Persistent, independently restartable |
| Docker-friendliness | N/A | Official image, health-check endpoint |

Running Elasticsearch as a container means the index survives service restarts, can be inspected with any HTTP client (`GET http://localhost:9200/search_results/_search`), and would scale horizontally if the dataset grew beyond a single node.
