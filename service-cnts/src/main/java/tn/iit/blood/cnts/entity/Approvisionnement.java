package tn.iit.blood.cnts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "approvisionnement")
@Getter @Setter @NoArgsConstructor
public class Approvisionnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    @Column(name = "demande_id", nullable = false, unique = true, length = 50)
    private String demandeId;

    @Column(name = "hopital_id", nullable = false, length = 50)
    private String hopitalId;

    @Column(name = "groupe_sanguin", nullable = false, length = 10)
    private String groupeSanguin;

    @Column(nullable = false, length = 20)
    private String composant;

    @Column(name = "quantite_demandee", nullable = false)
    private Integer quantiteDemandee;

    @Column(name = "quantite_accordee", nullable = false)
    private Integer quantiteAccordee = 0;

    @Column(name = "centre_source", length = 20)
    private String centreSource;

    @Column(name = "delai_minutes")
    private Integer delaiMinutes;

    @Column(nullable = false, length = 20)
    private String statut = "EN_COURS";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
