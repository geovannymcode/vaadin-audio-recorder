package org.vaadin.addons.audiorecorder;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.lumo.Lumo;

/** Keeps the demo's existing Lumo appearance explicit on Vaadin 25. */
@StyleSheet(Lumo.STYLESHEET)
public class DemoAppShell implements AppShellConfigurator {
}
