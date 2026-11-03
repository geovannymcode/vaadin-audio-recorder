package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Vaadin Flow component for <strong>recording audio with real-time
 * waveform visualisation</strong>.
 * <p>
 * This add-on is <strong>AI-agnostic</strong>: it captures audio in the browser
 * and delivers it to the server as raw bytes. You decide what to do with them —
 * send to OpenAI Whisper, Google Gemini, Anthropic, Deepgram, AssemblyAI,
 * Azure Speech, your own model, or simply save to disk.
 * </p>
 *
 * <h3>Minimal usage (3 lines)</h3>
 * <pre>{@code
 * AudioRecorder recorder = new AudioRecorder();
 * recorder.addRecordingFinishedListener(e -> {
 *     byte[] audio = e.getAudioBytes();   // raw audio ready
 *     String transcript = myAiService.transcribe(audio, e.getMimeType());
 * });
 * add(recorder);
 * }</pre>
 *
 * <h3>With AudioHandler functional interface</h3>
 * <pre>{@code
 * // OpenAI example
 * AudioRecorder recorder = new AudioRecorder(audioData ->
 *     openAiClient.audio().transcriptions()
 *         .create(new TranscriptionRequest(audioData.bytes(), "whisper-1"))
 *         .text()
 * );
 *
 * // Google Gemini example
 * AudioRecorder recorder = new AudioRecorder(audioData ->
 *     geminiClient.transcribe(audioData.bytes())
 * );
 *
 * // Save to file — no AI at all
 * AudioRecorder recorder = new AudioRecorder(audioData -> {
 *     Files.write(Path.of("recording.webm"), audioData.bytes());
 *     return "Saved!";
 * });
 * }</pre>
 *
 * @author Geovanny Mendoza — JUGBAQ (Barranquilla Java User Group)
 */
@Tag("audio-recorder-element")
@JsModule("./audio-recorder-element.ts")
public class AudioRecorder extends Component implements HasSize, HasStyle {

