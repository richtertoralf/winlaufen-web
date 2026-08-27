package de.winlaufen.web.bridge;

import de.winlaufen.web.bridge.source.winlaufen.ResultBlock;
import java.util.List;

public final class TestBlocks {
    private TestBlocks() { }
    public static ResultBlock block(int classIndex, int finish, List<List<String>> rows, List<String> headers) {
        return new ResultBlock("Standardwettkampf", 1, new String[]{"U13 m", "U13 w"},
                new int[]{0, 0}, 0, classIndex, 0, finish, rows, headers);
    }
}
