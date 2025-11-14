package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.ActionRow;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/queue-card.ui")
@RegisteredType(name = "QueueCard")
public class QueueCard extends ActionRow {
    private static final Logger logger = LoggerFactory.getLogger(QueueCard.class);

    private String songId;

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public QueueCard(MemorySegment address) {
        super(address);
    }

    public QueueCard() {
    }
}
