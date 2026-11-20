package tn.iit.blood.service_don_stock.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Idempotency key – unique per hospital request */
    @Column(name = "demande_id", nullable = false, unique = true)
    private String demandeId;

    @Column(name = "lieu_id", nullable = false)
    private String lieuId;

    @Column(name = "groupe_sanguin", nullable = false)
    private String groupeSanguin;

    @Column(nullable = false)
    private String composant;

    @Column(nullable = false)
    private int quantite;

    /** RESERVEE_STOCK_LOCAL | APPROVISIONNEMENT_COMMANDE | STOCK_INSUFFISANT_PARTOUT | A_CONFIRMER | LIVREE */
    @Column(nullable = false)
    private String statut;

    private String reference;

    @Column(name = "centre_source")
    private String centreSource;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public Reservation() {}

    // ---- getters / setters ----
    public Long    getId()           { return id; }
    public String  getDemandeId()    { return demandeId; }
    public void    setDemandeId(String d) { this.demandeId = d; }
    public String  getLieuId()       { return lieuId; }
    public void    setLieuId(String l) { this.lieuId = l; }
    public String  getGroupeSanguin() { return groupeSanguin; }
    public void    setGroupeSanguin(String g) { this.groupeSanguin = g; }
    public String  getComposant()    { return composant; }
    public void    setComposant(String c) { this.composant = c; }
    public int     getQuantite()     { return quantite; }
    public void    setQuantite(int q) { this.quantite = q; }
    public String  getStatut()       { return statut; }
    public void    setStatut(String s) { this.statut = s; }
    public String  getReference()    { return reference; }
    public void    setReference(String r) { this.reference = r; }
    public String  getCentreSource() { return centreSource; }
    public void    setCentreSource(String cs) { this.centreSource = cs; }
    public Integer getEtaMinutes()   { return etaMinutes; }
    public void    setEtaMinutes(Integer e) { this.etaMinutes = e; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }
}
