package tn.iit.blood.cnts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_central")
@Getter @Setter @NoArgsConstructor
public class StockCentral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "centre_id", nullable = false, length = 20)
    private String centreId;

    @Column(name = "groupe_sanguin", nullable = false, length = 10)
    private String groupeSanguin;

    @Column(nullable = false, length = 20)
    private String composant;

    @Column(nullable = false)
    private Integer quantite = 0;

    @Column(name = "mise_a_jour", nullable = false)
    private LocalDateTime miseAJour = LocalDateTime.now();
}
