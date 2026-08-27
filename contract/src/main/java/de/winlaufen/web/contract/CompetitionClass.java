package de.winlaufen.web.contract;

/** One competition class. {@code index} is the zero-based position in the class array. */
public record CompetitionClass(int index, String name, int roundsOrTeamSize, ClassSnapshot snapshot) { }
