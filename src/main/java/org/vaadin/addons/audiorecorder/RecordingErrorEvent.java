package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;

@DomEvent("recording-error")
public class RecordingErrorEvent extends ComponentEvent<AudioRecorder> {

    private final String message;

    public RecordingErrorEvent(
            AudioRecorder source, boolean fromClient,
            @EventData("event.detail.message") String message) {
        super(source, fromClient);
        this.message = message;
    }

    public String getMessage() { return message; }
}
