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

    /**
     * Stores the session credential (a password or an access token, depending on the backend).
     *
     * @param label shown to the user in their keyring manager
     */
    public CompletableFuture<Boolean> storePassword(String username, String protocol, String server, String password, String label) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
                return Secret.passwordStorevSync(SCHEMA, attributes, Secret.COLLECTION_DEFAULT, label, password, null);
            } catch (GErrorException e) {
                return false;
            }
        }, exec);
    }

    public CompletableFuture<String> lookupPassword(String username, String protocol, String server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
                return Secret.passwordLookupvSync(SCHEMA, attributes, null);
            } catch (GErrorException e) {
                return null;
            }
        }, exec);
    }

    /** Removes the stored credential. Completes with whether an entry was removed. */
    public CompletableFuture<Boolean> clearPassword(String username, String protocol, String server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
                return Secret.passwordClearvSync(SCHEMA, attributes, null);
            } catch (GErrorException e) {
                return false;
            }
        }, exec);
    }

    @Override
    public void close() {
        exec.close();
    }
}
