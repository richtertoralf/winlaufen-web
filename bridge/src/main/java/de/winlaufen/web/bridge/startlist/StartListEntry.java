package de.winlaufen.web.bridge.startlist;

import java.util.regex.Pattern;

/**
 * One participant entry of a start list, carrying the values the source delivered.
 *
 * <p>The field names map to the known WinLaufen export columns: {@code bib} is {@code StNr},
 * {@code className} is {@code Klasse}, {@code startTime} is {@code Startzeit}, {@code lastName}
 * is {@code Name}, {@code firstName} is {@code Vorname}, {@code club} is {@code Verein},
 * {@code association} is {@code Verband}, {@code course} is {@code Strecke}, {@code birthYear}
 * is {@code Jahrgang}, {@code gender} is {@code Geschlecht} and {@code nation} is {@code Nation}.
 *
 * <p>Deliberate limits of this record:
 *
 * <ul>
 *   <li>There is no participant identifier. The source supplies none, so none is invented — not
 *       from the row position, not as a generated id, not as a hash of the attributes.</li>
 *   <li>{@code bib} stays text. {@code 0012}, {@code 12} and {@code A12} are three different
 *       start numbers and must all survive unchanged. Only surrounding whitespace is removed.</li>
 *   <li>{@code startTime} is a WinLaufen <em>time of day</em>, kept as the delivered text. It
 *       carries no date, no time zone and no UTC instant, and this record must not turn it into
 *       one. Only its syntax is checked.</li>
 * </ul>
 *
 * <p>Optional values are the empty string when the source did not supply them; no field is null.
 */
public record StartListEntry(String bib, String className, String startTime, String lastName,
                             String firstName, String club, String association, String course,
                             String birthYear, String gender, String nation) {

    /** Longest accepted value of a single field. A defensive bound, not a competition rule. */
    public static final int MAX_VALUE_CHARS = 1_024;

    /**
     * {@code H:MM}, {@code HH:MM}, {@code HH:MM:SS} and {@code HH:MM:SS.s}, with a comma also
     * accepted as the decimal separator. The observed WinLaufen exports use whole seconds.
     */
    private static final Pattern START_TIME =
            Pattern.compile("(\\d{1,2}):([0-5]\\d)(?::([0-5]\\d)(?:[.,](\\d{1,6}))?)?");

    public StartListEntry {
        bib = required(bib, "StNr");
        className = required(className, "Klasse");
        startTime = optional(startTime, "Startzeit");
        lastName = optional(lastName, "Name");
        firstName = optional(firstName, "Vorname");
        club = optional(club, "Verein");
        association = optional(association, "Verband");
        course = optional(course, "Strecke");
        birthYear = optional(birthYear, "Jahrgang");
        gender = optional(gender, "Geschlecht");
        nation = optional(nation, "Nation");
        if (!startTime.isEmpty() && !isValidStartTime(startTime)) {
            throw new StartListFormatException("Ungültige Startzeit: " + startTime);
        }
    }

    /**
     * Whether a value is a syntactically valid WinLaufen time of day. The hour is bounded at 23
     * so that a mistyped value cannot silently become a second day.
     */
    public static boolean isValidStartTime(String value) {
        if (value == null || !START_TIME.matcher(value).matches()) {
            return false;
        }
        return Integer.parseInt(value.substring(0, value.indexOf(':'))) <= 23;
    }

    /** True when this entry carries a start time. */
    public boolean hasStartTime() {
        return !startTime.isEmpty();
    }

    private static String required(String value, String column) {
        String text = optional(value, column);
        if (text.isEmpty()) {
            throw new StartListFormatException(column + " fehlt");
        }
        return text;
    }

    private static String optional(String value, String column) {
        String text = value == null ? "" : value.strip();
        if (text.length() > MAX_VALUE_CHARS) {
            throw new StartListFormatException(column + " überschreitet " + MAX_VALUE_CHARS
                    + " Zeichen");
        }
        return text;
    }
}
