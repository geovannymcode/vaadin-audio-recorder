package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;

/**
 * Demo view — run with {@code mvn jetty:run} → http://localhost:8080
 */
@Route("")
public class TestView extends VerticalLayout {

    public TestView() {
        setPadding(true);
        setSpacing(true);
        setMaxWidth("720px");
        getStyle().set("margin", "0 auto");

        add(new H2("🎙️ Audio Recorder for Vaadin — Demo"));
        add(new Paragraph(
            "AI-agnostic audio recorder add-on. Add the dependency, write a few "
            + "lines of Java, and it works with any AI provider or none at all."));

        // ━━━ EXAMPLE 1: Record voice → transcribe → text ━━━━━━━━━━━━━━
        add(createSection("Example 1 — Record & Transcribe",
            "Record your voice with the microphone. When you stop, the audio is sent "
            + "to the handler (simulated here — replace with OpenAI Whisper, Gemini, etc.) "
            + "and the transcription appears in the text area below."));

        TextArea transcription1 = new TextArea("Transcription");
        transcription1.setWidthFull();
        transcription1.setReadOnly(true);
        transcription1.setPlaceholder("Record something and the transcription will appear here...");

        Span status1 = statusLabel();

        AudioRecorder recorder1 = new AudioRecorder(audioData -> {
            // ← Replace this with your real AI provider:
            // return transcriptionModel.call(new AudioTranscriptionPrompt(...)).getResult().getOutput();
            return "[Simulated] Received %s of %s (%d seconds). "
                    .formatted(fmt(audioData.bytes().length),
                               audioData.mimeType(),
                               audioData.durationSeconds())
                    + "In production, this would be the real transcription from OpenAI Whisper, "
                    + "Google Gemini, Anthropic, or any AI provider you connect.";
        });
        recorder1.setCanvasSize(500, 120);
        recorder1.applyDarkTheme();
        recorder1.setVisualMode(AudioRecorder.VisualMode.WAVEFORM);
        recorder1.setMaxDuration(120);

        recorder1.addRecordingStartedListener(e -> {
            status1.setText("🔴 Recording...");
            transcription1.clear();
        });
        recorder1.addRecordingStoppedListener(e ->
                status1.setText("⏳ Processing audio (" + e.getDurationSeconds() + "s)..."));
        recorder1.addHandlerResultListener(e -> {
            transcription1.setValue(e.getResult());
            status1.setText("✅ Transcription complete");
        });
        recorder1.addRecordingErrorListener(e -> {
            status1.setText("❌ " + e.getMessage());
            Notification.show(e.getMessage(), 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        add(recorder1, status1, transcription1);
        add(codeBlock("""
            // Spring AI + OpenAI Whisper:
            AudioRecorder recorder = new AudioRecorder(audio -> {
                var options = OpenAiAudioTranscriptionOptions.builder()
                    .withLanguage("es")
                    .withResponseFormat(TranscriptResponseFormat.TEXT)
                    .build();
                var prompt = new AudioTranscriptionPrompt(
                    new ByteArrayResource(audio.bytes()), options);
                return transcriptionModel.call(prompt)
                    .getResult().getOutput();
            });
            recorder.addHandlerResultListener(e ->
                textArea.setValue(e.getResult()));
            add(recorder, textArea);"""));

        // ━━━ EXAMPLE 2: Upload audio file → play → transcribe → text ━━━━
        add(createSection("Example 2 — Upload Audio File & Transcribe",
            "Upload an existing audio file (MP3, WAV, OGG, WebM, etc.). "
            + "You can play it back and the handler transcribes it automatically. "
            + "Same handler, same result — works for recordings and file uploads."));

        TextArea transcription2 = new TextArea("Transcription");
        transcription2.setWidthFull();
        transcription2.setReadOnly(true);
        transcription2.setPlaceholder("Upload an audio file and the transcription will appear here...");

        Span status2 = statusLabel();

        AudioRecorder recorder2 = new AudioRecorder(audioData -> {
            // Same handler — works for both recorded and uploaded audio
            return "[Simulated] Uploaded file: %s of %s (%d seconds). "
                    .formatted(fmt(audioData.bytes().length),
                               audioData.mimeType(),
                               audioData.durationSeconds())
                    + "In production, this would be the real transcription text.";
        });
        recorder2.setCanvasSize(500, 120);
        recorder2.applySunsetTheme();
        recorder2.setVisualMode(AudioRecorder.VisualMode.BARS);

        recorder2.addHandlerResultListener(e -> {
            transcription2.setValue(e.getResult());
            status2.setText("✅ Transcription complete");
        });
        recorder2.addRecordingStartedListener(e -> {
            status2.setText("🔴 Recording...");
            transcription2.clear();
        });
        recorder2.addRecordingStoppedListener(e ->
                status2.setText("⏳ Processing..."));
        recorder2.addRecordingErrorListener(e -> {
            status2.setText("❌ " + e.getMessage());
            Notification.show(e.getMessage(), 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        add(recorder2, status2, transcription2);
        add(codeBlock("""
            // Upload works with the same AudioHandler.
            // The ⬆ Upload button in the recorder opens a file picker.
            // When the user selects a file, it is sent to the handler
            // just like a live recording — same bytes, same flow.
            AudioRecorder recorder = new AudioRecorder(audio ->
                whisperService.transcribe(audio.bytes(), audio.mimeType())
            );
            recorder.addHandlerResultListener(e ->
                textArea.setValue(e.getResult()));
            add(recorder, textArea);"""));
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private VerticalLayout createSection(String title, String description) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.add(new H3(title));
        if (description != null) {
            Paragraph p = new Paragraph(description);
            p.getStyle().set("color", "var(--lumo-secondary-text-color)");
            section.add(p);
        }
        return section;
    }

    private Div codeBlock(String code) {
        Div block = new Div();
        block.setText(code);
        block.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "12px 16px")
                .set("border-radius", "8px")
                .set("font-family", "monospace")
                .set("font-size", "12px")
                .set("white-space", "pre")
                .set("overflow-x", "auto")
                .set("line-height", "1.5")
                .set("margin-bottom", "24px");
        return block;
    }

    private Span statusLabel() {
        Span s = new Span("Ready");
        s.getStyle().set("font-size", "13px")
                    .set("color", "var(--lumo-secondary-text-color)");
        return s;
    }

    private static String fmt(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
