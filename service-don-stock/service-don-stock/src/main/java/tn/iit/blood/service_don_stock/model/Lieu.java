package tn.iit.blood.service_don_stock.model;

import jakarta.persistence.*;

@Entity
@Table(name = "lieu")
public class Lieu {

    @Id
    private String id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String type;

    private String region;

    @Column(name = "stock_local", nullable = false)
    private boolean stockLocal;

    public Lieu() {}

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getNom()            { return nom; }
    public void   setNom(String nom)  { this.nom = nom; }

    public String getType()             { return type; }
    public void   setType(String type)  { this.type = type; }

    public String getRegion()               { return region; }
    public void   setRegion(String region)  { this.region = region; }

    public boolean isStockLocal()                   { return stockLocal; }
    public void    setStockLocal(boolean stockLocal) { this.stockLocal = stockLocal; }
}
