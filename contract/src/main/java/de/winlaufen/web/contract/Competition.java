package de.winlaufen.web.contract;

import java.util.List;

/** Competition metadata plus the currently known class snapshots. */
public record Competition(String type, int evaluationMode, int classCount, int winSpringenPosition,
                          int roundOrHeat, List<CompetitionClass> classes) {

    public Competition {
        classes = List.copyOf(classes);
    }
}
