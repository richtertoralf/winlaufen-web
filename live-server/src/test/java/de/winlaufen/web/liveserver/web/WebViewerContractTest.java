package de.winlaufen.web.liveserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import de.winlaufen.web.liveserver.state.PublishedState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Product semantics of the web viewer.
 *
 * <p>The column projection and the LIVE class rule are asserted as behaviour against the real
 * published JSON. The project has no JavaScript runtime available and must not add one, so the
 * viewer's own implementation of these two rules is additionally pinned by source assertions that
 * fail if the rule is weakened again.
 */
class WebViewerContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> BIATHLON_HEADERS = List.of(
            "Rang", "StNr", "Name, Vorname", "Verein", "Vbd", "Nation", "Schießen",
            "Gesamtzeit", "Rückstand");
    private static final List<String> BIATHLON_ROW = List.of(
            "1", "101", "Name", "Club", "Vbd-Wert", "DE", "1 0 2 0 ", "3:30:35.1", "0:00:00.0");

    @Test
    void exactPublicColumnOptionsKeepHeadersAndCellsIndexAligned() {
        assertProjection(new PresentationConfig(true, true, true, true, false),
                BIATHLON_HEADERS, BIATHLON_ROW);
        assertProjection(new PresentationConfig(false, true, true, true, false),
                without("Verein"), withoutCell(3));
        assertProjection(new PresentationConfig(true, false, true, true, false),
                without("Vbd"), withoutCell(4));
        assertProjection(new PresentationConfig(true, true, false, true, false),
                without("Nation"), withoutCell(5));
        assertProjection(new PresentationConfig(true, true, true, false, false),
                without("Schießen"), withoutCell(6));
        assertProjection(new PresentationConfig(false, false, false, false, false),
                List.of("Rang", "StNr", "Name, Vorname", "Gesamtzeit", "Rückstand"),
                List.of("1", "101", "Name", "3:30:35.1", "0:00:00.0"));
    }

    @Test
    void liveViewFollowsTheClassOfTheNewestResultSnapshot() throws Exception {
        // Class 0 reports first, then class 1. LIVE must move to class 1.
        JsonNode first = MAPPER.readTree(PublicJson.state(published(1, 0)));
        assertEquals(0, first.get("state").get("currentFinish").get("classIndex").asInt());
        assertEquals(0, liveClassIndex(null, first));

        JsonNode second = MAPPER.readTree(PublicJson.state(published(2, 1)));
        assertEquals(1, second.get("state").get("currentFinish").get("classIndex").asInt());
        assertEquals(1, liveClassIndex(0, second),
                "LIVE must follow the class of the newest result snapshot");

        // And back again, which the pinned-once implementation could never do.
        JsonNode third = MAPPER.readTree(PublicJson.state(published(3, 0)));
        assertEquals(0, liveClassIndex(1, third));
    }

    @Test
    void viewerAppliesTheLiveClassRuleUnconditionally() throws Exception {
        String script = resource("/web-viewer/viewer.js");
        assertTrue(script.contains("if (state.currentFinish) liveClassIndex = state.currentFinish.classIndex;"),
                "LIVE must adopt the transmitted class on every snapshot");
        assertFalse(script.contains("liveClassIndex === null"),
                "the class must not be pinned to the first snapshot ever received");
    }

    @Test
    void rebuildsTheClassPickerOnlyWhenTheOfferedClassesChanged() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        // Ein Uhrtelegramm erzeugt sekuendlich einen Snapshot. Baut die Ergebnisansicht dabei
        // ihre Klassenauswahl neu auf, zerstoert das eine offene mobile Auswahl.
        assertTrue(script.contains("JSON.stringify(classes.map(item => [item.index, item.name]))"),
                "the picker follows the offered classes, not every snapshot");
        assertEquals(1, occurrences(script, "select.replaceChildren("),
                "the option list is rebuilt in exactly one place");
        assertEquals(1, occurrences(script, "select.value = resultsClassIndex"),
                "the value is assigned in exactly one place");

        int guard = script.indexOf("if (signature !== renderedClasses)");
        int rebuild = script.indexOf("select.replaceChildren(");
        int value = script.indexOf("select.value = resultsClassIndex");
        int afterBlock = script.indexOf("const live = classes.find");
        assertTrue(guard > 0, "the rebuild is guarded by the class signature");
        assertTrue(guard < rebuild && rebuild < afterBlock,
                "the option list is only replaced inside the guard");
        assertTrue(guard < value && value < afterBlock,
                "the selected value is only reassigned inside the guard");

        // Die bestaetigte Auswahl bleibt Sache des change-Listeners.
        assertTrue(script.contains("resultsClassIndex = Number(select.value)"),
                "a confirmed choice is still stored");
        // LIVE bleibt von der Auswahl unabhaengig und folgt weiter der uebertragenen Klasse.
        assertTrue(script.contains("if (state.currentFinish) liveClassIndex = state.currentFinish.classIndex;"),
                "LIVE keeps following the transmitted class");
    }

    @Test
    void keepsViewsPublicOnlyRevisionGuardCurrentFinishAndConditionalMessages() throws Exception {
        String html = resource("/web-viewer/viewer.html");
        String script = resource("/web-viewer/viewer.js");

        assertTrue(html.contains("data-view=\"startlist\""));
        assertTrue(html.contains("data-view=\"live\""));
        assertTrue(html.contains("data-view=\"results\""));
        assertEquals(1, occurrences(html, "view active"));
        assertTrue(script.contains("message.publicationRevision < publicationRevision"));
        assertTrue(script.contains("index === highlighted"));
        assertTrue(script.contains("display.showPublicMessages && Boolean(state.message)"));
        assertTrue(script.contains("publicMessage.hidden = !visible"));
        assertFalse(script.contains("BridgeConfig"));
        assertFalse(script.contains("/bridge/"));
        assertFalse(script.contains("/api/v1/config"));
    }

    @Test
    void keepsDynamicColumnsStickyHeaderHorizontalScrollAndOneBasedHumanRound() throws Exception {
        String script = resource("/web-viewer/viewer.js");
        String css = resource("/web-viewer/viewer.css");

        for (String header : new String[]{"Verein", "Vbd", "Nation", "Schießen"}) {
            assertTrue(script.contains("header === '" + header + "'"), "missing column rule " + header);
        }
        assertTrue(script.indexOf("node.createTHead()") < script.indexOf("node.createTBody()"));
        assertTrue(css.contains("th{position:sticky;top:0;"));
        assertTrue(css.contains(".table-wrap{width:100%;overflow:auto"));
        assertTrue(script.contains("function displayRoundOrHeat(rawRoundOrHeat) { return rawRoundOrHeat + 1; }"));
        assertTrue(script.contains("displayRoundOrHeat(state.competition.roundOrHeat)"));
    }

    @Test
    void showsTheSpeakerWebProductNameAndTheSurfaceName() throws Exception {
        String html = resource("/web-viewer/viewer.html");

        assertTrue(html.contains("<title>Live-Ergebnisse · Sprecher-Web</title>"),
                "the browser tab names the surface and the product");
        assertTrue(html.contains(
                        "<span class=\"brand\">Sprecher-Web<span>Live-Ergebnisse aus WinLaufen</span></span>"),
                "the header names the product and where the shown data comes from");
        assertFalse(html.contains("WinLaufen Sprecher Web"), "the former product name is gone");
        assertFalse(html.contains("WinLaufen Web"), "the former ambiguous product name is gone");
        assertFalse(html.contains("Live-Ergebnisse für WinLaufen"),
                "the subtitle names the data source, it does not claim to be a product for WinLaufen");
    }

    @Test
    void showsTheMavenProjectVersionAndTheGitBuildIdInADiscreetFooter() throws Exception {
        String html = resource("/web-viewer/viewer.html");
        String css = resource("/web-viewer/viewer.css");
        String version = System.getProperty("product.version");
        // Without git metadata the plugin publishes nothing and the POM fallback applies.
        String buildId = System.getProperty("git.commit.id.describe", "unbekannt");

        assertNotNull(version, "the build passes the Maven project version to this test");
        assertTrue(html.contains("<footer class=\"version\">Sprecher-Web · Version " + version
                        + " · Build " + buildId + "</footer>"),
                "the footer shows exactly the built version and commit, never a hand-written copy");
        assertTrue(buildId.matches("unbekannt|[0-9a-f]{7,40}(-dirty)?"),
                "the build id stays an abbreviated commit id and never becomes a release tag");
        assertFalse(html.contains("${"), "no unresolved build placeholder reaches the browser");
        assertTrue(html.indexOf("<footer") > html.indexOf("</main>"),
                "the version stays below the content and never takes header space");
        assertTrue(css.contains("footer.version{"), "the footer keeps its own discreet rule");
    }

    @Test
    void temporarilyHighlightsTheCurrentFinishRow() throws Exception {
        String css = resource("/web-viewer/viewer.css");
        assertTrue(css.contains("tbody tr.current"), "the current finish row keeps a marker");
        assertTrue(css.contains("@keyframes arrival"), "the arrival emphasis must fade out again");
        assertTrue(css.contains("animation:arrival"));
        assertTrue(css.contains("prefers-reduced-motion"), "motion must be avoidable");
    }

    /** Mirrors the viewer's {@code visibleColumn} rule against the real published JSON. */
    private static void assertProjection(PresentationConfig presentation, List<String> expectedHeaders,
                                         List<String> expectedRow) {
        JsonNode state;
        try {
            state = MAPPER.readTree(PublicJson.state(new PublishedState(1, "stream", 1,
                    biathlonState(), presentation)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        JsonNode snapshot = state.get("state").get("competition").get("classes").get(0).get("snapshot");
        JsonNode display = state.get("presentation");

        List<String> headers = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        for (int index = 0; index < snapshot.get("headers").size(); index++) {
            String header = snapshot.get("headers").get(index).asText();
            if (visibleColumn(header, display)) {
                headers.add(header);
                cells.add(snapshot.get("rows").get(0).get(index).asText());
            }
        }
        assertEquals(expectedHeaders, headers);
        assertEquals(expectedRow, cells);
    }

    private static boolean visibleColumn(String header, JsonNode display) {
        return switch (header) {
            case "Verein" -> display.get("showClub").asBoolean();
            case "Vbd" -> display.get("showAssociation").asBoolean();
            case "Nation" -> display.get("showNation").asBoolean();
            case "Schießen" -> display.get("showShooting").asBoolean();
            default -> true;
        };
    }

    /** Mirrors the viewer's LIVE class rule: every snapshot adopts the transmitted class. */
    private static Integer liveClassIndex(Integer previous, JsonNode message) {
        JsonNode finish = message.get("state").get("currentFinish");
        return finish.isNull() ? previous : finish.get("classIndex").asInt();
    }

    private static PublishedState published(long revision, int reportingClass) {
        List<CompetitionClass> classes = List.of(
                new CompetitionClass(0, "U13 m", 0, new ClassSnapshot(revision,
                        List.of("Rang", "StNr"), List.of(List.of("1", "101")))),
                new CompetitionClass(1, "U13 w", 0, new ClassSnapshot(revision,
                        List.of("Rang", "StNr"), List.of(List.of("1", "201")))));
        var competition = new Competition("Standardwettkampf", 1, 2, 0, 0, classes);
        var state = new CanonicalState(SourceHealth.CONNECTED, "12:00:00", competition,
                new CurrentFinish(reportingClass, 0, revision), null);
        return new PublishedState(revision, "stream", revision, state, PresentationConfig.defaults());
    }

    private static CanonicalState biathlonState() {
        var table = new ClassSnapshot(1, BIATHLON_HEADERS, List.of(BIATHLON_ROW));
        var competition = new Competition("Biathlon", 1, 1, 0, 0,
                List.of(new CompetitionClass(0, "U15 m", 0, table)));
        return new CanonicalState(SourceHealth.CONNECTED, "12:00:00", competition,
                new CurrentFinish(0, 0, 1), null);
    }

    private static List<String> without(String header) {
        return BIATHLON_HEADERS.stream().filter(value -> !value.equals(header)).toList();
    }

    private static List<String> withoutCell(int index) {
        List<String> row = new ArrayList<>(BIATHLON_ROW);
        row.remove(index);
        return row;
    }

    private static String resource(String name) throws Exception {
        try (var input = WebViewerContractTest.class.getResourceAsStream(name)) {
            assertNotNull(input, "missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
