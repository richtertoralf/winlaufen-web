package de.winlaufen.web.bridge.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.startlist.StartListParser;
import de.winlaufen.web.bridge.startlist.StartListStore;
import de.winlaufen.web.bridge.startlist.TestWorkbook;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start-list import as an organiser actually performs it: pick a file in Bridge Control and
 * upload it to the running bridge.
 *
 * <p>The tests go through real HTTP against a started server rather than calling the handler, so
 * the rules that only exist at the network boundary — Origin, content type, size limit, status
 * codes — are covered too.
 */
class BridgeControlStartListTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Prologue: bib 12 is Anna, in class U16. */
    private static final String PROLOGUE = """
            StNr,Klasse,Name,Vorname,Startzeit
            12,U16,MÜLLER,Anna,10:00:15
            13,U16,MEIER,Ben,10:00:30
            14,U18,SCHMIDT,Cara,10:01:00
            """;

    /** Heat: bib 12 is somebody else, in a different class, and bib 13 is gone. */
    private static final String HEAT = """
            StNr,Klasse,Name,Vorname,Startzeit
            12,U20,VOBORNÍKOVÁ,Tereza,14:00:00
            """;

    @TempDir
    Path temp;

    private StartListStore startLists;
    private BridgeControlServer server;

    @BeforeEach
    void start() throws Exception {
        startLists = StartListStore.besideConfig(temp.resolve("bridge.properties"));
        server = newServer(startLists);
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private BridgeControlServer newServer(StartListStore store) throws Exception {
        return new BridgeControlServer("127.0.0.1", 0, new CanonicalStateStore(
                PresentationConfig.defaults()),
                new BridgeConfigStore(temp.resolve("bridge.properties")), store,
                BridgeControlStartListTest::config, List::of, ignored -> { });
    }

    private static BridgeConfig config() {
        return new BridgeConfig("WINLAUFEN", "localhost", "127.0.0.1", 44442,
                List.of(new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                        URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"), "local",
                        BridgeConfigStore.DEFAULT_LOCAL_SECRET)),
                PresentationConfig.defaults());
    }

    // --- Successful import ---------------------------------------------------------------------

    @Test
    void aCsvUploadBecomesTheCurrentStartList() throws Exception {
        HttpResponse<String> response = upload("Startliste.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = MAPPER.readTree(response.body());
        assertEquals(1, result.get("generation").asLong());
        assertEquals("IMPORT_CSV", result.get("source").asText());
        assertEquals("Startliste.csv", result.get("sourceLabel").asText());
        assertEquals(3, result.get("entryCount").asInt());
        assertEquals(2, result.get("classCount").asInt());

        assertEquals(1, startLists.current().generation());
        assertEquals("Anna", startLists.current().find("U16", "12").firstName());
    }

    @Test
    void anXlsxUploadIsAcceptedWithItsParticipantCount() throws Exception {
        byte[] workbook = TestWorkbook.realShape(List.of(
                List.of("Name", "Vorname", "StNr", "Klasse", "Startzeit"),
                List.of("BÖRNER", "Alwin", "5", "Schüler U12 m", "10:01:00"),
                List.of("MEIER", "Ben", "6", "Schüler U12 m", "10:01:15")));

        HttpResponse<String> response = upload("Startliste.xlsx", workbook);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = MAPPER.readTree(response.body());
        assertEquals("IMPORT_XLSX", result.get("source").asText());
        assertEquals(2, result.get("entryCount").asInt());
        assertEquals(1, result.get("classCount").asInt());
        assertEquals("Schüler U12 m", startLists.current().entries().getFirst().className());
    }

    @Test
    void aTxtExportIsAcceptedAsDelimitedText() throws Exception {
        assertEquals(200, upload("Startliste.txt",
                PROLOGUE.getBytes(StandardCharsets.UTF_8)).statusCode());

        assertEquals(3, startLists.current().entries().size());
    }

    // --- Replacement ---------------------------------------------------------------------------

    @Test
    void aSecondImportReplacesTheWholeStartList() throws Exception {
        assertEquals(200, upload("A.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8)).statusCode());

        HttpResponse<String> second = upload("B.csv", HEAT.getBytes(StandardCharsets.UTF_8));

        assertEquals(200, second.statusCode(), second.body());
        assertEquals(2, startLists.current().generation());
        assertEquals(1, startLists.current().entries().size());
        assertNull(startLists.current().find("U16", "12"),
                "the prologue assignment of bib 12 must not survive the new start list");
        assertNull(startLists.current().find("U16", "13"),
                "a participant missing from the new list is gone, never merged in");
        assertEquals("Tereza", startLists.current().find("U20", "12").firstName());
        assertEquals("B.csv", startLists.current().sourceLabel());
    }

    // --- Failures leave everything as it was ---------------------------------------------------

    @Test
    void anInvalidUploadIsRejectedAndChangesNothing() throws Exception {
        upload("A.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = upload("B.csv",
                "StNr,Klasse\n12,U16\n12,U16\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode());
        assertUnchangedPrologue();
        assertTrue(MAPPER.readTree(response.body()).get("error").asText().contains("12"),
                "the parser's own message reaches the operator: " + response.body());
    }

    @Test
    void anErrorResponseNeverCarriesAStackTrace() throws Exception {
        HttpResponse<String> response = upload("B.csv", "Foo|Bar\n1|2\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode());
        String error = MAPPER.readTree(response.body()).get("error").asText();
        assertFalse(error.contains("Exception"), error);
        assertFalse(error.contains("de.winlaufen"), error);
        assertFalse(error.contains("\tat "), error);
        assertNotNull(error);
    }

    @Test
    void legacyXlsIsRejectedAndChangesNothing() throws Exception {
        upload("A.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = upload("Startliste.xls", new byte[] {1, 2, 3});

        assertEquals(400, response.statusCode());
        assertTrue(MAPPER.readTree(response.body()).get("error").asText().contains(".xls"));
        assertUnchangedPrologue();
    }

    @Test
    void anOversizedUploadIsRejectedAndChangesNothing() throws Exception {
        upload("A.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));
        byte[] huge = new byte[StartListParser.MAX_INPUT_BYTES + 1];

        HttpResponse<String> response = upload("B.csv", huge);

        assertEquals(413, response.statusCode());
        assertUnchangedPrologue();
    }

    @Test
    void aMissingFileNameIsRejected() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(base()
                        + "/api/v1/startlist"))
                .header("Content-Type", "application/octet-stream")
                .header("Origin", base())
                .POST(HttpRequest.BodyPublishers.ofByteArray(PROLOGUE.getBytes(StandardCharsets.UTF_8))));

        assertEquals(400, response.statusCode());
        assertEquals(0, startLists.current().generation());
    }

    // --- Network boundary ----------------------------------------------------------------------

    @Test
    void aForeignOrAbsentOriginIsRejected() throws Exception {
        HttpRequest.Builder foreign = HttpRequest.newBuilder(
                        URI.create(base() + "/api/v1/startlist?name=B.csv"))
                .header("Content-Type", "application/octet-stream")
                .header("Origin", "http://angreifer.example")
                .POST(HttpRequest.BodyPublishers.ofByteArray(PROLOGUE.getBytes(StandardCharsets.UTF_8)));
        HttpRequest.Builder anonymous = HttpRequest.newBuilder(
                        URI.create(base() + "/api/v1/startlist?name=B.csv"))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(PROLOGUE.getBytes(StandardCharsets.UTF_8)));

        assertEquals(403, send(foreign).statusCode());
        assertEquals(403, send(anonymous).statusCode());
        assertEquals(0, startLists.current().generation());
    }

    @Test
    void aFormPostIsRejectedBecauseTheUploadIsNotAFormContentType() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(base() + "/api/v1/startlist?name=B.csv"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", base())
                .POST(HttpRequest.BodyPublishers.ofString("x=1")));

        assertEquals(415, response.statusCode());
        assertEquals(0, startLists.current().generation());
    }

    @Test
    void theUploadPathAnswersNothingButPost() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                URI.create(base() + "/api/v1/startlist")).GET());

        assertEquals(404, response.statusCode());
    }

    /**
     * The file name is a format selector and a label, never a path. A name that tries to escape
     * the directory is reduced to its last segment and nothing is written under it.
     */
    @Test
    void aFileNameIsNeverUsedAsAPath() throws Exception {
        assertEquals(200, upload("../../etc/Startliste.csv",
                PROLOGUE.getBytes(StandardCharsets.UTF_8)).statusCode());

        assertEquals("Startliste.csv", startLists.current().sourceLabel());
        try (var files = Files.list(temp)) {
            assertEquals(List.of("startlist.properties"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    // --- Status --------------------------------------------------------------------------------

    @Test
    void theStatusReportsNoStartListBeforeTheFirstImport() throws Exception {
        JsonNode startList = MAPPER.readTree(get("/api/v1/status").body()).get("startList");

        assertEquals(0, startList.get("generation").asLong());
        assertEquals(0, startList.get("entryCount").asInt());
        assertEquals(0, startList.get("classCount").asInt());
        assertEquals("", startList.get("sourceLabel").asText());
    }

    @Test
    void theStatusReportsEveryFieldTheSurfaceShows() throws Exception {
        upload("Startliste.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));

        JsonNode startList = MAPPER.readTree(get("/api/v1/status").body()).get("startList");

        assertEquals("Startliste.csv", startList.get("sourceLabel").asText());
        assertEquals("IMPORT_CSV", startList.get("source").asText());
        assertEquals(1, startList.get("generation").asLong());
        assertEquals(3, startList.get("entryCount").asInt());
        assertEquals(2, startList.get("classCount").asInt());
    }

    @Test
    void theStatusStillCarriesTheSourceAndOutputInformation() throws Exception {
        JsonNode status = MAPPER.readTree(get("/api/v1/status").body());

        assertEquals("DISCONNECTED", status.get("sourceHealth").asText());
        assertTrue(status.has("sourceRevision"));
        assertTrue(status.has("outputs"));
    }

    // --- Restart -------------------------------------------------------------------------------

    @Test
    void anImportedStartListSurvivesARestartOfTheBridge() throws Exception {
        upload("Startliste.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));
        upload("Heat.csv", HEAT.getBytes(StandardCharsets.UTF_8));
        server.close();

        // Exactly what a restarted bridge does: open the store at the same place again.
        StartListStore restarted = StartListStore.besideConfig(temp.resolve("bridge.properties"));
        server = newServer(restarted);
        server.start();

        JsonNode startList = MAPPER.readTree(get("/api/v1/status").body()).get("startList");
        assertEquals(2, startList.get("generation").asLong(), "a restart never counts as an import");
        assertEquals("Heat.csv", startList.get("sourceLabel").asText());
        assertEquals(1, startList.get("entryCount").asInt());
        assertEquals("Tereza", restarted.current().find("U20", "12").firstName());
    }

    @Test
    void anImportAfterARestartContinuesTheGeneration() throws Exception {
        upload("Startliste.csv", PROLOGUE.getBytes(StandardCharsets.UTF_8));
        server.close();
        startLists = StartListStore.besideConfig(temp.resolve("bridge.properties"));
        server = newServer(startLists);
        server.start();

        HttpResponse<String> response = upload("Heat.csv", HEAT.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, MAPPER.readTree(response.body()).get("generation").asLong());
    }

    // --- Helpers -------------------------------------------------------------------------------

    private void assertUnchangedPrologue() {
        assertEquals(1, startLists.current().generation(), "a rejected import changes no generation");
        assertEquals(3, startLists.current().entries().size());
        assertEquals("Anna", startLists.current().find("U16", "12").firstName());
        assertEquals("A.csv", startLists.current().sourceLabel());
    }

    private HttpResponse<String> upload(String name, byte[] data) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/startlist?name="
                        + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .header("Content-Type", "application/octet-stream")
                .header("Origin", base())
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base() + path)).GET());
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://127.0.0.1:" + server.port();
    }
}
