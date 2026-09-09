package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.contract.StartListRow;
import de.winlaufen.web.liveserver.state.PublishedStartList;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Product semantics of the start-list view.
 *
 * <p>The project deliberately has no JavaScript runtime, so the rules the surface has to keep are
 * pinned by source assertions. What can be checked in Java — the message the browser actually
 * receives — is asserted against the real encoder.
 */
class StartListViewerContractTest {

    // --- The message a browser receives --------------------------------------------------------

    @Test
    void theBrowserMessageCarriesEveryFieldTheViewCanShow() throws Exception {
        PublishedStartList published = new PublishedStartList(3, "stream-a", 2, "IMPORT_XLSX",
                "Startliste.xlsx", List.of(new StartListRow("0012", "Schüler U12 m", "10:00:15",
                        "VOBORNÍKOVÁ", "Tereza", "SKP Jablonec", "Český svaz", "0.8", "2000", "w",
                        "CZE")));

        String json = PublicJson.startList(published);

        assertTrue(json.contains("\"type\":\"startlist\""));
        assertTrue(json.contains("\"publicationRevision\":3"));
        assertTrue(json.contains("\"generation\":2"));
        assertTrue(json.contains("\"source\":\"IMPORT_XLSX\""));
        assertTrue(json.contains("\"sourceLabel\":\"Startliste.xlsx\""));
        for (String field : new String[] {"\"bib\":\"0012\"", "\"className\":\"Schüler U12 m\"",
                "\"startTime\":\"10:00:15\"", "\"lastName\":\"VOBORNÍKOVÁ\"",
                "\"firstName\":\"Tereza\"", "\"club\":\"SKP Jablonec\"",
                "\"association\":\"Český svaz\"", "\"course\":\"0.8\"", "\"birthYear\":\"2000\"",
                "\"gender\":\"w\"", "\"nation\":\"CZE\""}) {
            assertTrue(json.contains(field), "missing " + field + " in " + json);
        }
    }

    @Test
    void anAbsentStartListIsExpressibleForTheBrowserToo() {
        String json = PublicJson.startList(PublishedStartList.empty());

        assertTrue(json.contains("\"generation\":0"));
        assertTrue(json.contains("\"entries\":[]"));
    }

    @Test
    void valuesThatLookLikeMarkupOrJsonStayData() {
        PublishedStartList published = new PublishedStartList(1, "s", 1, "IMPORT_CSV", "x.csv",
                List.of(new StartListRow("1", "<script>", "", "\"quote\"\\", "Zeile\nUmbruch", "",
                        "", "", "", "", "")));

        String json = PublicJson.startList(published);

        assertTrue(json.contains("\\\"quote\\\"\\\\"), "quotes and backslashes are escaped");
        assertTrue(json.contains("Zeile\\nUmbruch"), "a line break never breaks the message");
        assertFalse(json.contains("Zeile\nUmbruch"));
    }

