package tn.iit.blood.cnts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificat")
@Getter @Setter @NoArgsConstructor
public class Certificat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_certificat", nullable = false, unique = true, length = 30)
    private String numeroCertificat;

    @Column(name = "numero_don_national", nullable = false, length = 30)
    private String numeroDonNational;

    @Column(name = "don_id_local", nullable = false, unique = true, length = 50)
    private String donIdLocal;

    @Column(name = "centre_id", nullable = false, length = 20)
    private String centreId;

    @Column(name = "date_don", nullable = false)
    private LocalDate dateDon;

    @Column(name = "date_emission", nullable = false)
    private LocalDate dateEmission = LocalDate.now();

    @Column(nullable = false, length = 20)
    private String statut = "VALIDE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