    private static final Logger LOG = Logger.getLogger(AudioRecorder.class.getName());

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg",
            "audio/wav", "audio/x-wav", "audio/aac", "audio/flac",
            "audio/webm;codecs=opus", "audio/ogg;codecs=opus"
    );

    private AudioHandler audioHandler;

    // ─── Constructors ───────────────────────────────────────────────

    /**
     * Creates an AudioRecorder with no handler.
     * Use {@link #addRecordingFinishedListener} to handle recordings manually.
     */
    public AudioRecorder() {
    }

    /**
     * Creates an AudioRecorder that automatically calls the given handler
     * when a recording finishes. The handler receives an {@link AudioData}
     * record with the raw bytes and metadata.
     * <p>
     * The handler is AI-agnostic — you can connect OpenAI, Gemini,
     * Anthropic, Deepgram, or any custom backend.
     *
     * @param handler a function that processes the audio
     */
    public AudioRecorder(AudioHandler handler) {
        this.audioHandler = handler;
        addRecordingFinishedListener(event -> {
            if (this.audioHandler == null) return;

            String mime = event.getMimeType();
            if (!isAllowedMimeType(mime)) {
                LOG.warning("Rejected recording with unsupported MIME type: " + mime);
                fireHandlerError("Unsupported audio MIME type: " + mime);
                return;
            }

            AudioData data = new AudioData(
                    event.getAudioBytes(),
                    mime,
                    event.getDurationSeconds(),
                    event.getSizeBytes()
            );

            executeHandler(data);
        });
    }

    private void executeHandler(AudioData data) {
        try {
            String result = this.audioHandler.process(data);
            fireHandlerResult(result);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "AudioHandler failed (sync)", ex);
            fireHandlerError(ex.getMessage());
        }
    }

    // ─── AudioHandler functional interface ───────────────────────────

    /**
     * Functional interface for processing recorded audio.
     * Implement this with any AI provider, file storage, or custom logic.
     * <p>
     * Works with: OpenAI, Anthropic, Google Gemini, Deepgram, AssemblyAI,
     * Azure Speech, Vosk, local Whisper, or any custom backend.
     */
    @FunctionalInterface
    public interface AudioHandler {
        /**
         * Process the recorded audio and return a result string.
         *
         * @param audioData the captured audio with metadata
         * @return a result string (e.g. transcription text, file path, status)
         */
        String process(AudioData audioData);
    }

    /**
     * Immutable record holding the captured audio and its metadata.
     *
     * @param bytes          raw audio bytes (decoded from Base64)
     * @param mimeType       the MIME type (e.g. "audio/webm", "audio/ogg")
     * @param durationSeconds recording duration in whole seconds
     * @param sizeBytes      total size in bytes
     */
    public record AudioData(
            byte[] bytes,
            String mimeType,
            int durationSeconds,
            long sizeBytes
    ) {}

    // ─── Handler result event ───────────────────────────────────────

    private void fireHandlerResult(String result) {
        getEventBus().fireEvent(new AudioHandlerResultEvent(this, false, result));
    }

    private void fireHandlerError(String message) {
        getEventBus().fireEvent(new RecordingErrorEvent(this, false, message));
    }

    /**
     * Returns {@code true} if the given MIME type is in the allowed whitelist.
     * The check normalises by trimming and lowercasing.
     */
    private static boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return false;
        return ALLOWED_MIME_TYPES.contains(mimeType.trim().toLowerCase());
    }

    /**
     * Adds a listener called after the {@link AudioHandler} finishes processing.
     * The event contains the result string returned by the handler.
     */
    public Registration addHandlerResultListener(
            ComponentEventListener<AudioHandlerResultEvent> listener) {
        return addListener(AudioHandlerResultEvent.class, listener);
    }

    // ─── Programmatic control (calls JS methods) ────────────────────

    /** Programmatically start recording from Java. */
    public void startRecording() {
        getElement().callJsFunction("startRecording");
    }

    /** Programmatically stop recording from Java. */
    public void stopRecording() {
        getElement().callJsFunction("stopRecording");
    }

    /** Programmatically pause recording from Java. */
    public void pauseRecording() {
        getElement().callJsFunction("pauseRecording");
    }

    /** Programmatically resume recording from Java. */
    public void resumeRecording() {
        getElement().callJsFunction("resumeRecording");
    }

    // ─── Visual configuration ───────────────────────────────────────

    /** Visualization modes. */
    public enum VisualMode {
        BARS("bars"), WAVEFORM("waveform");
        private final String value;
        VisualMode(String v) { this.value = v; }
        public String getValue() { return value; }
    }

    public void setVisualMode(VisualMode mode) {
        getElement().setProperty("visualMode", mode.getValue());
    }

    public void setCanvasSize(int width, int height) {
        getElement().setProperty("canvasWidth", width);
        getElement().setProperty("canvasHeight", height);
    }

    public void setStrokeColor(String css) {
        getElement().setProperty("strokeColor", css);
    }

    public void setBackgroundColor(String css) {
        getElement().setProperty("bgColor", css);
    }

    public void setGainColor(String css) {
        getElement().setProperty("gainColor", css);
    }

    /**
     * Auto-stop after {@code seconds}. Use 0 for unlimited.
     */
    public void setMaxDuration(int seconds) {
        getElement().setProperty("maxDuration", seconds);
    }

    /**
     * Show or hide the built-in controls (REC, Pause, Stop, Play buttons).
     * When hidden, use the programmatic methods
     * {@link #startRecording()}, {@link #stopRecording()}, etc.
     */
    public void setShowControls(boolean show) {
        getElement().setProperty("showControls", show);
    }

    // ─── Theme presets ──────────────────────────────────────────────

    public void applyDarkTheme() {
        setBackgroundColor("#0d1117");
        setStrokeColor("#58a6ff");
        setGainColor("#3fb950");
    }

    public void applyLightTheme() {
        setBackgroundColor("#f6f8fa");
        setStrokeColor("#1a73e8");
        setGainColor("#34a853");
    }

    public void applySunsetTheme() {
        setBackgroundColor("#1a1a2e");
        setStrokeColor("#e94560");
        setGainColor("#f5a623");
    }

    // ─── Event registrations ────────────────────────────────────────

    public Registration addRecordingFinishedListener(
            ComponentEventListener<RecordingFinishedEvent> listener) {
        return addListener(RecordingFinishedEvent.class, listener);
    }

    public Registration addRecordingStartedListener(
            ComponentEventListener<RecordingStartedEvent> listener) {
        return addListener(RecordingStartedEvent.class, listener);
    }

    public Registration addRecordingStoppedListener(
            ComponentEventListener<RecordingStoppedEvent> listener) {
        return addListener(RecordingStoppedEvent.class, listener);
    }

    public Registration addRecordingErrorListener(
            ComponentEventListener<RecordingErrorEvent> listener) {
        return addListener(RecordingErrorEvent.class, listener);
    }

}
