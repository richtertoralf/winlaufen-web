package de.winlaufen.web.contract;

/**
 * One start-list participant on the wire, carrying the values the source supplied.
 *
 * <p>The fields map to the known WinLaufen export columns: {@code bib} is {@code StNr},
 * {@code className} is {@code Klasse}, {@code startTime} is {@code Startzeit}, {@code lastName}
 * is {@code Name}, {@code firstName} is {@code Vorname}, {@code club} is {@code Verein},
 * {@code association} is {@code Verband}, {@code course} is {@code Strecke}, {@code birthYear}
 * is {@code Jahrgang}, {@code gender} is {@code Geschlecht} and {@code nation} is {@code Nation}.
 *
 * <p>Every value stays text, exactly as the bridge holds it. In particular {@code bib} is never a
 * number — {@code 0012}, {@code 12} and {@code A12} are three different start numbers — and
 * {@code startTime} stays a WinLaufen time of day without date or time zone. There is no
 * participant identifier: the source supplies none, so the contract carries none.
 */
public record StartListRow(String bib, String className, String startTime, String lastName,
                           String firstName, String club, String association, String course,
                           String birthYear, String gender, String nation) { }
