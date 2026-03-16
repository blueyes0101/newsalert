package com.newsalert.demo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NewsAlert – Interactive Demo CLI
 * =================================
 * University presentation tool showing the system internals live.
 * Run with: mvn compile exec:java  (from the demo/ directory)
 *
 * Requires Java 11+ (uses java.net.http.HttpClient)
 */
public class NewsAlertDemo {

    // ──────────────────────────────────────────────────────────────────────────
    //  Configuration constants – adjust to match your environment
    // ──────────────────────────────────────────────────────────────────────────

    static final String DEMO_EMAIL        = "demo@newsalert.com";
    static final String DEMO_PASSWORD     = "Demo1234!";
    static final String ALERT_SERVICE_URL = "http://localhost:8081";
    static final String NEWS_SERVICE_URL  = "http://localhost:8080";
    static final String ES_URL            = "http://localhost:9200";
    static final String DB_URL            = "jdbc:postgresql://localhost:5432/newsalert";
    static final String DB_USER           = "newsalert";
    static final String DB_PASSWORD       = "newsalert";

    // ──────────────────────────────────────────────────────────────────────────
    //  ANSI colours
    // ──────────────────────────────────────────────────────────────────────────

    static final String RESET  = "\u001B[0m";
    static final String BLACK  = "\u001B[30m";
    static final String RED    = "\u001B[31m";
    static final String GREEN  = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE   = "\u001B[34m";
    static final String CYAN   = "\u001B[36m";
    static final String WHITE  = "\u001B[37m";
    static final String BOLD   = "\u001B[1m";
    static final String DIM    = "\u001B[2m";

    // ──────────────────────────────────────────────────────────────────────────
    //  Shared HTTP client (reused across all requests)
    // ──────────────────────────────────────────────────────────────────────────

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ──────────────────────────────────────────────────────────────────────────
    //  State shared across demos
    // ──────────────────────────────────────────────────────────────────────────

    static String jwtToken   = null;   // set by Auth Demo
    static String lastAlertId = null;  // set by Keyword Demo

    // ──────────────────────────────────────────────────────────────────────────
    //  Entry point
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        clearScreen();
        printBanner();

        while (true) {
            printMenu();
            System.out.print(CYAN + "  Enter choice: " + RESET);
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1" -> runAuthDemo(sc);
                case "2" -> runKeywordDemo(sc);
                case "3" -> runCrawlerDemo(sc);
                case "4" -> runDatabaseDemo(sc);
                case "5" -> runElasticsearchDemo(sc);
                case "6" -> runFullFlowDemo(sc);
                case "0" -> {
                    println(GREEN + "\n  Goodbye! Demo session ended." + RESET);
                    return;
                }
                default  -> println(RED + "  Unknown option. Please enter 0-6." + RESET);
            }

            println(DIM + "\n  Press ENTER to return to the menu..." + RESET);
            sc.nextLine();
            clearScreen();
            printBanner();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  [1]  AUTH DEMO
    // ══════════════════════════════════════════════════════════════════════════

