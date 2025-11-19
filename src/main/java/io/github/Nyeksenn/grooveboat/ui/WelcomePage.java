package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.NavigationPage;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/welcome-page.ui", name = "WelcomePage")
public class WelcomePage extends NavigationPage {
    public WelcomePage(MemorySegment address) {
        super(address);
    }
}
