package de.winlaufen.web.protocol;

public record ClockValue(String wireValue) {
    public static ClockValue parse(String value) {
        if (value == null || value.length() != 11 || !value.startsWith("Uhr")
                || value.charAt(5) != ':' || value.charAt(8) != ':') return null;
        for (int index : new int[]{3, 4, 6, 7, 9, 10}) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') return null;
        }
        return new ClockValue(value.substring(3));
    }
}
