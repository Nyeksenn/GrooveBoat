package io.github.Nyeksenn.grooveboat;

import io.ebean.DatabaseFactory;
import io.ebean.config.DatabaseConfig;
import io.github.Nyeksenn.grooveboat.ui.*;
import org.freedesktop.gstreamer.audio.GstAudio;
import org.gnome.gio.Resource;
import org.javagi.base.GErrorException;
import org.javagi.gtk.types.TemplateTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        var cfg = new DatabaseConfig();
        var properties = new Properties();
        properties.put("ebean.migration.run", "true");
        properties.put("datasource.db.username", "gb");
        properties.put("datasource.db.password", "");
        properties.put("datasource.db.databaseDriver", "org.h2.Driver");

        var dataDir = Utils.getUserDataPath();
        var dbPath = Paths.get(dataDir, "grooveboat", "cache");
        var dbBaseUrl = "jdbc:h2:file:%s";
        var dbUrl = String.format(dbBaseUrl, dbPath);
        properties.put("datasource.db.databaseUrl", dbUrl);

        cfg.loadFromProperties(properties);
        cfg.setDdlGenerate(false);
        cfg.setDdlRun(false);
        cfg.setDdlCreateOnly(false);
        cfg.setDefaultServer(true);
        cfg.setPackages(List.of("io.github.Nyeksenn.grooveboat.entities"));
        DatabaseFactory.create(cfg);

        // Make sure GstAudio is loaded to avoid getType returning null
        GstAudio.javagi$ensureInitialized();

        try {
            var resource = Resource.load(
                    "src/main/resources/grooveboat.gresource");
            resource.resourcesRegister();
            TemplateTypes.register(MusicChooser.class);
            TemplateTypes.register(AlbumListView.class);
            TemplateTypes.register(MusicPlayer.class);
            TemplateTypes.register(SongListPage.class);
            new GrooveboatApp().run(args);
        } catch (GErrorException e) {
            logger.error("Couldn't load resources. Exiting");
            return;
        }
    }
}
