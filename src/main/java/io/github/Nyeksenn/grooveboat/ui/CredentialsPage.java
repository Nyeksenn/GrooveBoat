package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.NavigationPage;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/credentials-page.ui", name = "CredentialsPage")
public class CredentialsPage extends NavigationPage {
    public CredentialsPage(MemorySegment address) {
        super(address);
    }
}
