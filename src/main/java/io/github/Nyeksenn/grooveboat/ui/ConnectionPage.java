package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.NavigationPage;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/connection-page.ui", name = "ConnectionPage")
public class ConnectionPage extends NavigationPage {
    public ConnectionPage(MemorySegment address) {
        super(address);
    }
}
