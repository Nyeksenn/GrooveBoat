package io.github.Nyeksenn.grooveboat.library;

import io.ebean.Database;
import java.util.List;
import java.util.Properties;

/** In-memory H2 database with the real migrations, registered as the default Ebean server like in {@code Main}. */
final class TestDatabase {
    private TestDatabase() {
    }

    @SuppressWarnings("deprecation")
    static Database start() {
        var properties = new Properties();
        properties.put("ebean.migration.run", "true");
        properties.put("datasource.db.username", "gb");
        properties.put("datasource.db.password", "");
        properties.put("datasource.db.databaseDriver", "org.h2.Driver");
        properties.put("datasource.db.databaseUrl", "jdbc:h2:mem:grooveboat-test;DB_CLOSE_DELAY=-1");
        var cfg = Database.builder();
        cfg.loadFromProperties(properties);
        cfg.setDdlGenerate(false);
        cfg.setDdlRun(false);
        cfg.setDdlCreateOnly(false);
        cfg.setDefaultServer(true);
        cfg.setPackages(List.of("io.github.Nyeksenn.grooveboat.entities"));
        return cfg.build();
    }
}
