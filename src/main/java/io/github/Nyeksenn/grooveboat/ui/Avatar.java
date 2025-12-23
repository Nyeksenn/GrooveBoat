package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.gio.Settings;
import org.gnome.gtk.Button;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/avatar.ui", name = "Avatar")
public class Avatar extends Button {
    private final Settings settings = new Settings("io.github.Nyeksenn.grooveboat");

    @GtkChild(name = "gb-avatar")
    public org.gnome.adw.Avatar gbAvatar;

    @GtkCallback(name = "account-menu-clicked")
    public void accountMenuClicked() {
        activateActionIfExists("app.account-settings", null);
    }

    @InstanceInit
    public void init() {
        var username = settings.getString("username");
        this.gbAvatar.setText(username);
    }

    public Avatar() {
    }

    public Avatar(MemorySegment address) {
        super(address);
    }
}
