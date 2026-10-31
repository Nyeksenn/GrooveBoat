package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.HeaderBar;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.ViewSwitcher;
import org.gnome.adw.ViewSwitcherBar;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GLib;
import org.gnome.gtk.*;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-chooser.ui", name = "MusicChooser")
@RegisteredType(name = "MusicChooser")
public class MusicChooser extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(MusicChooser.class);

    @GtkChild(name = "music-chooser-header")
    public HeaderBar musicChooserHeader;

    @GtkChild(name = "music-switcher-title")
    public ViewSwitcher musicSwitcherTitle;

    @GtkChild(name = "music-switcher-bar")
    public ViewSwitcherBar musicSwitcherBar;

    @GtkChild(name = "mini-player")
    public MiniPlayer miniPlayer;

    @GtkChild(name = "album-searchbar")
    public SearchBar albumSearchbar;

    @GtkChild(name = "button-search")
    public ToggleButton buttonSearch;

    @GtkChild(name = "album-searchentry")
    public SearchEntry searchEntry;

    @GtkChild(name = "refresh-progress-bar")
    public ProgressBar refreshProgressBar;

    @GtkCallback(name = "search-clicked")
    public void searchClicked() {
        albumSearchbar.setSearchMode(!albumSearchbar.getSearchMode());
    }

    /** Runs a library sync in the background while the progress bar pulses on the main loop. */
    private void runRefresh(GrooveboatApp app, GrooveboatWindow win, boolean full) {
        var library = app.getLibrary();
        if (library == null || refreshProgressBar.getVisible()) {
            return;
        }
        refreshProgressBar.setFraction(0.0);
        refreshProgressBar.setVisible(true);
        var done = new AtomicBoolean(false);
        GLib.timeoutAdd(GLib.PRIORITY_DEFAULT, 200, () -> {
            if (done.get()) {
                return GLib.SOURCE_REMOVE;
            }
            refreshProgressBar.pulse();
            return GLib.SOURCE_CONTINUE;
        });
        app.executor.submit(() -> {
            library.refresh(full);
            GLib.idleAddOnce(() -> {
                done.set(true);
                refreshProgressBar.setVisible(false);
                win.activateActionIfExists("win.reload-albums", null);
            });
        });
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) win.getApplication();

            albumSearchbar.setKeyCaptureWidget((Widget) getRoot());
            albumSearchbar.bindProperty("search-mode-enabled", buttonSearch, "active").build();

            var refreshAction = new SimpleAction("refresh-data", null);
            refreshAction.onActivate((_) -> runRefresh(app, win, false));
            app.addAction(refreshAction);

            var fullRefreshAction = new SimpleAction("full-refresh", null);
            fullRefreshAction.onActivate((_) -> runRefresh(app, win, true));
            app.addAction(fullRefreshAction);
        });
    }

    public MusicChooser() {
    }

    public MusicChooser(MemorySegment address) {
        super(address);
    }
}
