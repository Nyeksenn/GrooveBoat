package io.github.Nyeksenn.grooveboat;

import io.ebean.DatabaseFactory;
import io.ebean.config.DatabaseConfig;
import io.github.Nyeksenn.grooveboat.audio.QueueStore;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import io.github.Nyeksenn.grooveboat.ui.*;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.freedesktop.gstreamer.audio.GstAudio;
import org.gnome.gio.Resource;
import org.javagi.base.GErrorException;
import org.javagi.gobject.types.Types;
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

        var dataDir = Helpers.getUserDataPath();
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
        AppSettings.migrate();

        try {
            var resource = Resource.load(
                    "src/main/resources/grooveboat.gresource");
            resource.resourcesRegister();
            TemplateTypes.register(MusicChooser.class);
            TemplateTypes.register(AlbumListView.class);
            TemplateTypes.register(ArtistListView.class);
            TemplateTypes.register(MusicPlayer.class);
            TemplateTypes.register(SongListPage.class);
            TemplateTypes.register(ArtistSongsPage.class);
            TemplateTypes.register(Avatar.class);
            TemplateTypes.register(WelcomePage.class);
            TemplateTypes.register(ServerTypePage.class);
            TemplateTypes.register(ConnectionPage.class);
            TemplateTypes.register(CredentialsPage.class);
            TemplateTypes.register(MusicControls.class);
            TemplateTypes.register(SongQueue.class);
            TemplateTypes.register(MiniPlayer.class);
            Types.register(RoundedImage.class);
            Types.register(QueueStore.class);
        } catch (GErrorException e) {
            logger.error("Couldn't load resources. Exiting");
            return;
        }
        new GrooveboatApp().run(args);
    }
}
