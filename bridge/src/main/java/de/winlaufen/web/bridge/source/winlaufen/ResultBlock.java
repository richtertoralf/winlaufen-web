package de.winlaufen.web.bridge.source.winlaufen;

import java.util.List;

public record ResultBlock(String competitionType, int evaluationMode, String[] classNames,
                          int[] roundsOrTeamSize, int winSpringenPosition, int classIndex,
                          int roundOrHeat, int currentFinishIndex,
                          List<List<String>> rows, List<String> headers) {
    public ResultBlock {
        classNames = classNames.clone();
        roundsOrTeamSize = roundsOrTeamSize.clone();
        rows = rows.stream().map(List::copyOf).toList();
        headers = List.copyOf(headers);
    }
}
