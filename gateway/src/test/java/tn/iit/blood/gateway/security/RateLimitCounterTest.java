package tn.iit.blood.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires du compteur de debit S2-09.
 *
 * <p>L'horloge est injectee : aucun test n'attend reellement 60 secondes.
 * Les tests portent sur la limite exacte, la reinitialisation de fenetre,
 * l'independance des cles, le calcul de {@code Retry-After}, la purge des
 * fenetres expirees et la remise a zero.</p>
 */
class RateLimitCounterTest {

    /** Instant aligne sur le debut d'une fenetre de 60 secondes. */
    private static final long START = 1_700_000_040_000L;

    /** Horloge controlee, avancee explicitement par chaque test. */
    private final AtomicLong now = new AtomicLong(START);

    private final LongSupplier clock = now::get;

    @Test
    void allowsExactlyTheConfiguredNumberOfRequestsThenRejects() {
        RateLimitCounter counter = new RateLimitCounter(3, 10_000, clock);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();

        RateLimitCounter.Decision rejected = counter.tryAcquire("10.0.0.1");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(counter.limitPerWindow()).isEqualTo(3);
    }

    @Test
    void resetsTheCounterAtTheNextWindow() {
        RateLimitCounter counter = new RateLimitCounter(2, 10_000, clock);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isFalse();

        // Fenetre suivante : le compteur repart de zero.
        now.addAndGet(RateLimitCounter.WINDOW_MILLIS);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isFalse();
    }

    @Test
    void countsEachKeyIndependently() {
        RateLimitCounter counter = new RateLimitCounter(1, 10_000, clock);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isFalse();

        // Une autre IP dispose de son propre quota.
        assertThat(counter.tryAcquire("10.0.0.2").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.2").allowed()).isFalse();
    }

    @Test
    void retryAfterIsTheRemainingTimeInTheWindow() {
        // 5 secondes apres le debut de la fenetre : 55 secondes restantes.
        now.set(START + 5_000L);
        RateLimitCounter counter = new RateLimitCounter(1, 10_000, clock);
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").retryAfterSeconds()).isEqualTo(55L);

        // Derniere seconde : arrondi superieur, jamais zero.
        now.set(START + 59_500L);
        assertThat(counter.tryAcquire("10.0.0.1").retryAfterSeconds()).isEqualTo(1L);
    }

    @Test
    void purgesExpiredWindowsWhenTheMapExceedsItsThreshold() {
        // Seuil 2 : la purge est declenchee par la 4e cle (2e de la nouvelle
        // fenetre) et supprime les deux cles de la fenetre precedente.
        RateLimitCounter counter = new RateLimitCounter(10, 2, clock);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.2").allowed()).isTrue();
        assertThat(counter.trackedKeys()).isEqualTo(2);

        now.addAndGet(RateLimitCounter.WINDOW_MILLIS);

        assertThat(counter.tryAcquire("10.0.0.3").allowed()).isTrue();
        // La purge n'a pas encore eu lieu : la carte vient juste de depasser
        // le seuil, mais le parcours est tente a la requete suivante.
        assertThat(counter.tryAcquire("10.0.0.4").allowed()).isTrue();

        assertThat(counter.isTracked("10.0.0.1")).isFalse();
        assertThat(counter.isTracked("10.0.0.2")).isFalse();
        assertThat(counter.isTracked("10.0.0.3")).isTrue();
        assertThat(counter.isTracked("10.0.0.4")).isTrue();
        assertThat(counter.trackedKeys()).isEqualTo(2);
    }

    @Test
    void doesNotPurgeEntriesOfTheCurrentWindow() {
        // Toutes les cles appartiennent a la fenetre courante : la purge ne
        // doit rien supprimer, meme au-dela du seuil.
        RateLimitCounter counter = new RateLimitCounter(10, 1, clock);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.2").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.3").allowed()).isTrue();

        assertThat(counter.trackedKeys()).isEqualTo(3);
        assertThat(counter.isTracked("10.0.0.1")).isTrue();
        assertThat(counter.isTracked("10.0.0.2")).isTrue();
        assertThat(counter.isTracked("10.0.0.3")).isTrue();
    }

    @Test
    void resetClearsEveryCounter() {
        RateLimitCounter counter = new RateLimitCounter(1, 10_000, clock);

        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isFalse();

        counter.reset();

        assertThat(counter.trackedKeys()).isZero();
        assertThat(counter.tryAcquire("10.0.0.1").allowed()).isTrue();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RateLimitCounter(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite par minute");

        assertThatThrownBy(() -> new RateLimitCounter(1, 0, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre maximal de cles");

        assertThatThrownBy(() -> new RateLimitCounter(1, 10, null))
                .isInstanceOf(NullPointerException.class);
    }
}
