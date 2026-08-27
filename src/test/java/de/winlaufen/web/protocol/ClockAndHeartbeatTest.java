package de.winlaufen.web.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClockAndHeartbeatTest {
    @Test void recognizesClockTelegramWithoutInterpretingItsValue() {
        assertNull(ClockValue.parse("12:00:00"));
        assertNull(ClockValue.parse("Uhr1:00:00"));
        assertNull(ClockValue.parse("UhrAA:00:00"));
        assertNull(ClockValue.parse("Uhr01:0:00"));
        assertNull(ClockValue.parse("Uhr01:00:0"));
        assertNull(ClockValue.parse("nichtUhr01:00:00"));
        assertEquals("01:23:45", ClockValue.parse("Uhr01:23:45").wireValue());
        assertEquals("99:99:99", ClockValue.parse("Uhr99:99:99").wireValue());
    }

    @Test void everyClockTelegramRefreshesHeartbeatRegardlessOfValue() {
        Heartbeat heartbeat = new Heartbeat(10);
        heartbeat.accept(3_000_000_010L);
        heartbeat.accept(4_000_000_010L);
        assertFalse(heartbeat.isStale(8_000_000_010L));
        assertTrue(heartbeat.isStale(8_000_000_011L));
    }

    @Test void connectionWithoutAnyClockTelegramBecomesStale() {
        Heartbeat heartbeat = new Heartbeat(10);
        assertFalse(heartbeat.isStale(4_000_000_010L));
        assertTrue(heartbeat.isStale(4_000_000_011L));
    }
}
