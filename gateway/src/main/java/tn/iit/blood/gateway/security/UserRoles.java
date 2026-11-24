package tn.iit.blood.gateway.security;

/**
 * Noms officiels des roles (S2-07 et S2-08).
 *
 * <p>L'enonce fixe quatre roles : {@code DONOR}, {@code STAFF_CENTRE},
 * {@code HOPITAL} et {@code ADMIN}. Le claim JWT {@code role} porte l'un de ces
 * noms ; le converter de {@link SecurityConfiguration} en derive l'autorite
 * Spring Security {@code ROLE_<role>}.</p>
 *
 * <p>Ces constantes evitent de disperser des chaines de caracteres dans les
 * regles d'autorisation et dans les utilisateurs de demonstration.</p>
 */
public final class UserRoles {

    /** Donneur : accede a ses propres donnees et prend ses rendez-vous. */
    public static final String DONOR = "DONOR";

    /** Personnel d'un centre : entretien, prelevement, certificats. */
    public static final String STAFF_CENTRE = "STAFF_CENTRE";

    /** Hopital : demandes de sang et compensation. */
    public static final String HOPITAL = "HOPITAL";

    /** Administrateur : toutes les operations metier. */
    public static final String ADMIN = "ADMIN";

    private UserRoles() {
    }
}
