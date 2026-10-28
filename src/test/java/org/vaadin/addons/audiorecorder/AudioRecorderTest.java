package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentUtil;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AudioRecorderTest {
    private RecordingFinishedEvent audio(AudioRecorder recorder, String mime) {
        return new RecordingFinishedEvent(recorder, true, "AQID", mime, 2, 3);
    }

    @Test
    void initializesAndConfiguresBothVisualModes() {
        var recorder = new AudioRecorder();
        assertEquals("audio-recorder-element", recorder.getElement().getTag());
        for (var mode : AudioRecorder.VisualMode.values()) {
            recorder.setVisualMode(mode);
            assertEquals(mode.getValue(), recorder.getElement().getProperty("visualMode"));
        }
        recorder.setCanvasSize(500, 150);
        recorder.setMaxDuration(120);
        recorder.setShowControls(false);
        assertEquals(500, recorder.getElement().getProperty("canvasWidth", 0));
        assertEquals(150, recorder.getElement().getProperty("canvasHeight", 0));
        assertEquals(120, recorder.getElement().getProperty("maxDuration", 0));
        assertFalse(recorder.getElement().getProperty("showControls", true));
        recorder.setMaxDuration(0);
        assertEquals(0, recorder.getElement().getProperty("maxDuration", -1));
        assertThrows(IllegalArgumentException.class, () -> recorder.setMaxDuration(-1));
        assertThrows(IllegalArgumentException.class, () -> recorder.setCanvasSize(0, 100));
    }

    @Test
    void appliesPresetsAndCustomColors() {
        var recorder = new AudioRecorder();
        recorder.applyDarkTheme();
        assertEquals("#0d1117", recorder.getElement().getProperty("bgColor"));
        recorder.applyLightTheme();
        assertEquals("#1a73e8", recorder.getElement().getProperty("strokeColor"));
        recorder.applySunsetTheme();
        assertEquals("#f5a623", recorder.getElement().getProperty("gainColor"));
        recorder.setStrokeColor("red");
        recorder.setBackgroundColor("black");
        recorder.setGainColor("green");
        assertEquals("red", recorder.getElement().getProperty("strokeColor"));
        assertEquals("black", recorder.getElement().getProperty("bgColor"));
        assertEquals("green", recorder.getElement().getProperty("gainColor"));
    }

    @Test
    void deliversLifecyclePayloadsAndUnregistersListeners() {
        var recorder = new AudioRecorder();
        var starts = new AtomicInteger();
        var stopped = new AtomicInteger();
        var error = new AtomicReference<String>();
        var finished = new AtomicReference<RecordingFinishedEvent>();
        var registration = recorder.addRecordingStartedListener(e -> {
            assertTrue(e.isFromClient());
            starts.incrementAndGet();
        });
        recorder.addRecordingStoppedListener(e -> stopped.set(e.getDurationSeconds()));
        recorder.addRecordingErrorListener(e -> error.set(e.getMessage()));
        recorder.addRecordingFinishedListener(finished::set);
        ComponentUtil.fireEvent(recorder, new RecordingStartedEvent(recorder, true));
        registration.remove();
        ComponentUtil.fireEvent(recorder, new RecordingStartedEvent(recorder, true));
        ComponentUtil.fireEvent(recorder, new RecordingStoppedEvent(recorder, true, 7));
        ComponentUtil.fireEvent(recorder, new RecordingErrorEvent(recorder, true, "Permission denied"));
        var event = audio(recorder, "audio/webm");
        ComponentUtil.fireEvent(recorder, event);
        assertEquals(1, starts.get());
        assertEquals(7, stopped.get());
        assertEquals("Permission denied", error.get());
        assertSame(event, finished.get());
        assertEquals("AQID", event.getBase64());
        assertEquals("audio/webm", event.getMimeType());
        assertEquals(2, event.getDurationSeconds());
        assertEquals(3, event.getSizeBytes());
        assertArrayEquals(new byte[]{1, 2, 3}, event.getAudioBytes());
        assertSame(event.getAudioBytes(), event.getAudioBytes());
    }

    @Test
    void handlerReceivesBytesAndPublishesResult() {
        var received = new AtomicReference<AudioRecorder.AudioData>();
        var result = new AtomicReference<String>();
        var recorder = new AudioRecorder(data -> { received.set(data); return "simulated"; });
        recorder.addHandlerResultListener(e -> {
            assertFalse(e.isFromClient());
            result.set(e.getResult());
        });
        ComponentUtil.fireEvent(recorder, audio(recorder, "Audio/WebM; codecs=opus"));
        assertEquals("simulated", result.get());
        assertArrayEquals(new byte[]{1, 2, 3}, received.get().bytes());
        assertEquals(3, received.get().sizeBytes());
        assertEquals(2, received.get().durationSeconds());
    }

    @Test
    void handlerFailuresAndInvalidAudioBecomeErrorEvents() {
        var calls = new AtomicInteger();
        var error = new AtomicReference<String>();
        var recorder = new AudioRecorder(data -> { calls.incrementAndGet(); throw new IllegalStateException("service failed"); });
        recorder.addRecordingErrorListener(e -> error.set(e.getMessage()));
        ComponentUtil.fireEvent(recorder, audio(recorder, "audio/webm"));
        assertEquals("service failed", error.get());
        ComponentUtil.fireEvent(recorder, audio(recorder, "text/html"));
        assertTrue(error.get().contains("Unsupported"));
        ComponentUtil.fireEvent(recorder, new RecordingFinishedEvent(recorder, true, "%%%", "audio/webm", 0, 3));
        assertTrue(error.get().contains("Base64"));
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsForgedSizesInvalidMetadataAndOversizedEncodedContent() {
        var recorder = new AudioRecorder();
        for (double size : new double[]{-1, 0, 2, 4, 3.5, Double.NaN, Double.POSITIVE_INFINITY, 52428801}) {
            var event = new RecordingFinishedEvent(recorder, true, "AQID", "audio/webm", 0, size);
            assertThrows(IllegalStateException.class, event::getAudioBytes);
        }
        assertThrows(IllegalStateException.class, () ->
                new RecordingFinishedEvent(recorder, true, null, "audio/webm", 0, 0).getAudioBytes());
        assertThrows(IllegalStateException.class, () ->
                new RecordingFinishedEvent(recorder, true, "AQID", "audio/webm", -1, 3).getAudioBytes());
        String oversized = "A".repeat(4 * ((50 * 1024 * 1024 + 2) / 3) + 4);
        assertThrows(IllegalStateException.class, () ->
                new RecordingFinishedEvent(recorder, true, oversized, "audio/webm", 0, 0).getAudioBytes());
    }

    @Test
    void publicConstructorsAndProgrammaticControlsRemainAvailable() {
        var recorder = new AudioRecorder(null);
        assertDoesNotThrow(() -> {
            recorder.startRecording();
            recorder.pauseRecording();
            recorder.resumeRecording();
            recorder.stopRecording();
        });
        ComponentUtil.fireEvent(recorder, audio(recorder, "audio/webm"));
        var data = new AudioRecorder.AudioData(new byte[]{42}, "audio/ogg", 5, 1);
        assertEquals("Kg==", Base64.getEncoder().encodeToString(data.bytes()));
        assertEquals("audio/ogg", data.mimeType());
    }
}
