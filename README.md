# 🎙️ Audio Recorder for Vaadin

Vaadin Flow add-on with microphone recording, waveform and frequency visualization,
a gain indicator, pause/resume, duration limits, playback, audio file upload,
custom themes, programmatic controls and recording/error events.

Requires **Java 25**, **Vaadin 25.3.5** and a **Jakarta Servlet 6.1** container.
Spring Boot, Spring AI and transcription services are optional application choices;
the library adds none of them. License: Apache 2.0.

## Installation

Version **0.2.0** is the proposed migration release. Publication to Maven Central
or Vaadin Directory has not been verified. Build and install locally first:

```bash
mvn clean install
```

In a Vaadin 25 application using Java 25:

```xml
<dependency>
    <groupId>org.vaadin.addons.audiorecorder</groupId>
    <artifactId>audio-recorder</artifactId>
    <version>0.2.0</version>
</dependency>
```

The consuming application supplies Vaadin and the Servlet runtime. Both library
dependencies have `provided` scope. Use a compatible Vaadin BOM in your application.
The existing 0.1.0 release remains the Java 21 / Vaadin 24 baseline.

## Basic usage

```java
AudioRecorder recorder = new AudioRecorder();
recorder.addRecordingFinishedListener(event -> {
    byte[] audio = event.getAudioBytes();
    String mime = event.getMimeType();
    // Store or process the audio using your application's service.
});
recorder.addRecordingErrorListener(event -> Notification.show(event.getMessage()));
add(recorder);
```

Or process recordings and uploads through the same optional handler:

```java
AudioRecorder recorder = new AudioRecorder(audio ->
    "Received " + audio.bytes().length + " bytes of " + audio.mimeType());
recorder.addHandlerResultListener(event -> resultArea.setValue(event.getResult()));
add(recorder);
```

`AudioHandler` runs synchronously under the Vaadin request/session lock. Keep it
short. For slow transcription, use an application-managed bounded executor;
copy the needed audio metadata, capture the attached `UI`, and apply background
results through `ui.access(...)`, checking attachment and handling detach/cancellation.
Configure push or polling in the host application if background results need to
appear immediately. Do not access Vaadin components directly from worker threads.
The add-on does not manage asynchronous callbacks or provider credentials.

## Configuration and headless controls

```java
recorder.setVisualMode(AudioRecorder.VisualMode.BARS); // or WAVEFORM
recorder.setCanvasSize(500, 150);                       // positive dimensions
recorder.setMaxDuration(120);                          // active seconds; 0 = unlimited
recorder.setStrokeColor("#e94560");
recorder.setBackgroundColor("#1a1a2e");
recorder.setGainColor("#f5a623");
recorder.applyDarkTheme(); // also applyLightTheme(), applySunsetTheme()

recorder.setShowControls(false);
Button start = new Button("Record", event -> recorder.startRecording());
Button pause = new Button("Pause", event -> recorder.pauseRecording());
Button resume = new Button("Resume", event -> recorder.resumeRecording());
Button stop = new Button("Stop", event -> recorder.stopRecording());
add(recorder, start, pause, resume, stop);
```

Hiding controls keeps the visualization and hides the built-in upload/playback
buttons too. Recording needs an attached component and browser permission.

## Events

| Event | Payload |
| --- | --- |
| `RecordingStartedEvent` | Source and client origin |
| `RecordingStoppedEvent` | `durationSeconds` |
| `RecordingFinishedEvent` | `audioBytes`, `base64`, `mimeType`, `durationSeconds`, `sizeBytes` |
| `RecordingErrorEvent` | `message` |
| `AudioHandlerResultEvent` | Handler `result` |

Listener registration methods return a removable `Registration`. Finished events
are used for both recordings and uploads. Uploaded duration is best effort and is
zero if metadata is unavailable. Pause/resume also emit client custom events;
there are no Java pause/resume event classes in the existing API.

## Architecture and limits

The Java `AudioRecorder` wraps a Lit web component shipped in
`META-INF/frontend/audio-recorder-element.ts`. Java setters update
client properties; programmatic controls call JavaScript functions. MediaRecorder
captures audio, Web Audio supplies analyzer data and Canvas draws the visualization.
Custom DOM events cross the Flow bridge using `@DomEvent` and `@EventData`.

