package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.gtk.Widget;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Helpers for widgets that reach the app through their window and set themselves up when realized. */
final class AppWidgets {
    private AppWidgets() {
    }

    /** The main window above {@code widget}. Only valid while the widget is realized. */
    static GrooveboatWindow window(Widget widget) {
        return (GrooveboatWindow) widget.getRoot();
    }

    /** The application above {@code widget}. Only valid while the widget is realized. */
    static GrooveboatApp app(Widget widget) {
        return (GrooveboatApp) window(widget).getApplication();
    }

    /**
     * Runs {@code setup} the first time {@code widget} is realized. The widget is realized again whenever the window
     * crosses the size breakpoint, and one-time setup (loading data, adding actions) must not repeat.
     */
    static void onFirstRealize(Widget widget, Runnable setup) {
        var done = new AtomicBoolean(false);
        widget.onRealize(() -> {
            if (done.compareAndSet(false, true)) {
                setup.run();
            }
        });
    }

    /**
     * Runs {@code setup} every time {@code widget} is realized and releases what it registered in the scope every time
     * the widget is unrealized. For handlers on app-lifetime objects, which must not outlive the widget (logout
     * replaces the window).
     */
    static void whileRealized(Widget widget, Consumer<RealizedScope> setup) {
        var scope = new RealizedScope();
        widget.onRealize(() -> setup.accept(scope));
        widget.onUnrealize(scope::release);
    }
}
