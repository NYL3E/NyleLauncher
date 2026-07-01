package fr.nylerp.launcher.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.nylerp.launcher.config.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-account roster (max {@value #MAX_ACCOUNTS}) persisted in
 * {@code state/accounts.json}. Replaces the single-account session.json :
 * the user can keep e.g. one Microsoft premium account plus two offline
 * (crack) pseudos, and switch between them from the header capsule.
 *
 * Migration : on first load, if accounts.json doesn't exist yet, the legacy
 * session.json (single account, via {@link AuthManager}) is imported as the
 * active account then deleted. Accounts persist across bootstrap upgrades —
 * Microsoft entries are re-validated anyway by the silent refresh at startup,
 * and offline entries carry no token at all.
 */
public final class AccountStore {

    public static final int MAX_ACCOUNTS = 3;

    private static final Logger LOG = LoggerFactory.getLogger(AccountStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** On-disk shape. {@code schema} guards future migrations. */
    private record StoreFile(int schema, List<Account> accounts, int activeIndex) {}

    private static List<Account> accounts;       // in-memory cache
    private static int activeIndex = -1;

    private static Path file() {
        return AppPaths.launcherState().resolve("accounts.json");
    }

    private static synchronized void load() {
        if (accounts != null) return;
        accounts = new ArrayList<>();
        activeIndex = -1;

        Path f = file();
        if (Files.exists(f)) {
            try {
                StoreFile sf = GSON.fromJson(Files.readString(f), StoreFile.class);
                if (sf != null && sf.accounts != null) {
                    for (Account a : sf.accounts) {
                        if (a != null && a.username() != null && accounts.size() < MAX_ACCOUNTS) {
                            accounts.add(a);
                        }
                    }
                    activeIndex = sf.activeIndex;
                }
            } catch (Exception e) {
                LOG.warn("Could not read accounts.json — starting empty: {}", e.toString());
            }
        } else {
            // One-shot migration from the legacy single-account session.json.
            // AuthManager.loadSaved() keeps its own bootstrap-version guard, so a
            // session from an older launcher version is simply not imported.
            Account legacy = AuthManager.loadSaved();
            if (legacy != null) {
                LOG.info("Migrating legacy session.json account '{}' into accounts.json",
                        legacy.username());
                accounts.add(legacy);
                activeIndex = 0;
                persist();
            }
            AuthManager.clear();
        }

        if (activeIndex >= accounts.size()) activeIndex = accounts.isEmpty() ? -1 : 0;
        if (activeIndex < 0 && !accounts.isEmpty()) activeIndex = 0;
    }

    private static void persist() {
        try {
            Files.writeString(file(), GSON.toJson(new StoreFile(1, accounts, activeIndex)));
        } catch (Exception e) {
            LOG.warn("Could not save accounts.json: {}", e.toString());
        }
    }

    /** Identity match : MS accounts by UUID, offline accounts by pseudo (case-insensitive). */
    private static int indexOf(Account a) {
        for (int i = 0; i < accounts.size(); i++) {
            Account b = accounts.get(i);
            if (b.type() != a.type()) continue;
            if (a.type() == Account.Type.MICROSOFT) {
                if (b.uuid() != null && b.uuid().equalsIgnoreCase(a.uuid())) return i;
            } else {
                if (b.username() != null && b.username().equalsIgnoreCase(a.username())) return i;
            }
        }
        return -1;
    }

    // ── public API ──────────────────────────────────────────────────────────

    /** Immutable snapshot of the roster, in stored order. */
    public static synchronized List<Account> list() {
        load();
        return List.copyOf(accounts);
    }

    /** The currently selected account, or null when the roster is empty. */
    public static synchronized Account active() {
        load();
        return (activeIndex >= 0 && activeIndex < accounts.size())
                ? accounts.get(activeIndex) : null;
    }

    public static synchronized boolean isFull() {
        load();
        return accounts.size() >= MAX_ACCOUNTS;
    }

    /**
     * Adds the account (or refreshes the existing entry with the same identity)
     * and makes it active. Returns false when the roster is already full of
     * OTHER accounts — the caller should tell the user to remove one first.
     */
    public static synchronized boolean addAndActivate(Account a) {
        load();
        int i = indexOf(a);
        if (i >= 0) {
            accounts.set(i, a);     // refresh tokens / casing
            activeIndex = i;
            persist();
            return true;
        }
        if (accounts.size() >= MAX_ACCOUNTS) return false;
        accounts.add(a);
        activeIndex = accounts.size() - 1;
        persist();
        return true;
    }

    /** Switches the active account to an existing roster entry (no-op if absent). */
    public static synchronized void setActive(Account a) {
        load();
        int i = indexOf(a);
        if (i >= 0) {
            activeIndex = i;
            persist();
        }
    }

    /** Removes the active account ; the first remaining one (if any) becomes active. */
    public static synchronized void removeActive() {
        load();
        if (activeIndex >= 0 && activeIndex < accounts.size()) {
            Account removed = accounts.remove(activeIndex);
            LOG.info("Removed account '{}' from roster", removed.username());
        }
        activeIndex = accounts.isEmpty() ? -1 : 0;
        persist();
    }

    /**
     * Removes a SPECIFIC account (by identity) from the roster. Keeps the active index pointing at the
     * same account where possible. Returns {@code true} if the removed account WAS the active one — the
     * caller then switches to {@link #active()} (now the first remaining) or logs out if the roster is
     * empty.
     */
    public static synchronized boolean remove(Account a) {
        load();
        int i = indexOf(a);
        if (i < 0) return false;
        boolean wasActive = (i == activeIndex);
        accounts.remove(i);
        if (accounts.isEmpty()) activeIndex = -1;
        else if (i < activeIndex) activeIndex--;        // active shifted left by the removal
        else if (wasActive) activeIndex = 0;            // active removed → first remaining becomes active
        LOG.info("Removed account '{}' from roster (wasActive={})", a.username(), wasActive);
        persist();
        return wasActive;
    }

    /** Replaces the active entry after a silent token refresh. */
    public static synchronized void updateActive(Account refreshed) {
        load();
        if (activeIndex >= 0 && activeIndex < accounts.size()) {
            accounts.set(activeIndex, refreshed);
            persist();
        }
    }

    private AccountStore() {}
}
