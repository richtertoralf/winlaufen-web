package de.winlaufen.web.model;

import java.util.List;

public record Competition(String type, int evaluationMode, int classCount,
                          int winSpringenPosition, int roundOrHeat,
                          List<CompetitionClass> classes) {
    public Competition { classes = List.copyOf(classes); }
}
