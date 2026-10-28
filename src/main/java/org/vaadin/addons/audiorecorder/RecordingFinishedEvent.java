package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;

import java.util.Base64;

/**
 * Fired when the recording finishes and audio data is available.
 * <p>
 * Use {@link #getAudioBytes()} to get the raw bytes ready for any AI provider
 * or {@link #getBase64()} if you need the encoded string.
 */
@DomEvent("recording-finished")
public class RecordingFinishedEvent extends ComponentEvent<AudioRecorder> {

    /** Default maximum payload size: 50 MB. */
    private static final long DEFAULT_MAX_SIZE_BYTES = 50L * 1024 * 1024;

    private final String base64;
    private final String mimeType;
    private final int durationSeconds;
    private final long sizeBytes;
    private byte[] cachedAudioBytes;
    private final double reportedSizeBytes;

    public RecordingFinishedEvent(
            AudioRecorder source,
            boolean fromClient,
            @EventData("event.detail.base64") String base64,
            @EventData("event.detail.mimeType") String mimeType,
            @EventData("event.detail.durationSeconds") int durationSeconds,
            @EventData("event.detail.sizeBytes") double sizeBytes) {
        super(source, fromClient);
        this.base64 = base64;
        this.mimeType = mimeType;
        this.durationSeconds = durationSeconds;
        this.sizeBytes = (long) sizeBytes;
        this.reportedSizeBytes = sizeBytes;
    }

    /**
     * Returns the raw audio bytes, decoded and ready to send to any backend.
     * The result is cached so subsequent calls don't re-decode.
     * <p>
     * Example with OpenAI:
     * <pre>{@code
     * byte[] audio = event.getAudioBytes();
     * whisperClient.transcribe(audio);
     * }</pre>
     *
     * @throws IllegalStateException if the payload exceeds the maximum allowed size
     */
    public byte[] getAudioBytes() {
        if (cachedAudioBytes == null) {
            // The client controls both the encoded content and all metadata.
            long maxEncodedLength = 4 * ((DEFAULT_MAX_SIZE_BYTES + 2) / 3);
            if (base64 == null || base64.length() > maxEncodedLength
                    || !Double.isFinite(reportedSizeBytes) || reportedSizeBytes < 0
                    || reportedSizeBytes != sizeBytes || sizeBytes > DEFAULT_MAX_SIZE_BYTES
                    || durationSeconds < 0) {
                throw new IllegalStateException("Invalid or oversized audio payload (max 50 MiB)");
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Invalid Base64 audio payload", ex);
            }
            if (decoded.length > DEFAULT_MAX_SIZE_BYTES || decoded.length != sizeBytes) {
                throw new IllegalStateException("Audio payload size does not match actual content");
            }
            cachedAudioBytes = decoded;
        }
        return cachedAudioBytes;
    }

    /** Returns the Base64-encoded audio string. */
    public String getBase64() {
        return base64;
    }

    /** Returns the MIME type (e.g. "audio/webm", "audio/ogg"). */
    public String getMimeType() {
        return mimeType;
    }

    /** Returns the recording duration in seconds. */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /** Returns the audio blob size in bytes. */
    public long getSizeBytes() {
        return sizeBytes;
    }
}
