package io.github.Nyeksenn.grooveboat;

import io.ebean.DatabaseFactory;
import io.ebean.config.DatabaseConfig;
import io.github.Nyeksenn.grooveboat.ui.AlbumListView;
import io.github.Nyeksenn.grooveboat.ui.GrooveboatApp;
import io.github.Nyeksenn.grooveboat.ui.MusicChooser;
import io.github.jwharm.javagi.base.GErrorException;
import io.github.jwharm.javagi.gtk.types.TemplateTypes;
import org.gnome.gio.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gnome.glib.GLib;

import java.nio.file.Paths;
import java.util.Properties;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws GErrorException {
        var cfg = new DatabaseConfig();
        var properties = new Properties();
        properties.put("ebean.db.ddl.generate", "true");
        properties.put("ebean.db.ddl.run", "true");
        properties.put("datasource.db.username", "gb");
        properties.put("datasource.db.password", "");
        properties.put("datasource.db.databaseDriver", "org.h2.Driver");

        var dataDir = GLib.getUserDataDir();
        var dbPath = Paths.get(dataDir, "grooveboat", "cache");
        var dbBaseUrl = "jdbc:h2:%s:grooveboat";
        var dbUrl = String.format(dbBaseUrl, dbPath);
        properties.put("datasource.db.databaseUrl",dbUrl);

        cfg.loadFromProperties(properties);
        cfg.setDefaultServer(true);
        var server = DatabaseFactory.create(cfg);

        var resource = Resource.load(
                "src/main/resources/grooveboat.gresource");
        resource.resourcesRegister();
        TemplateTypes.register(MusicChooser.class);
        TemplateTypes.register(AlbumListView.class);

        new GrooveboatApp().run(args);
    }
}
