package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.gtk.Button;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/avatar.ui", name = "Avatar")
public class Avatar extends Button {
    @GtkCallback(name = "account-menu-clicked")
    public void accountMenuClicked() {
        activateActionIfExists("app.account-settings", null);
    }

    public Avatar() {
    }

    public Avatar(MemorySegment address) {
        super(address);
    }
}
