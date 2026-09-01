package com.mahghuuuls.homerecall.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wire format of the cast sync. The encode and decode halves live ten lines apart and
 * can still disagree — a field written and never read, or read in the wrong order, corrupts
 * every field after it, and the symptom appears as nonsense on a different machine entirely.
 */
class CastSyncMessageTest {

    @Test
    @DisplayName("a start survives the round trip with its caster id")
    void startRoundTrip() {
        CastSyncMessage sent = new CastSyncMessage(4711, true, 120, 0, false);
        CastSyncMessage received = roundTrip(sent);

        assertEquals(4711, received.casterId());
        assertTrue(received.casting());
        assertEquals(120, received.durationTicks());
        assertFalse(received.interrupted());
    }

    @Test
    @DisplayName("an interrupted end survives the round trip")
    void interruptedEndRoundTrip() {
        CastSyncMessage received = roundTrip(new CastSyncMessage(-8, false, 0, 0, true));

        assertEquals(-8, received.casterId());
        assertFalse(received.casting());
        assertEquals(0, received.durationTicks());
        assertTrue(received.interrupted());
    }

    @Test
    @DisplayName("the encoder leaves nothing unread behind it")
    void nothingLeftInTheBuffer() {
        // A trailing unread byte means the two halves disagree about the format; the next
        // message in the pipeline would read it as its own first field.
        ByteBuf buf = Unpooled.buffer();
        new CastSyncMessage(1, true, 120, 45, false).toBytes(buf);
        new CastSyncMessage().fromBytes(buf);
        assertEquals(0, buf.readableBytes(), "decode did not consume exactly what encode wrote");
    }

    private static CastSyncMessage roundTrip(CastSyncMessage sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        CastSyncMessage received = new CastSyncMessage();
        received.fromBytes(buf);
        return received;
    }
}