    @Test
    void theStartListIsNotPartOfTheStateMessage() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertFalse(PublicJson.state(de.winlaufen.web.liveserver.state.PublishedState.empty())
                .contains("startlist"), "the frequent state message never carries participants");
        assertTrue(script.contains("if (message.type === 'startlist')"),
                "the viewer routes the start list as its own message");
    }

    // --- The surface ---------------------------------------------------------------------------

    @Test
    void theStartListViewReplacedThePlaceholder() throws Exception {
        String html = resource("/web-viewer/viewer.html");

        assertTrue(html.contains("id=\"startlist-table\""), "the view has a real table area");
        assertTrue(html.contains("id=\"startlist-class\""), "a class can be picked directly");
        assertFalse(html.contains("Noch keine Startlistendaten verfügbar."),
                "the placeholder of the version without start lists is gone");
    }

    @Test
    void classesArePagedWithPreviousAndNextAndAPositionCounter() throws Exception {
        String html = resource("/web-viewer/viewer.html");
        String script = resource("/web-viewer/viewer.js");

        assertTrue(html.contains("id=\"startlist-previous\""));
        assertTrue(html.contains("id=\"startlist-next\""));
        assertTrue(html.contains("aria-label=\"Vorherige Klasse\""));
        assertTrue(html.contains("aria-label=\"Nächste Klasse\""));
        assertTrue(script.contains("`Klasse ${startListClassIndex + 1} von ${total}`"),
                "the view says which class of how many is shown");
    }

    @Test
    void navigationStopsAtTheEndsInsteadOfWrappingAround() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains("if (next < 0 || next >= startListClasses.length) return;"),
                "stepping past an end does nothing");
        assertTrue(script.contains("startListPrevious.disabled = startListClassIndex === 0;"));
        assertTrue(script.contains("startListNext.disabled = startListClassIndex === total - 1;"));
    }

    /**
     * WinLaufen and the organiser already decided the order. Re-sorting it in the browser would
     * silently present a different start list than the one that was imported.
     */
    @Test
    void neitherClassesNorParticipantsAreReordered() throws Exception {
        String script = resource("/web-viewer/viewer.js");
        String startListPart = script.substring(script.indexOf("--- Startliste ---"));

        assertFalse(startListPart.contains(".sort("),
                "the start list view never sorts; the import order is the order");
        assertTrue(script.contains("/** Klassen in der Reihenfolge ihres ersten Auftretens, Eintraege in Dateireihenfolge. */"),
                "the grouping documents that it preserves the import order");
        assertTrue(script.contains("item.entries.push(entry)"),
                "participants are appended in the order they arrive");
    }

    @Test
    void theViewShowsStartTimeBibNameAndClub() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains("header: 'Startzeit'"));
        assertTrue(script.contains("header: 'StNr'"));
        assertTrue(script.contains("header: 'Name'"));
        assertTrue(script.contains("header: 'Verein'"));
        assertTrue(script.contains("[entry.firstName, entry.lastName].filter(Boolean).join(' ')"),
                "a missing name part must not leave a stray separator");
    }

    @Test
    void optionalColumnsFollowTheExistingPresentationSettings() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains("display?.showClub !== false"));
        assertTrue(script.contains("display?.showAssociation !== false"));
        assertTrue(script.contains("display?.showNation === true"),
                "nation stays hidden unless the organiser switched it on, as in the result tables");
        assertFalse(script.contains("showStartListClub"),
                "no second configuration family just for the start list");
    }

    @Test
    void aColumnWithoutAnyValueIsNotShownAtAll() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains("startListColumns = STARTLIST_FIELDS.filter(field =>"),
                "columns are chosen from the data, so a missing optional field leaves no gap");
    }

    @Test
    void anEmptyStartListSaysSoInsteadOfShowingAnEmptyTable() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains("'Keine Startliste verfügbar.'"));
        assertTrue(script.contains("startListNav.hidden = total === 0;"),
                "without classes there is nothing to page through");
    }

    @Test
    void participantDataIsWrittenAsTextAndNeverAsMarkup() throws Exception {
        String script = resource("/web-viewer/viewer.js");
        String startListPart = script.substring(script.indexOf("--- Startliste ---"));

        assertTrue(startListPart.contains("row.insertCell().textContent = column.value(entry)"));
        // The property access, not the word: a comment may well name what is deliberately avoided.
        assertFalse(startListPart.contains(".innerHTML"),
                "participant values come from a foreign file and are never parsed as HTML");
        assertTrue(startListPart.contains("term.textContent") || startListPart.contains("node.textContent"),
                "even the empty-state text is written as text");
    }

    @Test
    void aNewStartListRefreshesTheViewWithoutTouchingTheResultTables() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains("if (message.type === 'startlist') { receiveStartList(message); return; }"),
                "a start list never runs the result rendering");
        assertTrue(script.contains("startListPublicationRevision = -1;"),
                "a reconnect accepts the start list of the new live-server run");
        assertTrue(script.contains("if (message.publicationRevision < startListPublicationRevision) return;"),
                "a browser never steps back to an older start list");
    }

    private static String resource(String name) throws Exception {
        try (var input = StartListViewerContractTest.class.getResourceAsStream(name)) {
            assertNotNull(input, "missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n");
        }
    }

    @Test
    void theStartListIsRedrawnOnlyWhenTheDisplaySettingsReallyChanged() throws Exception {
        String script = resource("/web-viewer/viewer.js");

        assertTrue(script.contains(
                        "if (JSON.stringify(display) !== previousDisplay && startListClasses.length) renderStartList();"),
                "a clock telegram must not redraw a table with thousands of rows every second");
        assertEquals(1, occurrences(script, "renderStartList();\nstart();"),
                "the view has a defined state before the first message arrives");
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
