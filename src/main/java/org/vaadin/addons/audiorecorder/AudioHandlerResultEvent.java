package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentEvent;

/**
 * Fired after the {@link AudioRecorder.AudioHandler} finishes processing.
 * Contains the result string returned by the handler (e.g. transcription text).
 */
public class AudioHandlerResultEvent extends ComponentEvent<AudioRecorder> {

    private final String result;

    public AudioHandlerResultEvent(AudioRecorder source, boolean fromClient, String result) {
        super(source, fromClient);
        this.result = result;
    }

    /** The result returned by the AudioHandler (e.g. transcription, file path, status). */
    public String getResult() {
        return result;
    }
}
