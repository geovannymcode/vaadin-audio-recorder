# 🎙️ Audio Recorder for Vaadin

A **Vaadin Flow** add-on for recording audio with **real-time waveform visualization**.  
**AI-agnostic** — works with OpenAI, Gemini, Anthropic, Deepgram, or no AI at all.

![Vaadin 24](https://img.shields.io/badge/Vaadin-24-blue)
![Java 21+](https://img.shields.io/badge/Java-21%2B-orange)
![Zero Dependencies](https://img.shields.io/badge/Dependencies-Zero-brightgreen)
![License](https://img.shields.io/badge/License-Apache%202.0-green)

## Why This Add-on?

You add one Maven dependency, write 3 lines of Java, and your Vaadin app has a
full audio recorder with animated waveform — **without writing a single line of
JavaScript**. The audio bytes arrive at your server ready to forward to any
transcription service or save to disk.

## Installation

```xml
<dependency>
    <groupId>org.vaadin.addons.audiorecorder</groupId>
    <artifactId>audio-recorder</artifactId>
    <version>0.1.0</version>
</dependency>
```

```xml
<repository>
    <id>vaadin-addons</id>
    <url>https://maven.vaadin.com/vaadin-addons</url>
</repository>
```

## Quick Start

### 3 lines — record and get the bytes

```java
AudioRecorder recorder = new AudioRecorder();

recorder.addRecordingFinishedListener(event -> {
    byte[] audio = event.getAudioBytes();
    String mime  = event.getMimeType();
    // Send to Whisper, Gemini, save to DB — your choice
});

add(recorder);
```

### With `AudioHandler` — plug any AI in one lambda

```java
AudioRecorder recorder = new AudioRecorder(audio ->
    transcriptionModel.call(
        new AudioTranscriptionPrompt(new ByteArrayResource(audio.bytes()))
    ).getResult().getOutput()
);

recorder.addHandlerResultListener(e -> textArea.setValue(e.getResult()));
add(recorder);
```

### Programmatic control (custom buttons)

```java
AudioRecorder recorder = new AudioRecorder();
recorder.setShowControls(false);

Button recBtn  = new Button("Record", e -> recorder.startRecording());
Button stopBtn = new Button("Stop",   e -> recorder.stopRecording());

recorder.addRecordingFinishedListener(event -> {
    byte[] audio = event.getAudioBytes();
    // process...
});

add(recorder, recBtn, stopBtn);
```

## Full Example — Spring AI + OpenAI Whisper

This is how you wire the add-on to get **real speech-to-text** in a
Spring Boot + Vaadin application.

### 1. Add dependencies to your `pom.xml`

```xml
<!-- Audio Recorder add-on -->
<dependency>
    <groupId>org.vaadin.addons.audiorecorder</groupId>
    <artifactId>audio-recorder</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Spring AI OpenAI (includes Whisper) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

### 2. Configure your API key in `application.yml`

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

### 3. Create your Vaadin view

```java
@Route("transcribe")
public class TranscribeView extends VerticalLayout {

    public TranscribeView(OpenAiAudioTranscriptionModel transcriptionModel) {

        TextArea result = new TextArea("Transcription");
        result.setWidthFull();
        result.setReadOnly(true);

        AudioRecorder recorder = new AudioRecorder(audio -> {
            OpenAiAudioTranscriptionOptions options =
                OpenAiAudioTranscriptionOptions.builder()
                    .withLanguage("es")
                    .withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                    .withTemperature(0f)
                    .build();

            Resource audioResource = new ByteArrayResource(audio.bytes());
            AudioTranscriptionPrompt prompt =
                new AudioTranscriptionPrompt(audioResource, options);

            return transcriptionModel.call(prompt)
                    .getResult().getOutput();
        });

        recorder.addHandlerResultListener(e -> result.setValue(e.getResult()));
        recorder.addRecordingErrorListener(e ->
            Notification.show(e.getMessage(), 3000, Notification.Position.BOTTOM_START));

        recorder.setCanvasSize(500, 120);
        recorder.applyDarkTheme();

        add(new H2("🎙️ Voice Transcription"), recorder, result);
    }
}
```

That's it — record audio, get text. **No JavaScript, no REST controllers,
no manual file handling.**

## Features

| Feature | Description |
|---------|-------------|
| 🎤 Record | Captures from microphone via MediaRecorder API |
| 📊 Bars mode | Frequency bars mirrored from center with gradient |
| 〰️ Waveform mode | Oscilloscope-style time-domain line |
| 📈 Gain bar | Real-time level indicator |
| ⏸️ Pause/Resume | Full pause and resume support |
| ⏱️ Max duration | Auto-stop after N seconds |
| 🔊 Playback | Built-in play button for last recording |
| ⬆️ File upload | Upload existing audio files (MP3, WAV, OGG, etc.) for transcription |
| 🎨 Themes | 3 presets + full custom colours |
| 🔌 Headless mode | Hide controls, drive from Java |
| 📦 Zero deps | Only Vaadin core — no AI, no Spring |

## Configuration

```java
recorder.setVisualMode(AudioRecorder.VisualMode.BARS);     // or WAVEFORM
recorder.setCanvasSize(500, 150);
recorder.setMaxDuration(120);         // 0 = unlimited
recorder.setShowControls(false);      // headless mode

// Colours
recorder.setStrokeColor("#e94560");
recorder.setBackgroundColor("#1a1a2e");
recorder.setGainColor("#f5a623");

// Or theme presets
recorder.applyDarkTheme();
recorder.applyLightTheme();
recorder.applySunsetTheme();
```

## Events

| Event | Data | When |
|-------|------|------|
| `RecordingStartedEvent` | — | User clicks Record |
| `RecordingStoppedEvent` | `durationSeconds` | Recording ends |
| `RecordingFinishedEvent` | `audioBytes`, `base64`, `mimeType`, `durationSeconds`, `sizeBytes` | Audio data ready |
| `RecordingErrorEvent` | `message` | Microphone denied, handler error, etc. |
| `AudioHandlerResultEvent` | `result` | After AudioHandler completes |

## Architecture

```
┌──────────────────────────────────────────────────────┐
│  Browser (Lit Web Component)                         │
│  ┌───────────────┐ ┌───────────┐  ┌──────────────┐   │
│  │ MediaRecorder │ │ Web Audio │  │ Canvas viz   │   │
│  │  getUserMedia │ │ Analyser  │  │ bars/waveform│   │
│  └──────┬────────┘ └─────┬─────┘  └──────────────┘   │
│         │  Blob → Base64 │                           │
│         └───────┬────────┘                           │
│                 │ CustomEvent("recording-finished")  │
├─────────────────┼────────────────────────────────────┤
│  Server (Java)  │                                    │
│         ┌───────▼────────┐                           │
│         │ AudioRecorder  │ ← @DomEvent + @EventData  │
│         │  .getAudioBytes()                          │
│         └───────┬────────┘                           │
│                 │                                    │
│    ┌────────────┼───────────────┐                    │
│    ▼            ▼               ▼                    │
│  OpenAI     Gemini        Save to DB                 │
│  Whisper    Anthropic     File system                │
│  Deepgram   Spring AI     Custom logic               │
│  Azure      Vosk          Whatever you want          │
└──────────────────────────────────────────────────────┘
```

## Browser Support

Requires [MediaRecorder](https://caniuse.com/mediarecorder),
[Web Audio API](https://caniuse.com/audio-api), and
[getUserMedia](https://caniuse.com/stream).
All modern browsers: Chrome, Firefox, Edge, Safari 14.1+.

## Development

```bash
mvn jetty:run              # demo at http://localhost:8080
mvn versions:set -DnewVersion=0.1.0
mvn install -Pdirectory    # → target/audio-recorder-0.1.0.zip
```

## License

Apache License 2.0

## Author

**Geovanny Mendoza** — [@geovannymcode](https://github.com/geovannymcode) · Vaadin Champion