    static void runAuthDemo(Scanner sc) throws Exception {
        sectionHeader("AUTH DEMO", "Register & Login via JWT");

        // ── REGISTER ─────────────────────────────────────────────────────────
        stepHeader("Step 1 / 3", "Register new account");

        String registerPayload = """
                {
                  "email": "%s",
                  "password": "%s"
                }""".formatted(DEMO_EMAIL, DEMO_PASSWORD);

        printRequest("POST", ALERT_SERVICE_URL + "/api/auth/register", registerPayload);

        HttpResponse<String> registerResp = post(ALERT_SERVICE_URL + "/api/auth/register", registerPayload);

        if (registerResp == null) return;

        int status = registerResp.statusCode();
        String body = registerResp.body();

        if (status == 201) {
            println(GREEN + "  ✓ 201 Created – account registered successfully" + RESET);
        } else if (status == 409) {
            println(YELLOW + "  ⚠ 409 Conflict – email already exists, continuing with login..." + RESET);
        } else {
            println(RED + "  ✗ HTTP " + status + " – " + body + RESET);
        }

        // ── DECODE TOKEN FROM REGISTER (if we got one) ────────────────────────
        String token = extractToken(body);
        if (token != null) {
            jwtToken = token;
            showToken(token, "Register response");
        }

        pause(700);

        // ── LOGIN ─────────────────────────────────────────────────────────────
        stepHeader("Step 2 / 3", "Login with same credentials");

        String loginPayload = """
                {
                  "email": "%s",
                  "password": "%s"
                }""".formatted(DEMO_EMAIL, DEMO_PASSWORD);

        printRequest("POST", ALERT_SERVICE_URL + "/api/auth/login", loginPayload);

        HttpResponse<String> loginResp = post(ALERT_SERVICE_URL + "/api/auth/login", loginPayload);
        if (loginResp == null) return;

        if (loginResp.statusCode() == 200) {
            println(GREEN + "  ✓ 200 OK – login successful" + RESET);
            String loginToken = extractToken(loginResp.body());
            if (loginToken != null) {
                jwtToken = loginToken;
                showToken(loginToken, "Login response");
            }
        } else {
            println(RED + "  ✗ HTTP " + loginResp.statusCode() + " – " + loginResp.body() + RESET);
        }

        pause(700);

        // ── RESULT ────────────────────────────────────────────────────────────
        stepHeader("Step 3 / 3", "Token stored for remaining demos");
        if (jwtToken != null) {
            println(GREEN + "  ✓ Authentication working. Token valid for 24 hours." + RESET);
            println(YELLOW + "  Token will be used as Bearer header in all subsequent API calls." + RESET);
        } else {
            println(RED + "  ✗ Could not obtain a JWT token. Subsequent demos may fail." + RESET);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  [2]  KEYWORD DEMO
    // ══════════════════════════════════════════════════════════════════════════

    static void runKeywordDemo(Scanner sc) throws Exception {
        sectionHeader("KEYWORD DEMO", "Add / List / Delete keywords (alerts)");

        ensureToken(sc);
        if (jwtToken == null) return;

        // ── LIST EXISTING ─────────────────────────────────────────────────────
        stepHeader("Step 1 / 4", "List current keywords");
        printRequest("GET", ALERT_SERVICE_URL + "/api/alerts", null);

        HttpResponse<String> listResp = get(ALERT_SERVICE_URL + "/api/alerts");
        if (listResp == null) return;

        println(GREEN + "  ✓ 200 OK" + RESET);
        println(CYAN + "  Response:" + RESET);
        printJson(listResp.body());
        println(YELLOW + "  These keywords are stored in PostgreSQL table 'alerts'." + RESET);

        pause(500);

        // ── ADD NEW ───────────────────────────────────────────────────────────
        stepHeader("Step 2 / 4", "Add a new keyword interactively");
        System.out.print(CYAN + "  Enter a keyword to monitor (e.g. \"Quantum Computing\"): " + RESET);
        String keyword = sc.nextLine().trim();
        if (keyword.isEmpty()) keyword = "Quantum Computing";

        String createPayload = """
                {
                  "keyword": "%s"
                }""".formatted(keyword);

        printRequest("POST", ALERT_SERVICE_URL + "/api/alerts", createPayload);

        HttpResponse<String> createResp = post(ALERT_SERVICE_URL + "/api/alerts", createPayload);
        if (createResp == null) return;

        if (createResp.statusCode() == 201) {
            println(GREEN + "  ✓ 201 Created" + RESET);
            printJson(createResp.body());
            lastAlertId = extractField(createResp.body(), "id");
            if (lastAlertId != null) {
                println(YELLOW + "  PostgreSQL INSERT: INSERT INTO alerts (keyword, user_id, active) VALUES ('" +
                        keyword + "', <userId>, true)  →  id=" + lastAlertId + RESET);
            }
        } else {
            println(RED + "  ✗ HTTP " + createResp.statusCode() + " – " + createResp.body() + RESET);
        }

        pause(500);

        // ── LIST AGAIN ────────────────────────────────────────────────────────
        stepHeader("Step 3 / 4", "List keywords again to confirm addition");
        printRequest("GET", ALERT_SERVICE_URL + "/api/alerts", null);

        HttpResponse<String> list2Resp = get(ALERT_SERVICE_URL + "/api/alerts");
        if (list2Resp != null) {
            println(GREEN + "  ✓ 200 OK" + RESET);
            printJson(list2Resp.body());
        }

        pause(500);

        // ── DELETE ────────────────────────────────────────────────────────────
        stepHeader("Step 4 / 4", "Delete the keyword we just added");
        if (lastAlertId == null) {
            println(YELLOW + "  Skipping – could not determine created alert id." + RESET);
            return;
        }

        System.out.print(CYAN + "  Delete keyword id=" + lastAlertId + " ? (y/n): " + RESET);
        String ans = sc.nextLine().trim();
        if (!ans.equalsIgnoreCase("y")) {
            println(YELLOW + "  Skipped deletion." + RESET);
            return;
        }

        String deleteUrl = ALERT_SERVICE_URL + "/api/alerts/" + lastAlertId;
        printRequest("DELETE", deleteUrl, null);

        HttpResponse<String> deleteResp = delete(deleteUrl);
        if (deleteResp == null) return;

        if (deleteResp.statusCode() == 204) {
            println(GREEN + "  ✓ 204 No Content – keyword deleted." + RESET);
            println(YELLOW + "  PostgreSQL DELETE: DELETE FROM alerts WHERE id=" + lastAlertId + RESET);
        } else {
            println(RED + "  ✗ HTTP " + deleteResp.statusCode() + " – " + deleteResp.body() + RESET);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  [3]  CRAWLER DEMO
    // ══════════════════════════════════════════════════════════════════════════

    static void runCrawlerDemo(Scanner sc) throws Exception {
        sectionHeader("CRAWLER DEMO", "Trigger & Watch a crawler cycle");

        println(YELLOW + "  Triggering manual crawler run..." + RESET);
        pause(300);
        println(CYAN + "  Step 1: Fetching active keywords from alert-service..." + RESET);
        pause(400);
        println(CYAN + "  Step 2: Querying SearXNG meta-search engine..." + RESET);
        pause(400);

        printRequest("POST", NEWS_SERVICE_URL + "/api/crawler/run", "{}");

        println(YELLOW + "  (This may take a few seconds – SearXNG is queried for each keyword)" + RESET);

        HttpResponse<String> resp = post(NEWS_SERVICE_URL + "/api/crawler/run", "{}");
        if (resp == null) return;

        int status = resp.statusCode();
        String body = resp.body();

        if (status == 200) {
            println(GREEN + "  ✓ 200 OK – Crawler run complete" + RESET);
            printJson(body);

            String processed = extractField(body, "processed");
            String errors    = extractField(body, "errors");
            println(CYAN + "  Keywords processed: " + (processed != null ? processed : "?") + RESET);
            if (errors != null && !errors.equals("0")) {
                println(RED + "  Errors: " + errors + RESET);
            }
        } else if (status == 503) {
            println(RED + "  ✗ 503 Service Unavailable – alert-service not reachable" + RESET);
            printJson(body);
        } else {
            println(RED + "  ✗ HTTP " + status + " – " + body + RESET);
        }

        pause(500);
        println(CYAN + "  Step 3: New results saved to PostgreSQL (table: search_results)" + RESET);
        pause(300);
        println(CYAN + "  Step 4: Results indexed in Elasticsearch via Hibernate Search" + RESET);
        println(DIM  + "          @Indexed + @FullTextField ensures automatic indexing on persist()" + RESET);
        pause(300);
        println(CYAN + "  Step 5: Kafka message sent to alert-service (topic: new-results)" + RESET);
        pause(300);
        println(CYAN + "  Step 6: Email notifications sent for matching users" + RESET);
        pause(400);

        // ── Elasticsearch index stats ─────────────────────────────────────────
        println("");
        println(BOLD + CYAN + "  Elasticsearch index state:" + RESET);
        HttpResponse<String> esResp = getRaw(ES_URL + "/_cat/indices?v");
        if (esResp != null && esResp.statusCode() == 200) {
            println(GREEN + "  ✓ Elasticsearch reachable" + RESET);
            for (String line : esResp.body().split("\n")) {
                println("  " + DIM + line + RESET);
            }
        } else {
            println(RED + "  ✗ Could not reach Elasticsearch at " + ES_URL + RESET);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  [4]  DATABASE DEMO
    // ══════════════════════════════════════════════════════════════════════════

    static void runDatabaseDemo(Scanner sc) throws Exception {
        sectionHeader("DATABASE DEMO", "PostgreSQL live state via JDBC");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            println(GREEN + "  ✓ Connected to PostgreSQL at " + DB_URL + RESET);

            // ── users ─────────────────────────────────────────────────────────
            tableHeader("users");
            runQuery(conn,
                "SELECT id, email, created_at FROM users ORDER BY id",
                new String[]{"ID", "Email", "Created At"});

            pause(300);

            // ── alerts ────────────────────────────────────────────────────────
            tableHeader("alerts");
            runQuery(conn,
                "SELECT id, user_id, keyword, active, created_at FROM alerts ORDER BY id",
                new String[]{"ID", "User ID", "Keyword", "Active", "Created At"});

            pause(300);

            // ── notification_log ──────────────────────────────────────────────
            tableHeader("notification_log  (last 5 entries)");
            runQuery(conn,
                "SELECT id, keyword, result_count, sent_at " +
                "FROM notification_log ORDER BY sent_at DESC LIMIT 5",
                new String[]{"ID", "Keyword", "# Results", "Sent At"});

        } catch (SQLException e) {
            println(RED + "  ✗ Database connection failed: " + e.getMessage() + RESET);
            println(YELLOW + "  Make sure PostgreSQL is running and credentials are correct." + RESET);
            println(YELLOW + "  DB_URL=" + DB_URL + "  USER=" + DB_USER + RESET);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  [5]  ELASTICSEARCH / HIBERNATE SEARCH DEMO
    // ══════════════════════════════════════════════════════════════════════════

    static void runElasticsearchDemo(Scanner sc) throws Exception {
        sectionHeader("ELASTICSEARCH DEMO", "Hibernate Search – Volltextsuche mit Elasticsearch");

        // ── Annotation explanation ────────────────────────────────────────────
        println(CYAN + "  How Hibernate Search connects JPA ↔ Elasticsearch:" + RESET);
        pause(200);
        println(YELLOW + "  @Indexed" + RESET + "                 → Entity SearchResult is automatically indexed");
        pause(150);
        println(YELLOW + "  @FullTextField" + RESET + "           → title, snippet   – analysed, supports fuzzy search");
        pause(150);
        println(YELLOW + "  @KeywordField" + RESET + "            → url, source, keyword – exact match (not analysed)");
        pause(150);
        println(YELLOW + "  @GenericField(sortable=YES)" + RESET + " → discoveredAt – enables ORDER BY in search");
        pause(300);
        println("");

        // ── Ask for search term ───────────────────────────────────────────────
        System.out.print(CYAN + "  Enter a search term (e.g. \"AI\"): " + RESET);
        String term = sc.nextLine().trim();
        if (term.isEmpty()) term = "AI";
        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);

        // ── Full-text / fuzzy search ──────────────────────────────────────────
        stepHeader("Search 1 / 2", "Full-text fuzzy search on title + snippet");
        String fuzzyUrl = NEWS_SERVICE_URL + "/api/search?q=" + encodedTerm + "&page=0&size=5";
        printRequest("GET", fuzzyUrl, null);

        HttpResponse<String> fuzzyResp = getRaw(fuzzyUrl);
        if (fuzzyResp != null && fuzzyResp.statusCode() == 200) {
            println(GREEN + "  ✓ 200 OK" + RESET);
            printSearchResults(fuzzyResp.body());
        } else if (fuzzyResp != null) {
            println(RED + "  ✗ HTTP " + fuzzyResp.statusCode() + " – " + fuzzyResp.body() + RESET);
        }

        pause(500);

        // ── Exact keyword search ──────────────────────────────────────────────
        stepHeader("Search 2 / 2", "Exact @KeywordField match (filter by keyword)");
        String exactUrl = NEWS_SERVICE_URL + "/api/search?q=" + encodedTerm + "&keyword=" + encodedTerm + "&page=0&size=5";
        printRequest("GET", exactUrl, null);

        HttpResponse<String> exactResp = getRaw(exactUrl);
        if (exactResp != null && exactResp.statusCode() == 200) {
            println(GREEN + "  ✓ 200 OK" + RESET);
            printSearchResults(exactResp.body());
        } else if (exactResp != null) {
            println(RED + "  ✗ HTTP " + exactResp.statusCode() + " – " + exactResp.body() + RESET);
        }

        pause(400);

        // ── Difference explanation ────────────────────────────────────────────
        println("");
        println(CYAN + "  Difference between the two queries:" + RESET);
        println(YELLOW + "  Search 1 (fuzzy):  matches 'AI', 'A.I.', 'artificial intelligence' – analysed field" + RESET);
        println(YELLOW + "  Search 2 (exact):  additionally filters keyword == \"" + term + "\" – raw @KeywordField" + RESET);

        pause(300);

        // ── Doc count from ES ─────────────────────────────────────────────────
        println("");
        println(CYAN + "  Total documents in Elasticsearch:" + RESET);
        HttpResponse<String> countResp = getRaw(ES_URL + "/_cat/count?v");
        if (countResp != null && countResp.statusCode() == 200) {
            for (String line : countResp.body().split("\n")) {
                println("  " + DIM + line + RESET);
            }
        } else {
            println(RED + "  ✗ Could not reach Elasticsearch at " + ES_URL + RESET);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  [6]  FULL FLOW DEMO  (automated)
    // ══════════════════════════════════════════════════════════════════════════

    static void runFullFlowDemo(Scanner sc) throws Exception {
        sectionHeader("FULL FLOW DEMO", "Automated end-to-end walkthrough (6 steps)");
        println(YELLOW + "  Running fully automated – no input needed." + RESET);
        pause(1500);

        // ── STEP 1/6 : Auth ───────────────────────────────────────────────────
        flowStep(1, 6, "AUTHENTICATION");
        runAuthDemo(sc);
        pause(2000);

        // ── STEP 2/6 : Keywords ───────────────────────────────────────────────
        flowStep(2, 6, "KEYWORD MANAGEMENT");
        println(YELLOW + "  (Auto-using keyword 'Artificial Intelligence')" + RESET);

        ensureToken(null);
        if (jwtToken != null) {
            String createPayload = """
                    {
                      "keyword": "Artificial Intelligence"
                    }""";
            printRequest("POST", ALERT_SERVICE_URL + "/api/alerts", createPayload);
            HttpResponse<String> cr = post(ALERT_SERVICE_URL + "/api/alerts", createPayload);
            if (cr != null) {
                if (cr.statusCode() == 201) {
                    println(GREEN + "  ✓ Keyword created: 'Artificial Intelligence'" + RESET);
                    lastAlertId = extractField(cr.body(), "id");
                } else {
                    println(YELLOW + "  Response: " + cr.statusCode() + " – " + cr.body() + RESET);
                }
            }
            pause(500);
            printRequest("GET", ALERT_SERVICE_URL + "/api/alerts", null);
            HttpResponse<String> lr = get(ALERT_SERVICE_URL + "/api/alerts");
            if (lr != null) {
                println(GREEN + "  ✓ Current keyword list:" + RESET);
                printJson(lr.body());
            }
        }
        pause(2000);

        // ── STEP 3/6 : Crawler ────────────────────────────────────────────────
        flowStep(3, 6, "CRAWLER & INDEXING");
        runCrawlerDemo(sc);
        pause(2000);

        // ── STEP 4/6 : Database ───────────────────────────────────────────────
        flowStep(4, 6, "POSTGRESQL STATE");
        runDatabaseDemo(sc);
        pause(2000);

        // ── STEP 5/6 : Elasticsearch ──────────────────────────────────────────
        flowStep(5, 6, "ELASTICSEARCH / HIBERNATE SEARCH");
        // Automated search – no scanner interaction
        String term = "Artificial Intelligence";
        String enc  = URLEncoder.encode(term, StandardCharsets.UTF_8);

        println(CYAN + "  Annotation model:" + RESET);
        pause(200); println(YELLOW + "  @Indexed / @FullTextField / @KeywordField / @GenericField(sortable=YES)" + RESET);
        pause(500);

        println(CYAN + "\n  Fuzzy full-text search for: \"" + term + "\"" + RESET);
        HttpResponse<String> fr = getRaw(NEWS_SERVICE_URL + "/api/search?q=" + enc + "&page=0&size=3");
        if (fr != null && fr.statusCode() == 200) {
            println(GREEN + "  ✓ Results:" + RESET);
            printSearchResults(fr.body());
        }
        pause(1000);

        println(CYAN + "\n  Exact keyword filter for: \"" + term + "\"" + RESET);
        HttpResponse<String> er = getRaw(NEWS_SERVICE_URL + "/api/search?q=" + enc + "&keyword=" + enc + "&page=0&size=3");
        if (er != null && er.statusCode() == 200) {
            println(GREEN + "  ✓ Results:" + RESET);
            printSearchResults(er.body());
        }
        pause(2000);

        // ── STEP 6/6 : Cleanup ────────────────────────────────────────────────
        flowStep(6, 6, "CLEANUP & SUMMARY");
        if (lastAlertId != null && jwtToken != null) {
            println(YELLOW + "  Deleting demo keyword (id=" + lastAlertId + ")..." + RESET);
            HttpResponse<String> del = delete(ALERT_SERVICE_URL + "/api/alerts/" + lastAlertId);
            if (del != null && del.statusCode() == 204) {
                println(GREEN + "  ✓ Demo keyword removed." + RESET);
            }
        }

        pause(500);
        println("");
        println(BOLD + GREEN + "  ╔══════════════════════════════════════════╗" + RESET);
        println(BOLD + GREEN + "  ║   Full Demo Complete  ✓                  ║" + RESET);
        println(BOLD + GREEN + "  ║                                          ║" + RESET);
        println(BOLD + GREEN + "  ║   Services demonstrated:                 ║" + RESET);
        println(BOLD + GREEN + "  ║   • alert-service  (Quarkus, JWT, PG)   ║" + RESET);
        println(BOLD + GREEN + "  ║   • news-service   (Crawler, ES, Kafka) ║" + RESET);
        println(BOLD + GREEN + "  ║   • PostgreSQL     (live JDBC)          ║" + RESET);
        println(BOLD + GREEN + "  ║   • Elasticsearch  (Hibernate Search)   ║" + RESET);
        println(BOLD + GREEN + "  ╚══════════════════════════════════════════╝" + RESET);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HTTP helpers
    // ══════════════════════════════════════════════════════════════════════════

    static HttpResponse<String> post(String url, String jsonBody) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"))
                    .build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            println(RED + "  ✗ Cannot reach " + url + ": " + e.getMessage() + RESET);
            return null;
        }
    }

    static HttpResponse<String> get(String url) {
        try {
            if (jwtToken == null) {
                println(RED + "  ✗ No JWT token available. Run Auth Demo first." + RESET);
                return null;
            }
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            println(RED + "  ✗ Cannot reach " + url + ": " + e.getMessage() + RESET);
            return null;
        }
    }

    static HttpResponse<String> getRaw(String url) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json,text/plain,*/*")
                    .GET()
                    .build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            println(RED + "  ✗ Cannot reach " + url + ": " + e.getMessage() + RESET);
            return null;
        }
    }

    static HttpResponse<String> delete(String url) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + jwtToken)
                    .DELETE()
                    .build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            println(RED + "  ✗ Cannot reach " + url + ": " + e.getMessage() + RESET);
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Database helpers
    // ══════════════════════════════════════════════════════════════════════════

    static void runQuery(Connection conn, String sql, String[] headers) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            // Collect all rows first so we can size columns
            List<String[]> rows = new ArrayList<>();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] row = new String[cols];
                for (int i = 0; i < cols; i++) {
                    Object val = rs.getObject(i + 1);
                    row[i] = val == null ? "NULL" : val.toString();
                }
                rows.add(row);
            }

            // Calculate column widths
            int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++) widths[i] = headers[i].length();
            for (String[] row : rows) {
                for (int i = 0; i < Math.min(row.length, widths.length); i++) {
                    widths[i] = Math.max(widths[i], Math.min(row[i].length(), 40));
                }
            }

            // Print table
            String separator = buildSeparator(widths);
            println(DIM + "  " + separator + RESET);
            System.out.print("  " + CYAN + "| ");
            for (int i = 0; i < headers.length; i++) {
                System.out.print(pad(headers[i], widths[i]) + " | ");
            }
            println(RESET);
            println(DIM + "  " + separator + RESET);
            for (String[] row : rows) {
                System.out.print("  " + WHITE + "| ");
                for (int i = 0; i < headers.length; i++) {
                    String val = i < row.length ? truncate(row[i], 40) : "";
                    System.out.print(pad(val, widths[i]) + " | ");
                }
                println(RESET);
            }
            println(DIM + "  " + separator + RESET);
            println(GREEN + "  " + rows.size() + " row(s)" + RESET);
        } catch (SQLException e) {
            // table might not exist yet
            println(YELLOW + "  (table not found or empty: " + e.getMessage() + ")" + RESET);
        }
    }

    static String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) sb.append("-".repeat(w + 2)).append("+");
        return sb.toString();
    }

    static String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  JWT / JSON helpers  (no external libraries – manual parsing)
    // ══════════════════════════════════════════════════════════════════════════

    static String extractToken(String json) {
        // Matches: "token":"<value>"  or  "accessToken":"<value>"
        Pattern p = Pattern.compile("\"(?:token|accessToken)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String extractField(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+(?:\\.\\d+)?)|true|false)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    static void showToken(String token, String source) {
        String display = token.length() > 50 ? token.substring(0, 50) + "..." : token;
        println(YELLOW + "  Token from " + source + ":" + RESET);
        println(GREEN  + "  " + display + RESET);

        // Decode JWT payload (middle part)
        String[] parts = token.split("\\.");
        if (parts.length >= 2) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(
                        parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4));
                String payload = new String(decoded, StandardCharsets.UTF_8);
                println(CYAN + "  JWT payload (decoded):" + RESET);

                // Print interesting fields
                for (String field : new String[]{"iss", "upn", "sub", "groups", "userId", "exp"}) {
                    String val = extractField(payload, field);
                    if (val != null) {
                        println(DIM + "    " + field + ": " + RESET + WHITE + val + RESET);
                    }
                }
            } catch (Exception ex) {
                println(DIM + "  (could not decode JWT payload)" + RESET);
            }
        }
    }

    /** Pretty-print JSON with indentation colour. Very light-weight, no parser. */
    static void printJson(String json) {
        if (json == null || json.isBlank()) return;
        String[] lines = json
                .replaceAll("\\{", "{\n")
                .replaceAll("\\}", "\n}")
                .replaceAll(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", ",\n")  // commas outside strings
                .split("\n");
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("}") || line.startsWith("]")) indent = Math.max(0, indent - 1);
            sb.append("  ").append(DIM).append("  ".repeat(indent)).append(RESET);
            // Colour keys vs values
            if (line.matches("\"[^\"]+\"\\s*:.*")) {
                int colon = line.indexOf(':');
                sb.append(CYAN).append(line, 0, colon + 1).append(RESET)
                  .append(GREEN).append(line.substring(colon + 1)).append(RESET);
            } else {
                sb.append(GREEN).append(line).append(RESET);
            }
            sb.append("\n");
            if (line.endsWith("{") || line.endsWith("[")) indent++;
        }
        System.out.print(sb);
    }

    /** Print top-5 search results from SearchResponse JSON. */
    static void printSearchResults(String json) {
        if (json == null || json.isBlank()) { println(YELLOW + "  (empty response)" + RESET); return; }

        String total = extractField(json, "totalHits");
        println(CYAN + "  Total hits: " + (total != null ? total : "?") + RESET);

        // Extract content array elements – find each "title" occurrence
        Pattern titlePat   = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]{0,120})\"");
        Pattern sourcePat  = Pattern.compile("\"source\"\\s*:\\s*\"([^\"]{0,60})\"");
        Pattern datePat    = Pattern.compile("\"discoveredAt\"\\s*:\\s*\"([^\"]{0,30})\"");

        Matcher tm = titlePat.matcher(json);
        Matcher sm = sourcePat.matcher(json);
        Matcher dm = datePat.matcher(json);

        List<String> titles  = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> dates   = new ArrayList<>();

        while (tm.find()) titles.add(tm.group(1));
        while (sm.find()) sources.add(sm.group(1));
        while (dm.find()) dates.add(dm.group(1));

        if (titles.isEmpty()) {
            println(YELLOW + "  (no results found)" + RESET);
            return;
        }

        // ASCII table header
        println(DIM + "  +---+--------------------------------------------------+--------------------+------------+" + RESET);
        println(CYAN + "  | # | Title                                            | Source             | Date       |" + RESET);
        println(DIM + "  +---+--------------------------------------------------+--------------------+------------+" + RESET);

        for (int i = 0; i < Math.min(5, titles.size()); i++) {
            String t = pad(truncate(titles.get(i), 50), 50);
            String s = pad(i < sources.size() ? truncate(sources.get(i), 20) : "-", 20);
            String d = pad(i < dates.size()   ? truncate(dates.get(i), 10)   : "-", 10);
            println(WHITE + "  | " + (i + 1) + " | " + t + " | " + s + " | " + d + " |" + RESET);
        }
        println(DIM + "  +---+--------------------------------------------------+--------------------+------------+" + RESET);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI helpers
    // ══════════════════════════════════════════════════════════════════════════

    static void printBanner() {
        println(BOLD + CYAN + "  ═══════════════════════════════════════════════════" + RESET);
        println(BOLD + CYAN + "         NewsAlert  –  Live Demo Console" + RESET);
        println(BOLD + CYAN + "  ═══════════════════════════════════════════════════" + RESET);
        println(DIM  +        "  Keyword-based news monitoring & alerting system" + RESET);
        println(DIM  +        "  alert-service: " + ALERT_SERVICE_URL + "   news-service: " + NEWS_SERVICE_URL + RESET);
        println("");
    }

    static void printMenu() {
        println(CYAN + "  ───────────────────────────────────────────────────" + RESET);
        println(BOLD  + "   [1]" + RESET + " Auth Demo         " + DIM + "– Register & Login" + RESET);
        println(BOLD  + "   [2]" + RESET + " Keyword Demo      " + DIM + "– Add / List / Delete" + RESET);
        println(BOLD  + "   [3]" + RESET + " Crawler Demo      " + DIM + "– Trigger & Watch" + RESET);
        println(BOLD  + "   [4]" + RESET + " Database Demo     " + DIM + "– PostgreSQL live state" + RESET);
        println(BOLD  + "   [5]" + RESET + " Elasticsearch     " + DIM + "– Hibernate Search query" + RESET);
        println(BOLD  + "   [6]" + RESET + " Full Flow Demo    " + DIM + "– Everything end-to-end" + RESET);
        println(BOLD  + "   [0]" + RESET + " Exit" + RESET);
        println(CYAN + "  ───────────────────────────────────────────────────" + RESET);
    }

    static void sectionHeader(String title, String subtitle) {
        println("");
        println(BOLD + CYAN + "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        println(BOLD + CYAN + "    " + title + RESET);
        println(DIM           + "    " + subtitle + RESET);
        println(BOLD + CYAN + "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        println("");
    }

    static void stepHeader(String step, String title) {
        println("");
        println(YELLOW + "  ┌─ " + BOLD + step + RESET + YELLOW + " ──────────────────────────────────────────" + RESET);
        println(YELLOW + "  │  " + title + RESET);
        println(YELLOW + "  └──────────────────────────────────────────────────" + RESET);
    }

    static void flowStep(int n, int total, String title) throws Exception {
        println("");
        println(BOLD + GREEN + "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        println(BOLD + GREEN + "    STEP " + n + "/" + total + ": " + title + RESET);
        println(BOLD + GREEN + "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        pause(500);
    }

    static void tableHeader(String name) {
        println("");
        println(BOLD + CYAN + "  Table: " + name + RESET);
    }

    static void printRequest(String method, String url, String body) {
        println("");
        println(BOLD + "  " + YELLOW + method + RESET + " " + url);
        if (body != null && !body.isBlank()) {
            println(DIM + "  Request body:" + RESET);
            printJson(body);
        }
        if (jwtToken != null && !url.contains("/auth/")) {
            println(DIM + "  Authorization: Bearer " + jwtToken.substring(0, Math.min(30, jwtToken.length())) + "..." + RESET);
        }
    }

    static void ensureToken(Scanner sc) throws Exception {
        if (jwtToken != null) return;
        println(YELLOW + "  No JWT token found. Attempting auto-login..." + RESET);

        String loginPayload = """
                {
                  "email": "%s",
                  "password": "%s"
                }""".formatted(DEMO_EMAIL, DEMO_PASSWORD);

        HttpResponse<String> resp = post(ALERT_SERVICE_URL + "/api/auth/login", loginPayload);
        if (resp != null && resp.statusCode() == 200) {
            jwtToken = extractToken(resp.body());
            if (jwtToken != null) println(GREEN + "  ✓ Auto-login successful." + RESET);
        }
        if (jwtToken == null) {
            // Try register
            HttpResponse<String> reg = post(ALERT_SERVICE_URL + "/api/auth/register", loginPayload);
            if (reg != null && (reg.statusCode() == 201 || reg.statusCode() == 409)) {
                // If 409 login already failed, nothing more we can do
                if (reg.statusCode() == 201) jwtToken = extractToken(reg.body());
            }
        }
        if (jwtToken == null) {
            println(RED + "  ✗ Could not obtain token. Run [1] Auth Demo manually first." + RESET);
        }
    }

    static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    static void println(String s) {
        System.out.println(s);
    }

    static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
