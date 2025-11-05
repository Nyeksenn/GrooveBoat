package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.secret.Schema;
import org.gnome.secret.SchemaType;
import org.gnome.secret.Secret;
import org.javagi.base.GErrorException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecretKeeper implements AutoCloseable {
    private static final Schema SCHEMA = Secret.getSchema(SchemaType.COMPAT_NETWORK);
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private SecretKeeper() {
    }

    private static class Holder {
        private static final SecretKeeper INSTANCE = new SecretKeeper();
    }

    public static SecretKeeper getInstance() {
        return SecretKeeper.Holder.INSTANCE;
    }

    public CompletableFuture<Boolean> storePassword(String username, String protocol, String server, String password) throws GErrorException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
                return Secret.passwordStoreSync(SCHEMA, attributes, Secret.COLLECTION_DEFAULT, "GrooveBoat Server Password", password, null);
            } catch (GErrorException e) {
                return false;
            }
        }, exec);
    }

    public CompletableFuture<String> lookupPassword(String username, String protocol, String server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
                attributes.insert("user", username);
                return Secret.passwordLookupSync(SCHEMA, attributes, null);
            } catch (GErrorException e) {
                return null;
            }
        }, exec);
    }

    @Override
    public void close() {
        exec.close();
    }
}
