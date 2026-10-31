package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.backend.BackendType;
import org.gnome.adw.NavigationPage;
import org.gnome.glib.Variant;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

/** Setup step where the user picks the kind of server. The backend is fixed until the next logout. */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/server-type-page.ui", name = "ServerTypePage")
public class ServerTypePage extends NavigationPage {

    private void choose(BackendType type) {
        SetupDialog.of(this).setBackendType(type);
        activateActionIfExists("navigation.push", Variant.string("connection-page"));
    }

    @GtkCallback(name = "opensubsonic-selected")
    public void opensubsonicSelected() {
        choose(BackendType.OPENSUBSONIC);
    }

    @GtkCallback(name = "jellyfin-selected")
    public void jellyfinSelected() {
        choose(BackendType.JELLYFIN);
    }

    public ServerTypePage(MemorySegment address) {
        super(address);
    }
}
