package org.vaadin.addons.audiorecorder;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RecordingFinishedEventTest {

    private static final byte[] AUDIO = "fake-audio".getBytes(StandardCharsets.UTF_8);
    private static final String BASE64 = Base64.getEncoder().encodeToString(AUDIO);

    private static RecordingFinishedEvent event(String base64, double sizeBytes) {
        return new RecordingFinishedEvent(new AudioRecorder(), true,
                base64, "audio/webm", 3, sizeBytes);
    }

    @Test
    void decodesBase64ToRawBytes() {
        assertArrayEquals(AUDIO, event(BASE64, AUDIO.length).getAudioBytes());
    }

    @Test
    void cachesDecodedBytes() {
        RecordingFinishedEvent e = event(BASE64, AUDIO.length);
        assertSame(e.getAudioBytes(), e.getAudioBytes());
    }

    @Test
    void exposesMetadata() {
        RecordingFinishedEvent e = event(BASE64, AUDIO.length);
        assertEquals(BASE64, e.getBase64());
        assertEquals("audio/webm", e.getMimeType());
        assertEquals(3, e.getDurationSeconds());
        assertEquals(AUDIO.length, e.getSizeBytes());
    }

    @Test
    void rejectsPayloadOverFiftyMegabytes() {
        RecordingFinishedEvent e = event(BASE64, 50L * 1024 * 1024 + 1);
        assertThrows(IllegalStateException.class, e::getAudioBytes);
    }

    @Test
    void acceptsPayloadAtExactLimit() {
        RecordingFinishedEvent e = event(BASE64, 50L * 1024 * 1024);
        assertArrayEquals(AUDIO, e.getAudioBytes());
    }
}
