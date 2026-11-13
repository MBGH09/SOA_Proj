package tn.iit.blood.service_don_stock.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "prelevement")
public class Prelevement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Idempotency key */
    @Column(name = "don_id", nullable = false, unique = true)
    private String donId;

    @Column(name = "donneur_id", nullable = false)
    private String donneurId;

    @Column(name = "centre_id", nullable = false)
    private String centreId;

    @Column(name = "groupe_sanguin", nullable = false)
    private String groupeSanguin;

    @Column(name = "volume_ml", nullable = false)
    private int volumeMl;

    /**
     * A_DECLARER → QUALIFICATION_EN_COURS → QUALIFIE
     */
    @Column(nullable = false)
    private String statut = "A_DECLARER";

    @Column(name = "numero_don_national")
    private String numeroDonNational;

    @Column(name = "numero_certificat")
    private String numeroCertificat;

    @Column(name = "date_certificat")
    private LocalDate dateCertificat;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public Prelevement() {}

    // ---- getters / setters ----
    public Long      getId()                { return id; }
    public String    getDonId()             { return donId; }
    public void      setDonId(String d)     { this.donId = d; }
    public String    getDonneurId()         { return donneurId; }
    public void      setDonneurId(String d) { this.donneurId = d; }
    public String    getCentreId()          { return centreId; }
    public void      setCentreId(String c)  { this.centreId = c; }
    public String    getGroupeSanguin()     { return groupeSanguin; }
    public void      setGroupeSanguin(String g) { this.groupeSanguin = g; }
    public int       getVolumeMl()          { return volumeMl; }
    public void      setVolumeMl(int v)     { this.volumeMl = v; }
    public String    getStatut()            { return statut; }
    public void      setStatut(String s)    { this.statut = s; }
    public String    getNumeroDonNational() { return numeroDonNational; }
    public void      setNumeroDonNational(String n) { this.numeroDonNational = n; }
    public String    getNumeroCertificat()  { return numeroCertificat; }
    public void      setNumeroCertificat(String n)  { this.numeroCertificat = n; }
    public LocalDate getDateCertificat()    { return dateCertificat; }
    public void      setDateCertificat(LocalDate d) { this.dateCertificat = d; }
    public Instant   getCreatedAt()         { return createdAt; }
    public Instant   getUpdatedAt()         { return updatedAt; }
}
