package tn.iit.blood.gateway.security;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Compteur de debit en memoire, par cle (adresse IP), a fenetre fixe (S2-09).
 *
 * <p>Chaque cle dispose d'un compteur reinitialise au debut de chaque fenetre
 * de 60 secondes. Le calcul est atomique par cle ({@code ConcurrentHashMap
 * .compute}) : deux requetes simultanees de la meme IP ne peuvent pas perdre
 * d'increment.</p>
 *
 * <p><strong>Limites connues :</strong></p>
 * <ul>
 *   <li>le compteur vit dans la memoire de l'instance : il n'est pas partage
 *       entre plusieurs instances du gateway ;</li>
 *   <li>la fenetre est fixe : un client peut envoyer jusqu'a deux fois la
 *       limite autour d'une frontiere de minute ;</li>
 *   <li>la carte est purgee des fenetres expirees, mais elle peut contenir
 *       autant de cles que d'IP distinctes vues pendant une meme fenetre.</li>
 * </ul>
 */
public final class RateLimitCounter {

    /** Duree d'une fenetre : 60 secondes. */
    static final long WINDOW_MILLIS = 60_000L;

    /** Nombre de cles au-dela duquel on purge les fenetres expirees. */
    static final int DEFAULT_MAX_TRACKED_KEYS = 10_000;

    private final int limitPerWindow;

    private final int maxTrackedKeys;

    /** Horloge injectable : les tests unitaires ne dependent pas du temps reel. */
    private final LongSupplier clock;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Derniere fenetre ou une purge a eu lieu (au plus une purge par fenetre). */
    private final AtomicLong lastPurgedWindow = new AtomicLong(Long.MIN_VALUE);

    /** Compteur de production : horloge systeme et seuil par defaut. */
    public RateLimitCounter(int limitPerWindow) {
        this(limitPerWindow, DEFAULT_MAX_TRACKED_KEYS, System::currentTimeMillis);
    }

    /** Constructeur utilise par les tests unitaires (horloge et seuil controles). */
    RateLimitCounter(int limitPerWindow, int maxTrackedKeys, LongSupplier clock) {
        if (limitPerWindow < 1) {
            throw new IllegalArgumentException(
                    "La limite par minute doit etre superieure ou egale a 1.");
        }
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException(
                    "Le nombre maximal de cles suivies doit etre superieur ou egal a 1.");
        }
        this.limitPerWindow = limitPerWindow;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Compte une requete pour la cle donnee.
     *
     * @param key adresse IP de l'appelant
     * @return la decision (autorisee ou non) et, si refusee, le delai
     *         {@code Retry-After} en secondes
     */
    public Decision tryAcquire(String key) {
        Objects.requireNonNull(key, "key");
        long now = clock.getAsLong();
        long windowIndex = now / WINDOW_MILLIS;

        purgeExpiredWindows(windowIndex);

        Window updated = windows.compute(key, (ignored, current) ->
                current == null || current.index() != windowIndex
                        ? new Window(windowIndex, 1)
                        : new Window(windowIndex, current.count() + 1));

        if (updated.count() <= limitPerWindow) {
            return new Decision(true, 0L);
        }

        long remainingMillis = WINDOW_MILLIS - (now % WINDOW_MILLIS);
        long retryAfterSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        return new Decision(false, retryAfterSeconds);
    }

    /**
     * Purge les fenetres expirees lorsque la carte depasse son seuil.
     *
     * <p>La purge est tentee au plus une fois par fenetre pour eviter de
     * parcourir la carte a chaque requete. Les entrees de la fenetre courante
     * sont conservees : un afflux d'IP distinctes dans une meme minute peut
     * donc depasser temporairement le seuil, sans croissance durable.</p>
     */
    private void purgeExpiredWindows(long windowIndex) {
        if (windows.size() <= maxTrackedKeys) {
            return;
        }
        long previous = lastPurgedWindow.get();
        if (previous == windowIndex) {
            return;
        }
        if (lastPurgedWindow.compareAndSet(previous, windowIndex)) {
            windows.entrySet().removeIf(entry -> entry.getValue().index() < windowIndex);
        }
    }

    /** Limite configuree, en requetes par fenetre. */
    public int limitPerWindow() {
        return limitPerWindow;
    }

    /**
     * Reinitialise tous les compteurs.
     *
     * <p>Reserve aux tests automatises et a une remise a zero administrative
     * locale : aucun endpoint HTTP n'expose cette operation.</p>
     */
    public void reset() {
        windows.clear();
        lastPurgedWindow.set(Long.MIN_VALUE);
    }

    /** Nombre de cles actuellement suivies (tests unitaires). */
    int trackedKeys() {
        return windows.size();
    }

    /** Indique si une cle est encore suivie (tests unitaires). */
    boolean isTracked(String key) {
        return windows.containsKey(key);
    }

    /** Resultat d'une tentative de consommation du quota. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /** Etat d'une cle dans une fenetre donnee. */
    private record Window(long index, int count) {
    }
}
