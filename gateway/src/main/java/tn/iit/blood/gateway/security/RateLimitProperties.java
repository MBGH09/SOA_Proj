package tn.iit.blood.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de la limitation de debit (S2-09).
 *
 * <p>{@code gateway.rate-limit.enabled} active le filtre. Lorsqu'il est actif,
 * {@code per-minute} fixe le nombre maximal de requetes autorisees par adresse
 * IP dans une fenetre fixe de 60 secondes. La valeur est fournie par la
 * variable {@code RATE_LIMIT_PER_MIN} (120 par defaut).</p>
 *
 * <p>La limitation est active par defaut en production. Les ressources de test
 * la desactivent globalement ({@code gateway.rate-limit.enabled=false}) pour
 * ne pas modifier le comportement des tests S2-02 a S2-08 ; les tests S2-09
 * l'activent explicitement avec une limite basse.</p>
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /** Active le filtre de limitation (true par defaut). */
    private boolean enabled = true;

    /** Requetes autorisees par adresse IP et par fenetre de 60 secondes. */
    private int perMinute = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPerMinute() {
        return perMinute;
    }

    public void setPerMinute(int perMinute) {
        this.perMinute = perMinute;
    }
}
