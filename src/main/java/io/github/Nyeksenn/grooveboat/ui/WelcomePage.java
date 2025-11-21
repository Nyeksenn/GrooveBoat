package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.NavigationPage;
import org.gnome.gtk.Button;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/welcome-page.ui", name = "WelcomePage")
public class WelcomePage extends NavigationPage {
    @GtkChild(name = "connection-next")
    public Button nextButton;

    @InstanceInit
    public void init() {
        onRealize(() -> nextButton.grabFocus());
    }

    public WelcomePage(MemorySegment address) {
        super(address);
    }
}
