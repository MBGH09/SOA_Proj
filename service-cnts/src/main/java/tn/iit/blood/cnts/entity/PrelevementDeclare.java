package tn.iit.blood.cnts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prelevement_declare")
@Getter @Setter @NoArgsConstructor
public class PrelevementDeclare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_don_national", nullable = false, unique = true, length = 30)
    private String numeroDonNational;

    @Column(name = "don_id_local", nullable = false, unique = true, length = 50)
    private String donIdLocal;

    @Column(name = "groupe_sanguin", nullable = false, length = 10)
    private String groupeSanguin;

    @Column(name = "volume_ml", nullable = false)
    private Integer volumeMl;

    @Column(name = "centre_id", nullable = false, length = 20)
    private String centreId;

    @Column(name = "date_prelevement", nullable = false)
    private LocalDate datePrelevement;

    @Column(nullable = false, length = 30)   // VARCHAR(30) — V4 migration widened from 20
    private String statut = "QUALIFICATION_EN_COURS";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
