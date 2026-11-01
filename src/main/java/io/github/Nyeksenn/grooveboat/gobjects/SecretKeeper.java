package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.secret.Schema;
import org.gnome.secret.SchemaType;
import org.gnome.secret.Secret;
import org.javagi.base.GErrorException;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The session credential in the desktop keyring (libsecret). The calls block until the keyring answers, so they must
 * never run on the GTK main thread; call them from the I/O pool.
 */
public final class SecretKeeper {
    private static final Schema SCHEMA = Secret.getSchema(SchemaType.COMPAT_NETWORK);

    /** The synchronous libsecret calls used to share one thread and so ran one at a time; the lock keeps that. */
    private final ReentrantLock lock = new ReentrantLock();

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
     * @return whether the credential was stored
     */
    public boolean storePassword(String username, String protocol, String server, String password, String label) {
        lock.lock();
        try {
            var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
            return Secret.passwordStorevSync(SCHEMA, attributes, Secret.COLLECTION_DEFAULT, label, password, null);
        } catch (GErrorException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** The stored credential, or empty when there is none or the keyring can't be read. */
    public Optional<String> lookupPassword(String username, String protocol, String server) {
        lock.lock();
        try {
            var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
            return Optional.ofNullable(Secret.passwordLookupvSync(SCHEMA, attributes, null));
        } catch (GErrorException e) {
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /** Removes the stored credential. Returns whether an entry was removed. */
    public boolean clearPassword(String username, String protocol, String server) {
        lock.lock();
        try {
            var attributes = Secret.attributesBuild(SCHEMA, "user", username, "protocol", protocol, "server", server, null);
            return Secret.passwordClearvSync(SCHEMA, attributes, null);
        } catch (GErrorException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }
}
