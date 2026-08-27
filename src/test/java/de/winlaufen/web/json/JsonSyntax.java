package de.winlaufen.web.json;

public final class JsonSyntax {
    private final String input;
    private int position;

    private JsonSyntax(String input) { this.input = input; }

    public static void parse(String input) {
        JsonSyntax parser = new JsonSyntax(input);
        parser.value();
        parser.space();
        if (parser.position != input.length()) parser.fail();
    }

    private void value() {
        space();
        if (position == input.length()) fail();
        switch (input.charAt(position)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> number();
        }
    }

    private void object() {
        position++;
        space();
        if (take('}')) return;
        do {
            space(); string(); space(); require(':'); value(); space();
        } while (take(','));
        require('}');
    }

    private void array() {
        position++;
        space();
        if (take(']')) return;
        do { value(); space(); } while (take(','));
        require(']');
    }

    private void string() {
        require('"');
        while (position < input.length()) {
            char c = input.charAt(position++);
            if (c == '"') return;
            if (c < 0x20) fail();
            if (c == '\\') {
                if (position == input.length()) fail();
                char escaped = input.charAt(position++);
                if (escaped == 'u') {
                    for (int i = 0; i < 4; i++) if (position == input.length() || Character.digit(input.charAt(position++), 16) < 0) fail();
                } else if ("\"\\/bfnrt".indexOf(escaped) < 0) fail();
            }
        }
        fail();
    }

    private void number() {
        if (take('-') && position == input.length()) fail();
        if (take('0')) {
            if (position < input.length() && Character.isDigit(input.charAt(position))) fail();
        } else {
            digits();
        }
        if (take('.')) digits();
        if (take('e') || take('E')) { take('+'); take('-'); digits(); }
    }

    private void digits() {
        int start = position;
        while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
        if (start == position) fail();
    }

    private void literal(String value) {
        if (!input.startsWith(value, position)) fail();
        position += value.length();
    }

    private void space() { while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++; }
    private boolean take(char expected) {
        if (position < input.length() && input.charAt(position) == expected) { position++; return true; }
        return false;
    }
    private void require(char expected) { if (!take(expected)) fail(); }
    private void fail() { throw new IllegalArgumentException("Invalid JSON at position " + position); }
}
