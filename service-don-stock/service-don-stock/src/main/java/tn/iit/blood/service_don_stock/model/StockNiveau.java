package tn.iit.blood.service_don_stock.model;

import jakarta.persistence.*;

@Entity
@Table(name = "stock_niveau")
@IdClass(StockNiveauId.class)
public class StockNiveau {

    @Id
    @Column(name = "lieu_id")
    private String lieuId;

    @Id
    @Column(name = "groupe_sanguin")
    private String groupeSanguin;

    @Id
    @Column(name = "composant")
    private String composant;

    @Column(nullable = false)
    private int disponible;

    @Column(nullable = false)
    private int reserve;

    @Column(name = "en_qualification", nullable = false)
    private int enQualification;

    @Column(name = "en_transit", nullable = false)
    private int enTransit;

    @Column(name = "seuil_alerte", nullable = false)
    private int seuilAlerte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id", insertable = false, updatable = false)
    private Lieu lieu;

    public StockNiveau() {}

    // ---- getters / setters ----
    public String getLieuId()               { return lieuId; }
    public void   setLieuId(String lieuId)  { this.lieuId = lieuId; }

    public String getGroupeSanguin()                      { return groupeSanguin; }
    public void   setGroupeSanguin(String groupeSanguin)  { this.groupeSanguin = groupeSanguin; }

    public String getComposant()                  { return composant; }
    public void   setComposant(String composant)  { this.composant = composant; }

    public int  getDisponible()                 { return disponible; }
    public void setDisponible(int disponible)   { this.disponible = disponible; }

    public int  getReserve()              { return reserve; }
    public void setReserve(int reserve)   { this.reserve = reserve; }

    public int  getEnQualification()                      { return enQualification; }
    public void setEnQualification(int enQualification)   { this.enQualification = enQualification; }

    public int  getEnTransit()                { return enTransit; }
    public void setEnTransit(int enTransit)   { this.enTransit = enTransit; }

    public int  getSeuilAlerte()                  { return seuilAlerte; }
    public void setSeuilAlerte(int seuilAlerte)   { this.seuilAlerte = seuilAlerte; }

    public Lieu getLieu() { return lieu; }
}
