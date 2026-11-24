package tn.iit.blood.service_don_stock;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.grpc.DonStockGrpcService;
import tn.iit.blood.service_don_stock.grpc.StockNiveauMapper;
import tn.iit.blood.service_don_stock.grpc.StockStreamRegistry;
import tn.iit.blood.service_don_stock.model.*;
import tn.iit.blood.service_don_stock.repository.*;
import tn.iit.blood.service_don_stock.soap.CntsWebServiceClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S4-13 — Unit tests for DonStockGrpcService.
 *
 * Pure unit tests: no Spring context, no database, no network.
 * All collaborators are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class DonStockGrpcServiceTest {

    @Mock LieuRepository            lieuRepo;
    @Mock StockNiveauRepository     stockRepo;
    @Mock ReservationRepository     reservationRepo;
    @Mock PrelevementRepository     prelevementRepo;
    @Mock StockStreamRegistry       streamRegistry;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CntsWebServiceClient      cntsClient;

    DonStockGrpcService service;

    @BeforeEach
    void setUp() {
        service = new DonStockGrpcService(
                lieuRepo, stockRepo, reservationRepo, prelevementRepo,
                streamRegistry, eventPublisher, cntsClient);
    }

    // ================================================================
    // VerifierEtReserver — validation
    // ================================================================

    @Test
    void verifierEtReserver_quantiteZero_invalidArgument() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-1")
                .setLieuId("lieu-chu-hbs")
                .setGroupeSanguin(GroupeSanguin.A_POS)
                .setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE)
                .setQuantite(0)
                .build();

        service.verifierEtReserver(req, obs);

        ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(cap.capture());
        assertThat(cap.getValue().getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void verifierEtReserver_unspecifiedGroupe_invalidArgument() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-2")
                .setLieuId("lieu-chu-hbs")
                .setGroupeSanguin(GroupeSanguin.GROUPE_SANGUIN_UNSPECIFIED)
                .setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE)
                .setQuantite(2)
                .build();

        service.verifierEtReserver(req, obs);

        ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(cap.capture());
        assertThat(cap.getValue().getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void verifierEtReserver_unknownLieu_notFound() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        when(lieuRepo.findById("unknown")).thenReturn(Optional.empty());

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-3")
                .setLieuId("unknown")
                .setGroupeSanguin(GroupeSanguin.A_POS)
                .setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE)
                .setQuantite(2)
                .build();

        service.verifierEtReserver(req, obs);

        ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(cap.capture());
        assertThat(cap.getValue().getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ================================================================
    // VerifierEtReserver — local path
    // ================================================================

    @Test
    void verifierEtReserver_localStockEnough_reserveeLocalement() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        Lieu lieu = lieu("lieu-chu-hbs", true);
        when(lieuRepo.findById("lieu-chu-hbs")).thenReturn(Optional.of(lieu));
        when(reservationRepo.findByDemandeId("d-local")).thenReturn(Optional.empty());

        StockNiveau stock = stock("lieu-chu-hbs", "A_POS", "CGR", 20, 0);
        when(stockRepo.findByKeyForUpdate("lieu-chu-hbs", "A_POS", "CGR"))
                .thenReturn(Optional.of(stock));

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-local")
                .setLieuId("lieu-chu-hbs")
                .setGroupeSanguin(GroupeSanguin.A_POS)
                .setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE)
                .setQuantite(5)
                .build();

        service.verifierEtReserver(req, obs);

        ArgumentCaptor<VerifierEtReserverResponse> cap =
                ArgumentCaptor.forClass(VerifierEtReserverResponse.class);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutReservation.RESERVEE_STOCK_LOCAL);
        // Stock decremented by 5, reserve incremented
        assertThat(stock.getDisponible()).isEqualTo(15);
        assertThat(stock.getReserve()).isEqualTo(5);
    }

    // ================================================================
    // VerifierEtReserver — idempotency
    // ================================================================

    @Test
    void verifierEtReserver_sameDemandeId_returnsStoredAnswer() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        Reservation existing = new Reservation();
        existing.setDemandeId("d-idem");
        existing.setStatut(StatutReservation.RESERVEE_STOCK_LOCAL.name());

        when(reservationRepo.findByDemandeId("d-idem")).thenReturn(Optional.of(existing));

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-idem")
                .setLieuId("lieu-chu-hbs")
                .setGroupeSanguin(GroupeSanguin.A_POS)
                .setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE)
                .setQuantite(2)
                .build();

        service.verifierEtReserver(req, obs);

        ArgumentCaptor<VerifierEtReserverResponse> cap =
                ArgumentCaptor.forClass(VerifierEtReserverResponse.class);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutReservation.RESERVEE_STOCK_LOCAL);
        // The lieu repo must NOT be called — idempotency short-circuits before it
        verify(lieuRepo, never()).findById(any());
    }

    // ================================================================
    // EnregistrerPrelevement — validation
    // ================================================================

    @Test
    void enregistrerPrelevement_wrongVolume_invalidArgument() {
        @SuppressWarnings("unchecked")
        StreamObserver<EnregistrerPrelevementResponse> obs = mock(StreamObserver.class);

        EnregistrerPrelevementRequest req = EnregistrerPrelevementRequest.newBuilder()
                .setDonId("don-1")
                .setDonneurId("donneur-1")
                .setCentreId("crts-sfax")
                .setGroupeSanguin(GroupeSanguin.O_NEG)
                .setVolumeMl(300) // wrong — must be 450
                .build();

        service.enregistrerPrelevement(req, obs);

        ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(cap.capture());
        assertThat(cap.getValue().getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ================================================================
    // EnregistrerPrelevement — idempotency
    // ================================================================

    @Test
    void enregistrerPrelevement_sameDonId_returnsStoredAnswer() {
        @SuppressWarnings("unchecked")
        StreamObserver<EnregistrerPrelevementResponse> obs = mock(StreamObserver.class);

        Prelevement existing = new Prelevement();
        existing.setDonId("don-idem");
        existing.setStatut("QUALIFICATION_EN_COURS");
        existing.setNumeroDonNational("DN-2026-000042");

        when(prelevementRepo.findByDonId("don-idem")).thenReturn(Optional.of(existing));

        EnregistrerPrelevementRequest req = EnregistrerPrelevementRequest.newBuilder()
                .setDonId("don-idem")
                .setDonneurId("donneur-1")
                .setCentreId("crts-sfax")
                .setGroupeSanguin(GroupeSanguin.O_NEG)
                .setVolumeMl(450)
                .build();

        service.enregistrerPrelevement(req, obs);

        ArgumentCaptor<EnregistrerPrelevementResponse> cap =
                ArgumentCaptor.forClass(EnregistrerPrelevementResponse.class);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        assertThat(cap.getValue().getNumeroDonNational()).isEqualTo("DN-2026-000042");
        // CNTS must not be called a second time
        verify(cntsClient, never()).enregistrerPrelevement(any(), any(), anyInt(), any(), any());
    }

    // ================================================================
    // SuivreNiveauxStock — subscriber registration and snapshot
    // ================================================================

    @Test
    void suivreNiveauxStock_registersSubscriberAndSendsSnapshot() {
        @SuppressWarnings("unchecked")
        StreamObserver<NiveauStockMessage> obs = mock(StreamObserver.class);

        StockNiveau s = stock("lieu-chu-hbs", "A_POS", "CGR", 25, 10);
        Lieu lieu = lieu("lieu-chu-hbs", true);
        when(stockRepo.findByFilters(null, null)).thenReturn(List.of(s));
        when(lieuRepo.findById("lieu-chu-hbs")).thenReturn(Optional.of(lieu));

        SuivreNiveauxStockRequest req = SuivreNiveauxStockRequest.newBuilder().build();
        service.suivreNiveauxStock(req, obs);

        verify(streamRegistry).register(eq(req), eq(obs));
        // One snapshot message was sent
        verify(obs, times(1)).onNext(any(NiveauStockMessage.class));
        // Stream stays open — onCompleted must NOT be called
        verify(obs, never()).onCompleted();
    }

    // ================================================================
    // StockNiveauMapper — niveau computation
    // ================================================================

    @ParameterizedTest(name = "disponible={0} seuil={1} → {2}")
    @CsvSource({
        "5,  10, BAS",
        "10, 10, BAS",
        "11, 10, MOYEN",
        "20, 10, MOYEN",
        "21, 10, HAUT",
        "0,  0,  HAUT"   // seuil=0 → always HAUT
    })
    void stockNiveauMapper_computeNiveau(int disponible, int seuil, String expectedName) {
        StockNiveau s = stock("l", "A_POS", "CGR", disponible, seuil);
        Niveau result = StockNiveauMapper.computeNiveau(s);
        assertThat(result.name()).isEqualTo(expectedName);
    }

    // ================================================================
    // ConsulterReservation — not found
    // ================================================================

    @Test
    void consulterReservation_unknown_notFound() {
        @SuppressWarnings("unchecked")
        StreamObserver<ConsulterReservationResponse> obs = mock(StreamObserver.class);
        when(reservationRepo.findByDemandeId("x")).thenReturn(Optional.empty());

        service.consulterReservation(
                ConsulterReservationRequest.newBuilder().setDemandeId("x").build(), obs);

        ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(cap.capture());
        assertThat(cap.getValue().getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ================================================================
    // Factory helpers
    // ================================================================

    private Lieu lieu(String id, boolean stockLocal) {
        Lieu l = new Lieu();
        l.setId(id);
        l.setNom(id);
        l.setType("HOPITAL");
        l.setRegion("Sfax");
        l.setStockLocal(stockLocal);
        return l;
    }

    private StockNiveau stock(String lieuId, String groupe, String composant,
                              int disponible, int seuilAlerte) {
        StockNiveau s = new StockNiveau();
        s.setLieuId(lieuId);
        s.setGroupeSanguin(groupe);
        s.setComposant(composant);
        s.setDisponible(disponible);
        s.setReserve(0);
        s.setEnQualification(0);
        s.setEnTransit(0);
        s.setSeuilAlerte(seuilAlerte);
        return s;
    }
}
