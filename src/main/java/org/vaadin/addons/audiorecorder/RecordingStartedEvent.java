package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.DomEvent;

@DomEvent("recording-started")
public class RecordingStartedEvent extends ComponentEvent<AudioRecorder> {
    public RecordingStartedEvent(AudioRecorder source, boolean fromClient) {
        super(source, fromClient);
    }
}
