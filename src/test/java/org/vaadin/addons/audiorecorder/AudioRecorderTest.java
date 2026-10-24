package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AudioRecorderTest {

    private static final byte[] AUDIO = "fake-audio".getBytes(StandardCharsets.UTF_8);

    private static void finish(AudioRecorder recorder, String mimeType) {
        ComponentUtil.fireEvent(recorder, new RecordingFinishedEvent(recorder, true,
                Base64.getEncoder().encodeToString(AUDIO), mimeType, 2, AUDIO.length));
    }

    // ─── AudioHandler ───────────────────────────────────────────────

    @Test
    void handlerReceivesAudioDataAndResultIsFired() {
        AtomicReference<AudioRecorder.AudioData> received = new AtomicReference<>();
        AudioRecorder recorder = new AudioRecorder(data -> {
            received.set(data);
            return "transcript";
        });
        List<String> results = new ArrayList<>();
        recorder.addHandlerResultListener(e -> results.add(e.getResult()));

        finish(recorder, "audio/webm");

        assertArrayEquals(AUDIO, received.get().bytes());
        assertEquals("audio/webm", received.get().mimeType());
        assertEquals(2, received.get().durationSeconds());
        assertEquals(AUDIO.length, received.get().sizeBytes());
        assertEquals(List.of("transcript"), results);
    }

    @Test
    void mimeTypeCheckIgnoresCaseAndWhitespace() {
        List<String> results = new ArrayList<>();
        AudioRecorder recorder = new AudioRecorder(data -> "ok");
        recorder.addHandlerResultListener(e -> results.add(e.getResult()));

        finish(recorder, "  Audio/WebM;codecs=opus ");

        assertEquals(List.of("ok"), results);
    }

    @Test
    void unsupportedMimeTypeFiresErrorWithoutCallingHandler() {
        AudioRecorder recorder = new AudioRecorder(data -> fail("handler must not be called"));
        List<String> errors = new ArrayList<>();
        recorder.addRecordingErrorListener(e -> errors.add(e.getMessage()));

        finish(recorder, "application/x-msdownload");

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("application/x-msdownload"));
    }

    @Test
    void missingMimeTypeFiresError() {
        AudioRecorder recorder = new AudioRecorder(data -> fail("handler must not be called"));
        List<String> errors = new ArrayList<>();
        recorder.addRecordingErrorListener(e -> errors.add(e.getMessage()));

        finish(recorder, null);

        assertEquals(1, errors.size());
    }

    @Test
    void handlerExceptionIsReportedAsError() {
        AudioRecorder recorder = new AudioRecorder(data -> {
            throw new IllegalStateException("API down");
        });
        List<String> errors = new ArrayList<>();
        recorder.addRecordingErrorListener(e -> errors.add(e.getMessage()));

        finish(recorder, "audio/ogg");

        assertEquals(List.of("API down"), errors);
    }

    @Test
    void recorderWithoutHandlerStillNotifiesListeners() {
        AudioRecorder recorder = new AudioRecorder();
        List<byte[]> received = new ArrayList<>();
        recorder.addRecordingFinishedListener(e -> received.add(e.getAudioBytes()));

        finish(recorder, "audio/webm");

        assertEquals(1, received.size());
        assertArrayEquals(AUDIO, received.get(0));
    }

    // ─── Configuration → element properties ─────────────────────────

    @Test
    void settersWriteElementProperties() {
        AudioRecorder recorder = new AudioRecorder();
        recorder.setVisualMode(AudioRecorder.VisualMode.WAVEFORM);
        recorder.setCanvasSize(500, 120);
        recorder.setMaxDuration(30);
        recorder.setShowControls(false);

        var el = recorder.getElement();
        assertEquals("waveform", el.getProperty("visualMode"));
        assertEquals(500, el.getProperty("canvasWidth", 0));
        assertEquals(120, el.getProperty("canvasHeight", 0));
        assertEquals(30, el.getProperty("maxDuration", 0));
        assertFalse(el.getProperty("showControls", true));
    }

    @Test
    void darkThemeSetsAllColors() {
        AudioRecorder recorder = new AudioRecorder();
        recorder.applyDarkTheme();

        var el = recorder.getElement();
        assertEquals("#0d1117", el.getProperty("bgColor"));
        assertEquals("#58a6ff", el.getProperty("strokeColor"));
        assertEquals("#3fb950", el.getProperty("gainColor"));
    }
}
