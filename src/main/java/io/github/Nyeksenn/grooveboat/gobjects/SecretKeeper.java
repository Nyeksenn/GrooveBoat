package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.glib.GLib;
import org.gnome.glib.HashTable;
import org.gnome.secret.Schema;
import org.gnome.secret.SchemaAttributeType;
import org.gnome.secret.SchemaFlags;
import org.gnome.secret.Secret;
import org.javagi.base.GErrorException;

import java.lang.foreign.ValueLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecretKeeper {
    private final Schema SCHEMA;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final HashTable<String, String> attributes;

    private SecretKeeper() {
        var schemaMap = new HashTable<>(GLib::strHash, GLib::strEqual,
                (address) -> address.getString(0), (address) -> SchemaAttributeType.of(address.get(ValueLayout.JAVA_INT, 0)));
        schemaMap.put("password", SchemaAttributeType.STRING);
        this.SCHEMA = new Schema("io.github.Nyeksenn.grooveboat.Store", SchemaFlags.NONE, schemaMap);
        this.attributes = new HashTable<>(GLib::strHash, GLib::strEqual,
                (address) -> address.getString(0), (address) -> address.getString(0));
        attributes.put("id", "grooveboat-pwd");
    }

    private static class Holder {
        private static final SecretKeeper INSTANCE = new SecretKeeper();
    }

    public static SecretKeeper getInstance() {
        return SecretKeeper.Holder.INSTANCE;
    }

    public CompletableFuture<Boolean> storePassword(String password) throws GErrorException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Secret.passwordStoreSync(SCHEMA, attributes, Secret.COLLECTION_DEFAULT, "Server Password", password, null);
            } catch (GErrorException e) {
                return false;
            }
        }, exec);
    }

    public CompletableFuture<String> lookupPassword() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Secret.passwordLookupSync(SCHEMA, attributes, null);
            } catch (GErrorException e) {
                return null;
            }
        }, exec);
    }
}
