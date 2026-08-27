package de.winlaufen.web.web;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RendererContractTest {
    private static final List<String> HEADERS = List.of("Rang", "StNr", "Name, Vorname", "Verein", "Vbd", "Nation", "Schießen", "Gesamtzeit", "Rückstand");
    private static final List<String> ROW = List.of("1", "101", "Name", "Club", "Vbd-Wert", "DE", "1 0 2 0 ", "3:30:35.1", "0:00:00.0");

    @Test void exactPublicColumnOptionsKeepHeadersAndCellsIndexAligned() {
        assertProjection(options(true, true, true, true), HEADERS, ROW);
        assertProjection(options(false, true, true, true), without("Verein"), withoutCell(3));
        assertProjection(options(true, false, true, true), without("Vbd"), withoutCell(4));
        assertProjection(options(true, true, false, true), without("Nation"), withoutCell(5));
        assertProjection(options(true, true, true, false), without("Schießen"), withoutCell(6));
        assertProjection(options(false, false, false, false), List.of("Rang", "StNr", "Name, Vorname", "Gesamtzeit", "Rückstand"), List.of("1", "101", "Name", "3:30:35.1", "0:00:00.0"));
    }

    @Test void frontendKeepsOneMainViewSnapshotSyncCurrentFinishAndConditionalMessages() throws Exception {
        String html = resource("/web/renderer.html");
        String script = resource("/web/renderer.js");
        assertTrue(html.contains("data-view=\"startlist\""));
        assertTrue(html.contains("data-view=\"live\""));
        assertTrue(html.contains("data-view=\"results\""));
        assertEquals(1, occurrences(html, "view active"));
        assertTrue(script.contains("item.classList.toggle('active', item.id === button.dataset.view)"));
        assertTrue(script.contains("message.type === 'snapshot' || message.type === 'classSnapshot'"));
        assertTrue(script.contains("index === highlighted"));
        assertTrue(script.contains("display.showPublicMessages && Boolean(state.message)"));
        assertTrue(script.contains("publicMessage.hidden = !visible"));
        assertTrue(html.contains("id=\"public-message\"") && html.contains("hidden"));
    }

    @Test void tableHeadIsCreatedBeforeBodyAndSticksToItsOwnScrollContainerTop() throws Exception {
        String script = resource("/web/renderer.js");
        String css = resource("/web/app.css");
        assertTrue(script.indexOf("node.createTHead()") < script.indexOf("node.createTBody()"));
        assertTrue(css.contains(".table-wrap{width:100%;overflow:auto"));
        assertTrue(css.contains("th{position:sticky;top:0;"));
        assertFalse(css.contains("th{position:sticky;top:56px;"));
    }

    @Test void zeroBasedRoundOrHeatIsOneBasedOnlyInHumanReadableRenderer() throws Exception {
        assertEquals(1, displayRoundOrHeat(0));
        assertEquals(2, displayRoundOrHeat(1));
        String script = resource("/web/renderer.js");
        assertTrue(script.contains("function displayRoundOrHeat(rawRoundOrHeat) { return rawRoundOrHeat + 1; }"));
        assertTrue(script.contains("displayRoundOrHeat(state.competition.roundOrHeat)"));
    }

    private static Options options(boolean club, boolean association, boolean nation, boolean shooting) { return new Options(club, association, nation, shooting); }
    private static int displayRoundOrHeat(int rawRoundOrHeat) { return rawRoundOrHeat + 1; }
    private static void assertProjection(Options options, List<String> expectedHeaders, List<String> expectedRow) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < HEADERS.size(); index++) if (visible(HEADERS.get(index), options)) indices.add(index);
        assertEquals(expectedHeaders, indices.stream().map(HEADERS::get).toList());
        assertEquals(expectedRow, indices.stream().map(ROW::get).toList());
    }
    private static boolean visible(String header, Options options) {
        return switch (header) { case "Verein" -> options.club; case "Vbd" -> options.association; case "Nation" -> options.nation; case "Schießen" -> options.shooting; default -> true; };
    }
    private static List<String> without(String value) { return HEADERS.stream().filter(item -> !item.equals(value)).toList(); }
    private static List<String> withoutCell(int index) { List<String> result = new ArrayList<>(ROW); result.remove(index); return result; }
    private static String resource(String name) throws Exception { try (var input = RendererContractTest.class.getResourceAsStream(name)) { assertNotNull(input); return new String(input.readAllBytes(), StandardCharsets.UTF_8); } }
    private static int occurrences(String value, String needle) { return (value.length() - value.replace(needle, "").length()) / needle.length(); }
    private record Options(boolean club, boolean association, boolean nation, boolean shooting) { }
}