Audio travels as Base64 in a Flow event, with a **50 MiB decoded payload ceiling**.
The browser checks size before encoding and stops recordings that exceed this
ceiling. Server decoding checks encoded length, actual decoded size, declared
size and basic metadata. `AudioHandler` rejects unsupported MIME types and emits
error events for invalid audio or handler failures. Manual finished listeners
should catch `IllegalStateException` from `getAudioBytes()` and enforce their
own content policy. Supported MIME base types are WebM, Ogg, MP4, MPEG, WAV,
x-WAV, AAC and FLAC; codec parameters are accepted. Unknown upload MIME types
are rejected rather than guessed.

Client MIME types, durations and file contents are untrusted. A MIME allowlist
is not media-content verification. Validate actual content before saving or
passing it to a downstream service. The Base64 string has already entered the
server request before validation; encoding and decoding create additional memory
copies. Configure request size limits, quotas and concurrency limits in the host
application/proxy. A streaming transport would be a separate design change.
`setMaxDuration` is a browser convenience, not a server security boundary; pause
time is excluded, and browser timers may be throttled.

Microphone tracks and AudioContext are released on stop, errors and detach;
playback object URLs are revoked on completion, failure, replacement and detach.

## Browser and microphone requirements

Use a current browser supported by [Vaadin 25](https://vaadin.com/docs/latest/compatibility)
with MediaRecorder, Web Audio and `getUserMedia`. Microphone capture needs **HTTPS
or localhost** and explicit user permission. Embedded applications may also need
an iframe microphone permission policy. Supported recording codecs vary by browser;
if WebM is unsupported the browser chooses its recording format. Uploaded audio
and playback support also depend on installed browser codecs. A real microphone,
permission denial, Safari and mobile behavior require manual cross-browser checks.

## Demo (no Spring Boot or API keys)

Requirements: JDK 25, Maven 3.8+ (validated with 3.9.9), Node.js 24+ and npm 11.3+
for the Vaadin frontend. The Vaadin plugin can provision its supported Node version.

```bash
mvn -Pdemo jetty:run
```

Open [localhost:8080](http://localhost:8080). Jetty **12.1.14 EE11** runs the demo
from test output with `useTestScope=true`. The profile supplies the development server and logging and adds `src/demo/java` as a
test source; the ordinary library build never compiles or packages the demo.
The two examples retain recording → simulated transcription and file upload →
simulated transcription. Neither calls an external provider. Stop with Ctrl+C.

Spring AI snippets shown in the demo are **illustrative**, not compiled or
validated provider integrations. Older `.withLanguage(...)` / `.withResponseFormat(...)`
examples may differ from the Spring AI version selected by your host application.
Use that version's official documentation and dependencies when adding a provider;
no Spring starter or provider dependency is required by this library.

## Build, tests and packaging

```bash
mvn clean verify                 # Java component, payload and handler tests; library JAR
mvn -Pdirectory clean verify     # library JAR and Vaadin Directory ZIP
jar tf target/audio-recorder-0.2.0.jar
unzip -p target/audio-recorder-0.2.0.jar META-INF/MANIFEST.MF
javap -verbose -classpath target/audio-recorder-0.2.0.jar org.vaadin.addons.audiorecorder.AudioRecorder
```

The bytecode major version must be **69**. The JAR contains the component, event
classes and frontend resource, without `TestView.class`, a servlet container or
application frontend bundles. Frontend preparation is confined to the demo
profile. Consumer applications build their own production frontend with the
Vaadin Maven plugin's `build-frontend` goal; the add-on JAR is not an executable
production application. Automated Java tests do not establish browser behavior.

The Directory profile creates `target/audio-recorder-0.2.0.zip` during `package`;
it contains the JAR, sources, demo, tests, POM, README and license. Review the archive
and Directory requirements before a manual submission. These commands do not
publish a release.

## Migration references

- [Vaadin upgrade guide](https://vaadin.com/docs/latest/upgrading)
- [Vaadin compatibility](https://vaadin.com/docs/latest/compatibility)
- [Vaadin roadmap](https://vaadin.com/roadmap)
- [Vaadin 25.3.5 release](https://github.com/vaadin/platform/releases/tag/25.3.5)
- [Jetty EE11 Maven plugin](https://jetty.org/docs/jetty/12.1/programming-guide/maven-jetty/jetty-maven-plugin.html)

## License

Apache License 2.0

## Author

**Geovanny Mendoza** — [@geovannymcode](https://github.com/geovannymcode) · Vaadin Champion
