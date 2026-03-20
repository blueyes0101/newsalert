# NewsAlert — Complete Project Walkthrough

A tutorial-style guide that walks through every layer of the system, from Docker to
JavaFX, so you can understand, run, and verify the project yourself. Every class
name, endpoint, configuration key, and command is taken directly from the codebase.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Infrastructure and Docker Setup](#2-infrastructure-and-docker-setup)
3. [Database Layer](#3-database-layer)
4. [Hibernate Search Deep Dive](#4-hibernate-search-deep-dive)
5. [Kafka Messaging](#5-kafka-messaging)
6. [Scheduled Tasks](#6-scheduled-tasks)
7. [REST API Complete Reference](#7-rest-api-complete-reference)
8. [WebSocket](#8-websocket)
9. [JavaFX Client](#9-javafx-client)
10. [Testing](#10-testing)
11. [Full Demo Walkthrough](#11-full-demo-walkthrough)

---

## 1. Project Overview

NewsAlert is a two-microservice platform that continuously monitors the web for news
articles matching user-defined keywords and notifies subscribers in real time. A
background crawler (running inside **news-service**) polls the SearXNG meta-search
engine every 15 minutes for each active keyword, deduplicates results against
PostgreSQL, and persists new articles. Hibernate Search automatically mirrors every
persisted row into an Elasticsearch index, enabling fast full-text search with fuzzy
matching, highlighting, and aggregation. When new articles are found, a Kafka event
is published; **alert-service** consumes it, sends HTML email notifications to
matching subscribers, and broadcasts a WebSocket push to connected desktop clients.
A JavaFX tray application lets users manage their keyword subscriptions and see
instant popup notifications.

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│  User's machine                                                      │
│  ┌─────────────────┐          WebSocket ws://localhost:8081/ws/     │
│  │  JavaFX Client  │◄──────────/notifications──────────────────┐    │
│  │  (no fixed port)│           REST  http://localhost:8080      │    │
│  └────────┬────────┘           REST  http://localhost:8081      │    │
└───────────┼────────────────────────────────────────────────────┼────┘
            │                                                     │
┌───────────▼───────────┐      ┌──────────────────────────────────▼─┐
│   news-service :8080  │      │      alert-service :8081           │
│  Quarkus / JAX-RS     │      │  Quarkus / JAX-RS / JWT            │
│  ┌─────────────────┐  │      │  ┌──────────────────────────────┐  │
│  │  CrawlerService │  │      │  │  NewResultsConsumer (Kafka)  │  │
│  │  @Scheduled 15m │  │      │  │  NotificationService         │  │
│  └────────┬────────┘  │      │  │  AlertResource / AuthResource│  │
│           │           │      │  └──────────────────────────────┘  │
│  ┌────────▼────────┐  │      └───────────┬───────────┬────────────┘
│  │ SearchResource  │  │                  │           │
│  │ /api/search     │  │                  │           │
│  └─────────────────┘  │                  │           │
└──┬──────────┬──────┬──┘                  │           │
   │          │      │                     │           │
   ▼          ▼      ▼                     ▼           ▼
PostgreSQL  Elastic  Kafka ◄──────── Kafka         PostgreSQL
:5432       :9200    :9092  new-results topic       :5432
                      │                             (shared DB)
                      ▼
                  SearXNG :8888
         (Bing, Google, DuckDuckGo, Brave,
          Google News, Bing News)
```

**Services and ports:**

| Service        | Host port | Internal port | Role                                          |
|----------------|-----------|---------------|-----------------------------------------------|
| news-service   | 8080      | 8080          | Crawler, full-text search API                 |
| alert-service  | 8081      | 8081          | Auth, alert management, notifications, WS     |
| PostgreSQL     | 5432      | 5432          | Relational store (shared schema)              |
| Elasticsearch  | 9200      | 9200          | Full-text search index                        |
| Kafka          | 9092      | 9092 / 29092  | Async messaging (new-results topic)           |
| Zookeeper      | 2181      | 2181          | Kafka coordination                            |
| SearXNG        | 8888      | 8080          | Meta-search engine (news crawler source)      |

### Tech Stack

| Layer            | Technology                                          |
|------------------|-----------------------------------------------------|
| Microservices    | Quarkus 3.15.1 (JAX-RS, CDI, Panache)              |
| Search           | Hibernate Search 6 + Elasticsearch 8.13.0           |
| Database         | PostgreSQL 16 + Flyway (alert-service migrations)   |
| Messaging        | Apache Kafka 7.5.0 (Confluent images)               |
| Web crawling     | SearXNG (meta-search, multiple engines)             |
| Security         | SmallRye JWT (RS256, 24-hour tokens)                |
| Email            | Quarkus Mailer + Qute templates                     |
| WebSocket        | Jakarta WebSocket API                               |
| Desktop client   | JavaFX 21.0.2 + OkHttp 4.12.0 + Gson 2.11.0        |
| Build            | Maven (multi-module: news-service, alert-service,   |
|                  | client, demo)                                       |
| Java version     | 21                                                  |

---

## 2. Infrastructure and Docker Setup

### Services in docker-compose.yml

#### postgres

```yaml
image: postgres:16
container_name: (service name) postgres
ports: 5432:5432
environment:
  POSTGRES_DB:       newsalert
  POSTGRES_USER:     newsalert
  POSTGRES_PASSWORD: newsalert
volumes: postgres_data:/var/lib/postgresql/data
healthcheck: pg_isready -U newsalert -d newsalert
  interval: 10s | timeout: 5s | retries: 5 | start_period: 10s
```

Single PostgreSQL 16 instance used by **both** microservices. The alert-service
creates its tables via Flyway migrations on startup. The news-service creates the
`search_results` table via Hibernate ORM (`database.generation=update`).

#### elasticsearch

```yaml
image: elasticsearch:8.13.0
ports: 9200:9200
environment:
  discovery.type: single-node
  xpack.security.enabled: false
  ES_JAVA_OPTS: -Xms512m -Xmx512m
  cluster.routing.allocation.disk.threshold_enabled: false
volumes: elasticsearch_data:/usr/share/elasticsearch/data
healthcheck: curl -sf 'http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=5s'
  interval: 15s | timeout: 10s | retries: 10 | start_period: 30s
```

Single-node Elasticsearch 8.13 with security disabled (development setup). The
`-Xms512m -Xmx512m` heap cap keeps it suitable for a laptop demo.

#### zookeeper

```yaml
image: confluentinc/cp-zookeeper:7.5.0
ports: 2181:2181
environment:
  ZOOKEEPER_CLIENT_PORT: 2181
  ZOOKEEPER_TICK_TIME: 2000
  ZOOKEEPER_4LW_COMMANDS_WHITELIST: "ruok,stat,srvr,mntr"
healthcheck: echo srvr | nc -w 2 localhost 2181 | grep -q 'Zookeeper version'
  interval: 10s | timeout: 5s | retries: 5 | start_period: 15s
```

Required by Kafka for coordination. Kafka waits for Zookeeper to be healthy before
starting (via `depends_on: condition: service_healthy`).

#### kafka

```yaml
image: confluentinc/cp-kafka:7.5.0
ports: 9092:9092
environment:
  KAFKA_BROKER_ID: 1
  KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
  KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
  KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
  KAFKA_LOG_RETENTION_HOURS: 48
depends_on: zookeeper (healthy)
healthcheck: kafka-broker-api-versions --bootstrap-server localhost:9092
  interval: 15s | timeout: 10s | retries: 10 | start_period: 30s
```

Kafka auto-creates the `new-results` topic on first publish. Internal Docker traffic
uses port 29092 (`kafka:29092`); external access (e.g. from a local terminal) uses
9092.

#### searxng

```yaml
image: searxng/searxng:latest
ports: 8888:8080
volumes: ./searxng:/etc/searxng:rw
environment:
  SEARXNG_BASE_URL: http://localhost:8888/
healthcheck: wget -qO- http://localhost:8080/healthz || curl -sf http://localhost:8080/
  interval: 15s | timeout: 10s | retries: 5 | start_period: 15s
```

Meta-search aggregating Bing, Google, DuckDuckGo, Brave, Google News, and Bing News
(configured in `searxng/settings.yml`). Exposed on host port **8888**.

#### alert-service

```yaml
build: ./alert-service / src/main/docker/Dockerfile.jvm
ports: 8081:8081
environment:
  QUARKUS_HTTP_PORT: 8081
  QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/newsalert
  QUARKUS_HIBERNATE_SEARCH_ORM_ELASTICSEARCH_HOSTS: elasticsearch:9200
  KAFKA_BOOTSTRAP_SERVERS: kafka:29092
  QUARKUS_REST_CLIENT_NEWS_SERVICE_URL: http://news-service:8080
depends_on: postgres (healthy), elasticsearch (healthy), kafka (healthy)
healthcheck: curl -sf http://localhost:8081/q/health/live
  interval: 20s | timeout: 10s | retries: 8 | start_period: 60s
```

Starts before news-service so that `/api/internal/keywords` is available when the
first crawler cycle fires.

#### news-service

```yaml
build: ./news-service / src/main/docker/Dockerfile.jvm
ports: 8080:8080
environment:
  QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/newsalert
  QUARKUS_HIBERNATE_SEARCH_ORM_ELASTICSEARCH_HOSTS: elasticsearch:9200
  KAFKA_BOOTSTRAP_SERVERS: kafka:29092
  QUARKUS_REST_CLIENT_SEARXNG_URL: http://searxng:8080
  QUARKUS_REST_CLIENT_ALERT_SERVICE_URL: http://alert-service:8081
depends_on: postgres (healthy), elasticsearch (healthy), kafka (healthy), alert-service (healthy)
healthcheck: curl -sf http://localhost:8080/q/health/live
  interval: 20s | timeout: 10s | retries: 8 | start_period: 60s
```

### How to Start

```bash
# From the project root directory:
docker compose up --build -d
```

`--build` compiles and packages both Quarkus services before starting containers.
`-d` runs everything in the background.

### How to Stop

```bash
docker compose down
```

To also remove all persistent volumes (wipes database and Elasticsearch data):

```bash
docker compose down -v
```

### Verify All Containers Are Running

```bash
docker compose ps
```

You should see 7 services all with status `running` (or `healthy`).

### Check Elasticsearch is Healthy

```bash
curl -s http://localhost:9200/_cluster/health | python3 -m json.tool
```

Expected: `"status": "yellow"` or `"status": "green"`. Yellow is normal for a
single-node cluster with no replicas.

### Check PostgreSQL is Accessible

```bash
docker compose exec postgres psql -U newsalert -d newsalert -c "\dt"
```

Expected: a table listing including `users`, `alerts`, `notification_logs`,
`search_results`.

---

## 3. Database Layer

### Databases

Both microservices share one PostgreSQL 16 instance:

| Setting  | Value                                          |
|----------|------------------------------------------------|
| Host     | `localhost` (external) / `postgres` (Docker)  |
| Port     | `5432`                                         |
| Database | `newsalert`                                    |
| Username | `newsalert`                                    |
| Password | `newsalert`                                    |

### Flyway Migrations (alert-service)

Flyway runs on alert-service startup (`quarkus.flyway.migrate-at-start=true`).
Location: `alert-service/src/main/resources/db/migration/`

#### V1__init.sql

Creates the core schema:

```sql
CREATE TABLE users (
    id            BIGSERIAL     PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users (email);

CREATE TABLE alerts (
    id         BIGSERIAL    PRIMARY KEY,
    keyword    VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_alerts_user_id ON alerts (user_id);
CREATE INDEX idx_alerts_active  ON alerts (active);

CREATE TABLE notification_logs (
    id           BIGSERIAL     PRIMARY KEY,
    result_url   VARCHAR(2048),
    result_title VARCHAR(512),
    sent_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    alert_id     BIGINT        NOT NULL REFERENCES alerts (id) ON DELETE CASCADE
);
CREATE INDEX idx_notification_logs_alert_id ON notification_logs (alert_id);
CREATE INDEX idx_notification_logs_sent_at  ON notification_logs (sent_at);
```

#### V2__add_sequences.sql

Hibernate 6 resolves `GenerationType.AUTO` to `SEQUENCE` and expects sequences named
`<table>_seq`. This migration creates them so Hibernate can generate IDs without
clashing with the BIGSERIAL implicit sequences:

```sql
CREATE SEQUENCE IF NOT EXISTS users_seq             START 1 INCREMENT 50;
CREATE SEQUENCE IF NOT EXISTS alerts_seq            START 1 INCREMENT 50;
CREATE SEQUENCE IF NOT EXISTS notification_logs_seq START 1 INCREMENT 50;
```

The `search_results` table in news-service is managed by Hibernate ORM directly
(`quarkus.hibernate-orm.database.generation=update`), not Flyway.

### Entity Classes

#### SearchResult

File: `news-service/src/main/java/com/newsalert/news/entity/SearchResult.java`

```java
@Entity
@Table(name = "search_results")
@Indexed
public class SearchResult extends PanacheEntity {

    @FullTextField(analyzer = "news_analyzer", highlightable = Highlightable.ANY)
    @Column(name = "title", length = 512)
    public String title;

    @FullTextField(analyzer = "news_analyzer", highlightable = Highlightable.ANY)
    @Column(name = "snippet", columnDefinition = "TEXT")
    public String snippet;

    @KeywordField
    @Column(name = "url", unique = true, length = 2048, nullable = false)
    public String url;

    @KeywordField
    @Column(name = "source", length = 128)
    public String source;

    @KeywordField
    @Column(name = "keyword", nullable = false, length = 255)
    public String keyword;

    @GenericField(sortable = Sortable.YES)
    @Column(name = "discovered_at", nullable = false)
    public LocalDateTime discoveredAt;

    @Column(name = "already_notified", nullable = false)
    public boolean alreadyNotified = false;
}
```

| Field           | JPA annotation             | HS annotation                                               | Purpose                                     |
|-----------------|----------------------------|-------------------------------------------------------------|---------------------------------------------|
| title           | @Column(length=512)        | @FullTextField(analyzer="news_analyzer", highlightable=ANY) | Full-text search + highlight support        |
| snippet         | @Column(TEXT)              | @FullTextField(analyzer="news_analyzer", highlightable=ANY) | Full-text search + highlight support        |
| url             | @Column(unique, 2048)      | @KeywordField                                               | Exact-match deduplication                   |
| source          | @Column(128)               | @KeywordField                                               | Aggregation by search engine name           |
| keyword         | @Column(255)               | @KeywordField                                               | Exact-match filter on keyword               |
| discoveredAt    | @Column                    | @GenericField(sortable=YES)                                 | Sort by discovery time, date-range filter   |
| alreadyNotified | @Column                    | (none)                                                      | Notification deduplication flag             |

#### Alert

File: `alert-service/src/main/java/com/newsalert/alert/entity/Alert.java`

```java
@Entity
@Table(name = "alerts")
@Indexed
public class Alert extends PanacheEntity {

    @Column(name = "keyword", nullable = false)
    @FullTextField
    @KeywordField
    public String keyword;

    @Column(name = "active", nullable = false)
    @KeywordField
    public boolean active = true;

    @Column(name = "created_at", nullable = false)
    @GenericField(sortable = Sortable.YES)
    public LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @IndexedEmbedded
    public User user;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<NotificationLog> notifications = new ArrayList<>();
}
```

| Field     | JPA annotation    | HS annotation                     | Purpose                                    |
|-----------|-------------------|-----------------------------------|--------------------------------------------|
| keyword   | @Column           | @FullTextField + @KeywordField    | Full-text search AND exact-match filter    |
| active    | @Column           | @KeywordField                     | Exact-match filter (true/false)            |
| createdAt | @Column           | @GenericField(sortable=YES)       | Sort alerts by creation date               |
| user      | @ManyToOne (LAZY) | @IndexedEmbedded                  | Embeds user.email into the alert ES doc so you can search/filter alerts by email |

`@IndexedEmbedded` on `user` means the User entity's indexed fields (specifically
`email`, which carries `@FullTextField` and `@KeywordField`) are embedded inside the
Alert's Elasticsearch document. This allows `AlertSearchResource` to search alerts
by user email without a separate query.

#### User

File: `alert-service/src/main/java/com/newsalert/alert/entity/User.java`

```java
@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @Column(name = "email", nullable = false, unique = true)
    @FullTextField
    @KeywordField
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Alert> alerts = new ArrayList<>();
}
```

User is not annotated `@Indexed` directly — its fields are indexed only through the
`@IndexedEmbedded` on Alert. `@FullTextField` on email allows full-text search;
`@KeywordField` allows exact-match lookup.

#### NotificationLog

File: `alert-service/src/main/java/com/newsalert/alert/entity/NotificationLog.java`

```java
@Entity
@Table(name = "notification_logs")
public class NotificationLog extends PanacheEntity {

    @Column(name = "result_url", length = 2048)
    public String resultUrl;

    @Column(name = "result_title", length = 512)
    public String resultTitle;

    @Column(name = "sent_at", nullable = false)
    public LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    public Alert alert;
}
```

Not indexed — used purely for relational deduplication (checking whether a URL has
already been notified for a given alert).

---

## 4. Hibernate Search Deep Dive

### 4.1 Configuration

#### news-service `application.properties`

```properties
quarkus.hibernate-search-orm.elasticsearch.version=8
quarkus.hibernate-search-orm.elasticsearch.hosts=elasticsearch:9200
quarkus.hibernate-search-orm.elasticsearch.protocol=HTTP
quarkus.hibernate-search-orm.schema-management.strategy=create-or-validate
quarkus.hibernate-search-orm.automatic-indexing.synchronization.strategy=write-sync
quarkus.hibernate-search-orm.elasticsearch.analysis.configurer=class:com.newsalert.news.search.NewsAnalysisConfigurer

newsalert.search.auto-reindex-on-empty=true
newsalert.search.reindex-threads=2
newsalert.search.reindex-batch-size=25
```

| Property | Value | What it does |
|----------|-------|--------------|
| `elasticsearch.version` | `8` | Tells Hibernate Search to use the Elasticsearch 8 REST client dialect |
| `elasticsearch.hosts` | `elasticsearch:9200` | Elasticsearch address (Docker service name inside compose; `localhost:9200` in dev profile) |
| `elasticsearch.protocol` | `HTTP` | Plain HTTP (security disabled in compose) |
| `schema-management.strategy` | `create-or-validate` | On startup, create the ES index if it does not exist; if it exists, validate the mapping. In dev: `drop-and-create-and-drop` (fresh index every run). |
| `automatic-indexing.synchronization.strategy` | `write-sync` | After `persist()` inside a transaction, Hibernate Search waits for Elasticsearch to confirm the document was written before returning. Guarantees that a search immediately after a persist will find the document. |
| `analysis.configurer` | `class:com.newsalert.news.search.NewsAnalysisConfigurer` | Registers the custom `news_analyzer` (see §4.3) |
| `newsalert.search.auto-reindex-on-empty` | `true` | `SearchIndexInitializer` triggers a MassIndexer if the ES index is empty at startup |
| `newsalert.search.reindex-threads` | `2` | Threads used by the startup MassIndexer |
| `newsalert.search.reindex-batch-size` | `25` | Batch size used by the startup MassIndexer |

#### alert-service `application.properties`

```properties
quarkus.hibernate-search-orm.elasticsearch.hosts=elasticsearch:9200
quarkus.hibernate-search-orm.elasticsearch.protocol=HTTP
quarkus.hibernate-search-orm.schema-management.strategy=create-or-validate
quarkus.hibernate-search-orm.automatic-indexing.synchronization.strategy=async
```

**Key differences from news-service:**

| Difference | news-service | alert-service | Reason |
|------------|-------------|---------------|--------|
| Sync strategy | `write-sync` | `async` | Alert writes (creating/toggling alerts) do not need immediate searchability; async is faster and reduces latency for the user facing endpoints |
| Custom analyzer | Yes (NewsAnalysisConfigurer) | No | Alert keywords do not need stemming or asciifolding; default tokenizer is sufficient |
| Auto-reindex on empty | Yes | No | Only news articles need to survive a cold restart with an empty index |

### 4.2 Indexed Entities

#### SearchResult (news-service)

The `@Indexed` annotation tells Hibernate Search to maintain an Elasticsearch index
for this entity. When a `SearchResult` is persisted inside a `@Transactional`
method, the JPA event listener fires on commit and sends the document to
Elasticsearch automatically.

**@FullTextField fields:** `title` and `snippet`

`@FullTextField` means the value is run through an analyzer both at index time and
at query time. The analyzer chosen here is `news_analyzer` (see §4.3). This enables:
- Case-insensitive search ("Quarkus" matches "quarkus")
- ASCII folding ("Zürich" matches "zurich")
- Stemming ("searching" matches "search")

`highlightable = Highlightable.ANY` stores the original term positions in
Elasticsearch so that the highlighting API can return `<em>`-tagged fragments. Without
this attribute, the highlight projection in `SearchResource.searchWithHighlights()`
silently returns empty lists.

**@KeywordField fields:** `url`, `source`, `keyword`

`@KeywordField` stores values verbatim (no analysis). Used for:
- `url` — exact-match deduplication check
- `source` — aggregation by search engine name (§4.7)
- `keyword` — exact-match filter (e.g. only show results for keyword="Bitcoin")

**@GenericField(sortable=YES):** `discoveredAt`

`sortable = Sortable.YES` instructs Elasticsearch to add a doc-values column (a
columnar store) for this field, which is required for efficient `sort()` calls. Without
it, sorting raises an exception at query time.

#### Alert (alert-service)

`keyword` has both `@FullTextField` (for fuzzy keyword search) and `@KeywordField`
(for exact-match filtering in the notification pipeline). `createdAt` is
`@GenericField(sortable=YES)` so results can be ordered newest-first.
`@IndexedEmbedded` on `user` embeds the user's email into the alert document so you
can search alerts by email without a join.

### 4.3 Custom Analyzer

**Class:** `com.newsalert.news.search.NewsAnalysisConfigurer`
**File:** `news-service/src/main/java/com/newsalert/news/search/NewsAnalysisConfigurer.java`
**Interface:** `ElasticsearchAnalysisConfigurer`

```java
public class NewsAnalysisConfigurer implements ElasticsearchAnalysisConfigurer {
    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {

        context.tokenFilter("news_english_stemmer")
                .type("stemmer")
                .param("language", "english");

        context.analyzer("news_analyzer").custom()
                .tokenizer("standard")
                .tokenFilters("lowercase", "asciifolding", "news_english_stemmer");
    }
}
```

**Analyzer name:** `news_analyzer`

**Filter chain (applied in order):**

| Step | Filter | What it does | Concrete example |
|------|--------|-------------|-----------------|
| 1 | `standard` tokenizer | Splits on whitespace and punctuation | `"Hibernate-Search"` → `["Hibernate", "Search"]` |
| 2 | `lowercase` | Folds all tokens to lower case | `["Hibernate", "Search"]` → `["hibernate", "search"]` |
| 3 | `asciifolding` | Strips diacritics and converts to ASCII | `"Zürich"` → `"zurich"`, `"café"` → `"cafe"` |
| 4 | `news_english_stemmer` | Porter2 (Snowball) English stemming | `"running"` → `"run"`, `"searches"` → `"search"`, `"releases"` → `"releas"` |

**Which fields use this analyzer:** `SearchResult.title` and `SearchResult.snippet`
(both annotated `@FullTextField(analyzer = "news_analyzer")`).

**How to verify with a search example:**

Search for "running" and you will get results whose title or snippet contains
"run", "runs", "runner", or "running" because they all stem to `"run"`:

```bash
curl "http://localhost:9200/searchresult/_analyze" \
  -H "Content-Type: application/json" \
  -d '{"analyzer":"news_analyzer","text":"Releases running searches"}'
```

Expected tokens: `["releas", "run", "search"]`

### 4.4 Indexing Process

#### Automatic indexing on entity persist/update

When `CrawlerService.persistNewResults()` calls `searchResultRepository.persist(entity)`
inside a `@Transactional` method, the Hibernate ORM event listener intercepts the
commit. Because `automatic-indexing.synchronization.strategy=write-sync` is set,
Hibernate Search sends the document to Elasticsearch and waits for a confirmation
before the transaction returns. The next `GET /api/search` call will find the new
article immediately.

#### MassIndexer (manual endpoint)

**Trigger class:** `SearchResource` (method `reindex()`)
**Endpoint:** `POST /api/search/reindex`

```java
searchSession.massIndexer(SearchResult.class)
        .threadsToLoadObjects(4)
        .startAndWait();
```

Reads all `SearchResult` rows from PostgreSQL in batches, converts them to
Elasticsearch documents, and bulk-indexes them. Blocks until complete. Returns:

```json
{"status":"ok","reindexed":1234}
```

#### Startup auto-reindex

**Class:** `com.newsalert.news.search.SearchIndexInitializer`
**File:** `news-service/src/main/java/com/newsalert/news/search/SearchIndexInitializer.java`
**Trigger:** `@Observes StartupEvent`

On every news-service startup, `onStart()` counts documents in Elasticsearch
(`fetchTotalHitCount()`). If the count is 0 and `newsalert.search.auto-reindex-on-empty=true`,
it calls `performMassIndexing()` using:

```java
searchSession.massIndexer(SearchResult.class)
        .threadsToLoadObjects(reindexThreads)   // default: 2
        .batchSizeToLoadObjects(reindexBatchSize) // default: 25
        .startAndWait();
```

You will see these log lines in the news-service container:

```
INFO  Checking Elasticsearch index status on startup...
INFO  Found 0 documents in Elasticsearch index
INFO  Elasticsearch index is empty, triggering automatic reindexing...
INFO  Starting mass reindexing operation with 2 threads and batch size 25
INFO  Reindexing complete, indexed 0 entities
```

(The last line shows 0 on a fresh start with no data yet.)

#### How to observe indexing via curl

```bash
# Count documents in the ES index directly:
curl -s "http://localhost:9200/searchresult/_count" | python3 -m json.tool

# Trigger manual reindex and see returned count:
curl -s -X POST http://localhost:8080/api/search/reindex

# Check the health endpoint which compares ES count vs PG count:
curl -s http://localhost:8080/api/search/health
```

### 4.5 Search Queries

#### GET /api/search — Basic Full-Text Search

**HTTP Method + URL:** `GET http://localhost:8080/api/search`

**Query parameters:**

| Parameter | Type      | Default | Description                                              |
|-----------|-----------|---------|----------------------------------------------------------|
| `q`       | String    | (required, error if blank) | Search term matched against `title` and `snippet` |
| `keyword` | String    | (optional) | Exact-match filter on the `keyword` field             |
| `from`    | LocalDate | (optional) | Only results discovered on or after this date (ISO format: `2024-01-15`) |
| `page`    | int       | `0`     | Zero-based page number                                   |
| `size`    | int       | `20`    | Results per page (clamped to 1–100)                     |

**Hibernate Search API calls (inside `SearchResource.search()`):**

```java
searchSession.search(SearchResult.class)
    .where(f -> f.bool()
        .must(f.match().fields("title", "snippet").matching(q).fuzzy(1))
        // Optional: .filter(f.match().field("keyword").matching(keyword))
        // Optional: .filter(f.range().field("discoveredAt").atLeast(...))
    )
    .sort(f -> f.field("discoveredAt").desc())
    .fetch(page * size, size);
```

`fuzzy(1)` means Levenshtein edit distance of 1 — a query with a single character
insertion, deletion, or substitution still matches. Example: `"Quarkuz"` matches
`"Quarkus"`.

**Response JSON:**

```json
{
  "results": [
    {
      "id": 42,
      "title": "Quarkus 3.0 Released with Major Performance Improvements",
      "snippet": "The latest version of Quarkus brings significant startup time reductions...",
      "url": "https://quarkus.io/blog/quarkus-3-0-released",
      "source": "google_news",
      "keyword": "Quarkus",
      "discoveredAt": "2024-03-18T14:32:00"
    }
  ],
  "totalHits": 1,
  "page": 0,
  "size": 20
}
```

**How to test:**

```bash
curl "http://localhost:8080/api/search?q=Quarkus"
```

#### Fuzzy Search (edit distance = 1)

```bash
# "Quarkuz" has one character different from "Quarkus" — still matches:
curl "http://localhost:8080/api/search?q=Quarkuz"
```

The `fuzzy(1)` in the query predicate handles this automatically. No separate
`fuzzy` parameter is needed — it is always active.

#### Keyword Filtering (exact match)

```bash
# Only show results crawled for the keyword "Bitcoin":
curl "http://localhost:8080/api/search?q=price&keyword=Bitcoin"
```

#### Date Range Filtering

```bash
# Only show results discovered on or after 2024-03-01:
curl "http://localhost:8080/api/search?q=Quarkus&from=2024-03-01"
```

#### Sorting

Results are always sorted by `discoveredAt` descending (newest first). This is
hardcoded via `.sort(f -> f.field("discoveredAt").desc())`.

#### Pagination

```bash
# Page 0, 5 results per page:
curl "http://localhost:8080/api/search?q=news&page=0&size=5"

# Page 1, 5 results per page (next page):
curl "http://localhost:8080/api/search?q=news&page=1&size=5"
```

### 4.6 Highlighting

#### GET /api/search/highlighted

**HTTP Method + URL:** `GET http://localhost:8080/api/search/highlighted`

**Query parameters:** same as `/api/search` (`q`, `keyword`, `from`, `page`, `size`)

**How the highlighting query differs from regular search:**

Instead of `.select(SearchResult.class)` (implicit entity projection), the highlighted
endpoint uses a **composite projection**:

```java
searchSession.search(SearchResult.class)
    .select(f -> f.composite()
        .from(f.entity(), f.highlight("title"), f.highlight("snippet"))
        .as((SearchResult hit, List<String> titleFragments, List<String> snippetFragments) ->
                SearchResultWithHighlightsDTO.fromWithHighlights(hit, titleFragments, snippetFragments)))
    .where(...)
    .sort(...)
    .fetch(...);
```

`f.entity()` loads the full entity; `f.highlight("title")` asks Elasticsearch for
fragments with matched terms wrapped in `<em>` tags. All three projections are
resolved in a single round-trip to Elasticsearch.

**Fallback behavior:** The `SearchResultWithHighlightsDTO.fromWithHighlights()` factory
falls back to `List.of(entity.title)` when Elasticsearch returns no title fragments
(e.g. when the query matched in snippet only). The client always receives a non-null,
non-empty list.

**Response JSON:**

```json
{
  "hits": [
    {
      "id": 7,
      "title": "Getting Started with Elasticsearch in Java",
      "snippet": "A comprehensive guide to using Elasticsearch with Java applications...",
      "url": "https://example.com/elasticsearch-java-guide",
      "source": "Tech Tutorials",
      "keyword": "elasticsearch",
      "discoveredAt": "2024-03-15T09:00:00",
      "titleHighlights": [
        "Getting Started with <em>Elasticsearch</em> in Java"
      ],
      "snippetHighlights": [
        "A comprehensive guide to using <em>Elasticsearch</em> with Java applications..."
      ]
    }
  ],
  "total": 2,
  "page": 0,
  "size": 20
}
```

**How to test:**

```bash
curl "http://localhost:8080/api/search/highlighted?q=Elasticsearch"
```

### 4.7 Aggregations

#### GET /api/search/stats

**HTTP Method + URL:** `GET http://localhost:8080/api/search/stats`

**Query parameters:**

| Parameter | Type   | Default | Description                                            |
|-----------|--------|---------|--------------------------------------------------------|
| `keyword` | String | `""`    | Optional filter; if blank, aggregates all documents    |

**What is being aggregated:** document counts grouped by the `source` field (which
search engine returned the article, e.g. `google_news`, `bing`, `duckduckgo`).

**Hibernate Search API used:**

```java
AggregationKey<Map<String, Long>> countsBySourceKey = AggregationKey.of("counts_by_source");

searchSession.search(SearchResult.class)
    .where(f -> /* matchAll or keyword filter */)
    .aggregation(countsBySourceKey, f -> f.terms().field("source", String.class))
    .fetch(0, 0);  // no hits needed, just aggregation results

Map<String, Long> countsBySource = result.aggregation(countsBySourceKey);
```

`f.terms().field("source", ...)` is a **terms aggregation** — it groups documents by
distinct values of the `source` field and counts them. The `source` field must be a
`@KeywordField` (not `@FullTextField`) for terms aggregations to work, because terms
aggregations operate on exact, unanalyzed values.

**Response JSON:**

```json
{
  "google_news": 45,
  "bing_news": 32,
  "duckduckgo": 18,
  "brave": 12,
  "bing": 9
}
```

**How to test:**

```bash
# Aggregate all documents:
curl "http://localhost:8080/api/search/stats"

# Aggregate only documents matching "Quarkus":
curl "http://localhost:8080/api/search/stats?keyword=Quarkus"
```

### 4.8 Health and Monitoring

#### GET /api/search/health

**HTTP Method + URL:** `GET http://localhost:8080/api/search/health`

**What it returns:** The count of documents currently in the Elasticsearch index
compared to the count of rows in PostgreSQL.

**Implementation:**

```java
long indexedCount = searchSession.search(SearchResult.class)
    .where(f -> f.matchAll())
    .fetchTotalHitCount();

long dbCount = searchResultRepository.count();
```

**Response JSON:**

```json
{"status":"ok","indexedDocuments":1234,"databaseRows":1234}
```

**How to interpret:** `indexedDocuments` should equal `databaseRows` when the index
is in sync. A discrepancy means either the index is stale (run `POST /api/search/reindex`)
or a document failed to index (check logs).

**How to test:**

```bash
curl -s http://localhost:8080/api/search/health
```

---

## 5. Kafka Messaging

### Topic

| Topic name   | Created by      | How | Retention |
|--------------|-----------------|-----|-----------|
| `new-results` | Kafka auto-create | First publish by news-service | 48 hours (`KAFKA_LOG_RETENTION_HOURS=48`) |

### Producer — news-service

**Class:** `com.newsalert.news.messaging.NewResultsProducer`
**File:** `news-service/src/main/java/com/newsalert/news/messaging/NewResultsProducer.java`

**What triggers production:** After each `crawlKeyword()` call inside `CrawlerService`
(both scheduled and manual), `newResultsProducer.sendNewResults(keyword, newCount)` is
called. The producer only publishes if `count > 0` — no event is sent when a crawl
cycle finds no new articles.

**How it publishes:**

```java
@Channel("new-results")
Emitter<String> emitter;

emitter.send(objectMapper.writeValueAsString(new NewResultsEvent(keyword, count)));
```

**Message format** — `NewResultsEvent`:

```json
{"keyword": "Quarkus", "count": 3, "timestamp": "2024-03-18T14:32:00.000Z"}
```

`timestamp` is an ISO-8601 instant set at construction time in `NewResultsEvent`.

**Configuration in `application.properties`:**

```properties
mp.messaging.outgoing.new-results.connector=smallrye-kafka
mp.messaging.outgoing.new-results.topic=new-results
mp.messaging.outgoing.new-results.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

### Consumer — alert-service

**Class:** `com.newsalert.alert.messaging.NewResultsConsumer`
**File:** `alert-service/src/main/java/com/newsalert/alert/messaging/NewResultsConsumer.java`

```java
@Incoming("new-results")
public void onNewResults(String message) { ... }
```

**What it does with the message:**

1. Deserializes the JSON string into a `NewResultEvent` (fields: `keyword`, `count`, `timestamp`)
2. Calls `notificationService.processKeywordEvent(event.keyword)`
3. `NotificationService.processKeywordEvent()` then:
   - Finds all active `Alert` records matching the keyword
   - For each alert: queries news-service `/api/search` for results since the last notification
   - Deduplicates against `notification_logs` (using `NotificationLog.alreadyNotified()`)
   - Sends an HTML email via Quarkus Mailer + Qute template (`alert-email.html`)
   - Persists new `NotificationLog` records
   - Calls news-service `POST /api/internal/search-results/mark-notified` to flip `alreadyNotified=true`
   - Calls `WebSocketBroadcaster.broadcast()` to push to connected JavaFX clients

**Configuration in `application.properties`:**

```properties
mp.messaging.incoming.new-results.connector=smallrye-kafka
mp.messaging.incoming.new-results.topic=new-results
mp.messaging.incoming.new-results.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.new-results.group.id=alert-service-notifier
mp.messaging.incoming.new-results.auto.offset.reset=earliest
```

`group.id=alert-service-notifier` ensures that if alert-service restarts, it resumes
from where it left off rather than re-processing old messages.

### How to Observe Kafka

```bash
# List all topics:
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Read all messages from new-results from the beginning:
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic new-results \
  --from-beginning

# (Press Ctrl+C to stop)
```

Example output when a crawl finds new articles:

```
{"keyword":"Quarkus","count":2,"timestamp":"2024-03-18T14:32:01.234Z"}
{"keyword":"Bitcoin","count":5,"timestamp":"2024-03-18T14:32:03.891Z"}
```

---

## 6. Scheduled Tasks

### CrawlerService — 15-minute Crawl Cycle

**Class:** `com.newsalert.news.scheduler.CrawlerService`
**File:** `news-service/src/main/java/com/newsalert/news/scheduler/CrawlerService.java`

```java
@Scheduled(every = "15m", identity = "crawler-job")
void crawl() { ... }
```

**Cron expression from `application.properties`:**

```properties
newsalert.scheduler.cron=0 0/15 * * * ?
```

Note: the `@Scheduled(every = "15m")` annotation in the class overrides the properties
file cron for the runtime trigger; both express "every 15 minutes".

**What it does on each cycle:**

1. Calls `alertServiceClient.getActiveKeywords()` → `GET http://alert-service:8081/api/internal/keywords`
2. If no keywords are found, logs and exits
3. For each keyword, calls `crawlKeyword(keyword)` which:
   - `persistNewResults(keyword)` (in a `@Transactional` boundary):
     - Queries `SearXNG` for the keyword via `SearxngService.search(keyword)`
     - For each result: checks `searchResultRepository.existsByUrl(url)` (deduplication)
     - Calls `searchResultRepository.persist(entity)` → auto-indexed in Elasticsearch
   - After the transaction commits: calls `newResultsProducer.sendNewResults(keyword, newCount)`
     — publishes to Kafka only if `newCount > 0`

**How to observe:**

```bash
# Watch news-service logs in real time:
docker compose logs -f news-service

# You will see lines like:
# INFO  Crawler cycle started
# INFO  Crawling 3 keyword(s): [Quarkus, Bitcoin, Kubernetes]
# INFO  SearXNG returned 10 result(s) for keyword='Quarkus'
# INFO  Saved 2 new result(s) for keyword='Quarkus'
# INFO  Crawler cycle finished
```

**Side effects you can verify:**

1. PostgreSQL row count increases after a cycle:
   ```bash
   docker compose exec postgres psql -U newsalert -d newsalert \
     -c "SELECT keyword, count(*) FROM search_results GROUP BY keyword ORDER BY keyword;"
   ```

2. Elasticsearch document count increases:
   ```bash
   curl -s http://localhost:9200/searchresult/_count
   ```

3. Kafka messages appear in the `new-results` topic (see §5).

---

## 7. REST API Complete Reference

### news-service — Port 8080

Swagger UI: `http://localhost:8080/swagger-ui`

#### GET /api/search

Full-text search with optional filters.

| Parameter | Type      | Default | Required |
|-----------|-----------|---------|----------|
| `q`       | String    | —       | Yes      |
| `keyword` | String    | —       | No       |
| `from`    | LocalDate | —       | No       |
| `page`    | int       | `0`     | No       |
| `size`    | int       | `20`    | No       |

```bash
curl "http://localhost:8080/api/search?q=Quarkus"
curl "http://localhost:8080/api/search?q=news&keyword=Bitcoin&from=2024-01-01&page=0&size=5"
```

#### GET /api/search/highlighted

Same parameters as `/api/search`. Returns `<em>`-tagged highlight fragments.

```bash
curl "http://localhost:8080/api/search/highlighted?q=Elasticsearch"
```

#### GET /api/search/health

No parameters. Returns ES vs PG document counts.

```bash
curl "http://localhost:8080/api/search/health"
```

#### POST /api/search/reindex

No body. Triggers a full MassIndexer rebuild. Blocks until complete.

```bash
curl -X POST "http://localhost:8080/api/search/reindex"
```

#### GET /api/search/stats

Aggregation by source field.

| Parameter | Type   | Default | Required |
|-----------|--------|---------|----------|
| `keyword` | String | `""`    | No       |

```bash
curl "http://localhost:8080/api/search/stats"
curl "http://localhost:8080/api/search/stats?keyword=Quarkus"
```

#### POST /api/crawler/run

Triggers an immediate crawl cycle for all active keywords. Returns a summary.

```bash
curl -X POST "http://localhost:8080/api/crawler/run"
```

Response example:

```json
{
  "message": "Crawler run complete",
  "keywords": ["Quarkus", "Bitcoin"],
  "processed": 2,
  "errors": 0
}
```

#### POST /api/internal/search-results/mark-notified

Body: JSON array of URL strings. Marks matching rows `alreadyNotified = true`.
Intended for alert-service internal use only (Docker network).

```bash
curl -X POST "http://localhost:8080/api/internal/search-results/mark-notified" \
  -H "Content-Type: application/json" \
  -d '["https://example.com/article-1", "https://example.com/article-2"]'
```

---

### alert-service — Port 8081

Swagger UI: `http://localhost:8081/swagger-ui`

#### POST /api/auth/register

Register a new account. Returns a JWT token.

Body: `{"email": "user@example.com", "password": "secret123"}`

Constraints: email must be valid, password minimum 8 characters.

```bash
curl -X POST "http://localhost:8081/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'
```

Response:

```json
{"token": "eyJhbGciOiJSUzI1NiJ9...", "type": "Bearer"}
```

#### POST /api/auth/login

Authenticate and receive a JWT token.

```bash
curl -X POST "http://localhost:8081/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'
```

Response: same shape as register.

#### GET /api/alerts

List active alerts belonging to the authenticated user. Requires `Authorization: Bearer <token>`.

```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."
curl "http://localhost:8081/api/alerts" \
  -H "Authorization: Bearer $TOKEN"
```

Response:

```json
[
  {"id": 1, "keyword": "Quarkus", "active": true, "createdAt": "2024-03-18T10:00:00"},
  {"id": 2, "keyword": "Bitcoin", "active": true, "createdAt": "2024-03-18T10:01:00"}
]
```

#### POST /api/alerts

Create a new alert. Requires JWT.

Body: `{"keyword": "Kubernetes"}`

Constraint: keyword 2–255 characters.

```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."
curl -X POST "http://localhost:8081/api/alerts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"keyword":"Kubernetes"}'
```

Response (HTTP 201):

```json
{"id": 3, "keyword": "Kubernetes", "active": true, "createdAt": "2024-03-18T10:05:00"}
```

#### DELETE /api/alerts/{id}

Delete an alert belonging to the current user. Requires JWT.

```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."
curl -X DELETE "http://localhost:8081/api/alerts/3" \
  -H "Authorization: Bearer $TOKEN"
```

Response: HTTP 204 No Content.

#### PUT /api/alerts/{id}/toggle

Toggle an alert's `active` flag. Requires JWT.

```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."
curl -X PUT "http://localhost:8081/api/alerts/1/toggle" \
  -H "Authorization: Bearer $TOKEN"
```

Response: updated `AlertDTO` with new `active` value.

#### GET /api/alerts/search

Full-text search across alert keywords using Hibernate Search. Does **not** require
a JWT.

| Parameter | Type    | Default | Description                               |
|-----------|---------|---------|-------------------------------------------|
| `q`       | String  | `""`    | Search query (blank = list all active)    |
| `fuzzy`   | boolean | `true`  | Enable fuzzy matching (edit distance 1)   |
| `page`    | int     | `0`     | Page number (0-based)                     |
| `size`    | int     | `20`    | Page size                                 |

```bash
# Search for alerts matching "quark" (fuzzy matches "quarkus"):
curl "http://localhost:8081/api/alerts/search?q=quark"

# Exact match only:
curl "http://localhost:8081/api/alerts/search?q=Quarkus&fuzzy=false"
```

Response:

```json
{
  "results": [
    {"id": 1, "keyword": "Quarkus", "active": true, "createdAt": "2024-03-18T10:00:00", "user": {...}}
  ],
  "totalHits": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

#### GET /api/internal/keywords

Returns a sorted, deduplicated list of all active keyword strings. No authentication.
Used by news-service crawler. Docker-network only.

```bash
curl "http://localhost:8081/api/internal/keywords"
```

Response:

```json
["Bitcoin", "Elasticsearch", "Kubernetes", "Quarkus"]
```

---

## 8. WebSocket

**Endpoint:** `/ws/notifications` on **alert-service port 8081**

Full URL: `ws://localhost:8081/ws/notifications`

**Server class:** `com.newsalert.alert.ws.NotificationWebSocket`
**File:** `alert-service/src/main/java/com/newsalert/alert/ws/NotificationWebSocket.java`

**Protocol:** One-way push from server to clients. The server ignores any
client-to-server messages (they are logged at DEBUG level and discarded).

**Session registry:** Managed by `WebSocketBroadcaster` (application-scoped CDI
bean). It holds a `CopyOnWriteArraySet<Session>`. When a session closes or a send
fails, it is automatically removed.

**Messages sent from server to client:**

On connection open, the server immediately sends:

```json
{"type": "connected", "message": "NewsAlert WS ready"}
```

When new articles are found (triggered by `NotificationService.processKeywordEvent()`
after a Kafka event):

```json
{
  "keyword": "Quarkus",
  "count": 3,
  "titles": [
    "Quarkus 3.0 Released with Major Performance Improvements",
    "Quarkus Native Image Tutorial",
    "Quarkus vs Spring Boot: Performance Comparison"
  ]
}
```

`titles` contains up to 10 distinct article titles (`MAX_BROADCAST_TITLES = 10`).

**How to connect and observe:**

Using `wscat` (install with `npm install -g wscat`):

```bash
wscat -c ws://localhost:8081/ws/notifications
```

You will immediately see:

```json
{"type":"connected","message":"NewsAlert WS ready"}
```

Then trigger a crawl to see a push notification:

```bash
curl -X POST http://localhost:8080/api/crawler/run
```

---

## 9. JavaFX Client

**Module:** `client/`
**Main class:** `com.newsalert.client.MainApp`
**File:** `client/src/main/java/com/newsalert/client/MainApp.java`

### What the client does

- Runs a **first-time setup wizard** (4-step FXML flow: Welcome → Register →
  First Keyword → Done)
- Stores configuration (service URLs, JWT token, crawl interval) in
  `~/.newsalert/config.json` via `ConfigManager`
- Installs a **system tray icon** (16×16 blue circle, generated programmatically)
  with a context menu: My Keywords, Recent Results, Settings, Exit
- Connects to `ws://localhost:8081/ws/notifications` via `NotificationListener`
  (OkHttp WebSocket client with 20-second ping interval and exponential backoff
  reconnect: 5 s → 60 s max)
- Shows **popup notifications** (`NotificationPopupController`) positioned
  bottom-right, auto-closing after 8 seconds
- Periodically fires `POST /api/crawler/run` via `CrawlScheduler`
  (`ScheduledExecutorService`) at a user-configurable interval (default: editable
  in Settings, 1–60 minutes)
- Allows browsing recent results (`RecentResultsController`) with click-to-open
  links (`Desktop.browse()`)
- Allows managing keywords (`KeywordManagerController`) — add, delete, toggle

### How to Run

```bash
cd client
mvn javafx:run
```

The `javafx-maven-plugin` is configured with `mainClass=com.newsalert.client.MainApp`
and adds the required JVM opens:

```
--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED
-Djava.awt.headless=false
```

Alternatively, build a fat JAR and run it:

```bash
cd client
mvn package
java -jar target/client-1.0.0-SNAPSHOT.jar
```

### Features in Detail

| Feature                  | Class                          | How to trigger                      |
|--------------------------|--------------------------------|--------------------------------------|
| Setup wizard             | `SetupWizardController`        | Auto-shown on first run (no config)  |
| System tray              | `TrayManager`                  | Auto-installed at startup            |
| Keyword management       | `KeywordManagerController`     | Tray → "My Keywords"                 |
| Recent results           | `RecentResultsController`      | Tray → "Recent Results"              |
| Settings                 | `SettingsController`           | Tray → "Settings"                    |
| Popup notification       | `NotificationPopupController`  | Automatic on WS push message         |
| WebSocket listener       | `NotificationListener`         | Auto-started after setup             |
| Crawler trigger          | `CrawlScheduler`               | Fires at configured interval         |

### How it Connects to the Backend

- Alert-service URL default: read from `config.json` (set during wizard as
  `alertServiceUrl`, e.g. `http://localhost:8081`)
- News-service URL default: read from `config.json` (`newsServiceUrl`, e.g.
  `http://localhost:8080`)
- All HTTP calls use OkHttp via `AlertServiceApi`
- JWT token is stored in `config.json` and sent as `Authorization: Bearer <token>`

---

## 10. Testing

### Test Files

Only one test class exists in the project:

**File:** `news-service/src/test/java/com/newsalert/news/resource/SearchResourceTest.java`
**Class:** `com.newsalert.news.resource.SearchResourceTest`
**Framework:** JUnit 5 + RestAssured + `@QuarkusTest`
**Lifecycle:** `@TestInstance(Lifecycle.PER_CLASS)` — one instance for all tests;
`@BeforeAll` runs once to seed data.

### Test Profile Configuration

From `news-service/src/main/resources/application.properties`:

```properties
%test.quarkus.datasource.db-kind=postgresql
%test.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/newsalert_test
%test.quarkus.hibernate-orm.database.generation=drop-and-create
%test.quarkus.hibernate-search-orm.enabled=true
%test.quarkus.hibernate-search-orm.elasticsearch.hosts=localhost:9200
%test.quarkus.hibernate-search-orm.schema-management.strategy=drop-and-create
%test.quarkus.mailer.mock=true
```

The tests use a **separate database** (`newsalert_test`) and connect to the same
Elasticsearch at `localhost:9200`. The ES schema is dropped and recreated on each
test run. You need both PostgreSQL and Elasticsearch running (via `docker compose up`)
before running the tests.

### Test Data Seeded in @BeforeAll

The `seedTestData()` method creates 5 `SearchResult` rows, then calls
`searchSession.massIndexer(SearchResult.class).startAndWait()` to index them:

| Title (truncated)                                    | Source            | Days ago |
|------------------------------------------------------|-------------------|----------|
| Quarkus 3.0 Released with Major Performance...       | Quarkus Blog      | 1        |
| Hibernate Search 6.2: What's New                     | Hibernate Blog    | 3        |
| Getting Started with Elasticsearch in Java           | Tech Tutorials    | 7        |
| Microservices Best Practices for 2024                | DevOps Weekly     | 5        |
| Kubernetes vs Docker Swarm: A Comparison             | Cloud Native Daily | 10       |

### Test Methods

| Method                          | What it verifies |
|---------------------------------|-----------------|
| `testBasicSearch()`             | `GET /api/search?q=Quarkus` returns HTTP 200, `results.size() > 0`, first result title contains "Quarkus", `totalHits >= 1` |
| `testFuzzySearch()`             | `GET /api/search?q=Quarkuz` (intentional typo) returns results with title containing "Quarkus", proving `fuzzy(1)` is active |
| `testDateFilter()`              | `GET /api/search?q=Quarkus&from=<2 days ago>` returns only recent results; the Quarkus doc (1 day ago) appears |
| `testPagination()`              | `GET /api/search?q=Quarkus&page=0&size=2` returns at most 2 results, `page=0`, `size=2`, `totalHits >= 1` |
| `testHighlighting()`            | `GET /api/search/highlighted?q=Elasticsearch` returns `hits.size() > 0`, `title` not null, `titleHighlights` not null, `snippetHighlights` not null |
| `testAggregation()`             | `GET /api/search/stats` returns `size() > 0`, `'Quarkus Blog' >= 1`, `'Hibernate Blog' >= 1` |
| `testAggregationWithKeywordFilter()` | `GET /api/search/stats?keyword=Quarkus` returns HTTP 200, `size() >= 0` |

**Total: 7 test methods.**

### How to Run All Tests

```bash
# From the news-service module directory:
cd news-service
mvn test
```

Prerequisites: PostgreSQL and Elasticsearch must be running on `localhost`.

```bash
# Start only the required infrastructure:
docker compose up -d postgres elasticsearch
# Wait ~30 seconds for health checks, then:
cd news-service
mvn test
```

### How to Run a Specific Test

```bash
cd news-service
mvn test -Dtest=SearchResourceTest#testHighlighting
```

### Expected Output When All Tests Pass

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.newsalert.news.resource.SearchResourceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

---

## 11. Full Demo Walkthrough

A numbered, step-by-step sequence from zero to seeing everything working.

### Step 1 — Prerequisites

| Requirement   | Minimum version | Check command          |
|---------------|-----------------|------------------------|
| Java          | 21              | `java -version`        |
| Maven         | 3.9             | `mvn -version`         |
| Docker        | 24              | `docker --version`     |
| Docker Compose| 2.x             | `docker compose version` |

### Step 2 — Clone the Repository

```bash
git clone <repository-url>
cd newsalert
```

### Step 3 — Start Infrastructure

```bash
docker compose up --build -d
```

This builds both Quarkus services (JVM mode) and starts all 7 containers. The first
build downloads dependencies and may take several minutes.

### Step 4 — Wait for Health Checks

```bash
# Poll until all services are healthy:
watch docker compose ps
```

All services should show `healthy` within about 2–3 minutes. You can also check:

```bash
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

### Step 5 — Check Elasticsearch is Empty

If this is a completely fresh deployment (no existing data), the index will be empty:

```bash
curl -s http://localhost:9200/searchresult/_count
```

Expected: `{"count":0,...}` (or a 404 if the index hasn't been created yet — it gets
created by Hibernate Search on first startup).

### Step 6 — See Auto-Reindex Happen

On news-service startup, `SearchIndexInitializer.onStart()` fires. Watch the logs:

```bash
docker compose logs news-service | grep -i "reindex\|index"
```

Expected output (fresh start with no existing data):

```
INFO  Checking Elasticsearch index status on startup...
INFO  Found 0 documents in Elasticsearch index
INFO  Elasticsearch index is empty, triggering automatic reindexing...
INFO  Reindexing complete, indexed 0 entities
```

### Step 7 — Register a User and Get a JWT

```bash
curl -X POST "http://localhost:8081/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123"}'
```

Save the token from the response:

```bash
TOKEN="<paste token here>"
```

### Step 8 — Create a Keyword Alert

```bash
curl -X POST "http://localhost:8081/api/alerts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"keyword":"Quarkus"}'
```

### Step 9 — Trigger a Manual Crawl

```bash
curl -X POST "http://localhost:8080/api/crawler/run"
```

Expected response:

```json
{"message":"Crawler run complete","keywords":["Quarkus"],"processed":1,"errors":0}
```

Watch the news-service logs to see articles being fetched and indexed:

```bash
docker compose logs -f news-service
```

### Step 10 — Perform a Basic Search

Wait a few seconds after the crawl for indexing to complete, then:

```bash
curl "http://localhost:8080/api/search?q=Quarkus"
```

Expected: JSON with `results` array and `totalHits > 0`.

### Step 11 — Perform a Fuzzy Search with a Typo

```bash
curl "http://localhost:8080/api/search?q=Quarkuz"
```

"Quarkuz" has edit distance 1 from "Quarkus", so `fuzzy(1)` still matches it.
Expected: same results as the exact search.

### Step 12 — Perform a Date-Filtered Search

```bash
curl "http://localhost:8080/api/search?q=Quarkus&from=2024-01-01"
```

Only returns results discovered on or after 2024-01-01.

### Step 13 — Perform a Paginated Search

```bash
# First page, 3 results:
curl "http://localhost:8080/api/search?q=Quarkus&page=0&size=3"

# Second page, 3 results:
curl "http://localhost:8080/api/search?q=Quarkus&page=1&size=3"
```

### Step 14 — Get Highlighted Results

```bash
curl "http://localhost:8080/api/search/highlighted?q=Quarkus"
```

Look at the `titleHighlights` and `snippetHighlights` fields — matched terms appear
wrapped in `<em>` tags:

```json
"titleHighlights": ["<em>Quarkus</em> 3.0 Released with Major Performance Improvements"]
```

### Step 15 — Get Aggregation Stats

```bash
curl "http://localhost:8080/api/search/stats"
```

Expected: a JSON object mapping search engine names to document counts:

```json
{"google_news": 8, "bing_news": 5, "duckduckgo": 3}
```

### Step 16 — Check the Health Endpoint

```bash
curl -s http://localhost:8080/api/search/health
```

Expected:

```json
{"status":"ok","indexedDocuments":16,"databaseRows":16}
```

Both numbers should be equal when the index is in sync.

### Step 17 — Trigger Manual Reindex

```bash
curl -s -X POST http://localhost:8080/api/search/reindex
```

Expected:

```json
{"status":"ok","reindexed":16}
```

### Step 18 — Search the Second Entity (Alerts)

```bash
# Search for alerts by keyword using Hibernate Search on alert-service:
curl "http://localhost:8081/api/alerts/search?q=Quarkus"
```

Expected: alerts with keyword matching "Quarkus" or user emails fuzzy-matching "Quarkus".

### Step 19 — Open Swagger UI

Open in your browser:

- **news-service:** `http://localhost:8080/swagger-ui`
- **alert-service:** `http://localhost:8081/swagger-ui`

All endpoints are browsable and testable directly from the UI.

### Step 20 — Start the JavaFX Client

```bash
cd client
mvn javafx:run
```

The setup wizard appears. Enter your registered email/password, add a keyword, and
click through to "Done". The system tray icon appears and the WebSocket connection
is established. Trigger a crawl to see a popup notification.

### Step 21 — Run the Tests

```bash
# Infrastructure must be running first:
docker compose up -d postgres elasticsearch

# Wait ~30 seconds, then:
cd news-service
mvn test
```

Expected final output:

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Step 22 — Stop Everything

```bash
cd ..  # back to project root
docker compose down
```

To also wipe all data volumes:

```bash
docker compose down -v
```

---

## Summary

| Category                  | Count |
|---------------------------|-------|
| **Endpoints documented**  | **15** |
| news-service endpoints    | 7 (GET /api/search, GET /api/search/highlighted, GET /api/search/health, POST /api/search/reindex, GET /api/search/stats, POST /api/crawler/run, POST /api/internal/search-results/mark-notified) |
| alert-service endpoints   | 8 (POST /api/auth/register, POST /api/auth/login, GET /api/alerts, POST /api/alerts, DELETE /api/alerts/{id}, PUT /api/alerts/{id}/toggle, GET /api/alerts/search, GET /api/internal/keywords) |
| **curl examples**         | **38** |
| **Test methods described** | **7** |
| `testBasicSearch`, `testFuzzySearch`, `testDateFilter`, `testPagination`, `testHighlighting`, `testAggregation`, `testAggregationWithKeywordFilter` | |
