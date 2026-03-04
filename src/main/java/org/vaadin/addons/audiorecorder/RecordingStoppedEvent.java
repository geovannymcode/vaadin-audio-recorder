package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;

@DomEvent("recording-stopped")
public class RecordingStoppedEvent extends ComponentEvent<AudioRecorder> {

    private final int durationSeconds;

    public RecordingStoppedEvent(
            AudioRecorder source, boolean fromClient,
            @EventData("event.detail.durationSeconds") int durationSeconds) {
        super(source, fromClient);
        this.durationSeconds = durationSeconds;
    }

    public int getDurationSeconds() { return durationSeconds; }
}
