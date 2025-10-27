package io.github.Nyeksenn.grooveboat;

import io.ebean.annotation.Platform;
import io.ebean.dbmigration.DbMigration;

import java.io.IOException;

public class MigrationGenerator {

    /**
     * Generate the next "DB schema DIFF" migration.
     */
    public static void main(String[] args) throws IOException {

        DbMigration dbMigration = DbMigration.create();

        dbMigration.addPlatform(Platform.H2);

        dbMigration.generateMigration();
    }
}