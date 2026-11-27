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
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.donstock.soap.client.DelivrerCertificatDonResponse;
import tn.iit.blood.donstock.soap.client.DemanderApprovisionnementResponse;
import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.grpc.DonStockGrpcService;
import tn.iit.blood.service_don_stock.grpc.StockNiveauMapper;
import tn.iit.blood.service_don_stock.grpc.StockStreamRegistry;
import tn.iit.blood.service_don_stock.model.*;
import tn.iit.blood.service_don_stock.repository.*;
import tn.iit.blood.service_don_stock.scheduler.RetryConfirmerJob;
import tn.iit.blood.service_don_stock.service.CentreResolver;
import tn.iit.blood.service_don_stock.soap.*;

import javax.xml.datatype.DatatypeFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    @Mock CentreResolver            centreResolver;

    DonStockGrpcService service;

    @BeforeEach
    void setUp() {
        service = new DonStockGrpcService(
                lieuRepo, stockRepo, reservationRepo, prelevementRepo,
                streamRegistry, eventPublisher, cntsClient, centreResolver);
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
    // Central path — SOAP result handling and transaction boundaries
    // ================================================================

    @Test
    void verifierEtReserver_centralSuccess_ordersViaRegionAndIncreasesTransit() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        when(reservationRepo.findByDemandeId("d-central")).thenReturn(Optional.empty());
        when(lieuRepo.findById("lieu-clinique-sfax"))
                .thenReturn(Optional.of(lieu("lieu-clinique-sfax", false)));
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        DemanderApprovisionnementResponse soapResp = new DemanderApprovisionnementResponse();
        soapResp.setReference("REF-1");
        soapResp.setCentreSource("CRTS-SFAX");
        soapResp.setDelaiMinutes(45);
        soapResp.setQuantiteAccordee(3);
        soapResp.setStatut("ACCORDE");
        when(cntsClient.demanderApprovisionnement(
                anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString())).thenReturn(soapResp);

        StockNiveau stock = stock("lieu-clinique-sfax", "A_POS", "CGR", 0, 10);
        when(stockRepo.findByKeyForUpdate("lieu-clinique-sfax", "A_POS", "CGR"))
                .thenReturn(Optional.of(stock));

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-central")
                .setLieuId("lieu-clinique-sfax")
                .setGroupeSanguin(GroupeSanguin.A_POS)
                .setComposant(Composant.CGR)
                .setUrgence(Urgence.HAUTE)
                .setQuantite(3)
                .build();

        service.verifierEtReserver(req, obs);

        ArgumentCaptor<VerifierEtReserverResponse> cap =
                ArgumentCaptor.forClass(VerifierEtReserverResponse.class);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutReservation.APPROVISIONNEMENT_COMMANDE);
        assertThat(cap.getValue().getReference()).isEqualTo("REF-1");
        assertThat(cap.getValue().getCentreSource()).isEqualTo("CRTS-SFAX");
        assertThat(cap.getValue().getEtaMinutes()).isEqualTo(45);
        assertThat(stock.getEnTransit()).isEqualTo(3);

        // The region of the place is sent so CNTS can try the regional CRTS first.
        verify(cntsClient).demanderApprovisionnement(
                eq("lieu-clinique-sfax"), eq("Sfax"), eq("A_POS"), eq("CGR"),
                eq(3), eq("HAUTE"), eq("d-central"));
    }

    @Test
    void verifierEtReserver_stockInsuffisant_returnsShortageStatus() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        when(reservationRepo.findByDemandeId("d-short")).thenReturn(Optional.empty());
        when(lieuRepo.findById("lieu-clinique-sfax"))
                .thenReturn(Optional.of(lieu("lieu-clinique-sfax", false)));
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(cntsClient.demanderApprovisionnement(
                anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString()))
                .thenThrow(new StockInsuffisantException("no stock anywhere"));

        service.verifierEtReserver(VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-short").setLieuId("lieu-clinique-sfax")
                .setGroupeSanguin(GroupeSanguin.A_POS).setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE).setQuantite(9).build(), obs);

        ArgumentCaptor<VerifierEtReserverResponse> cap =
                ArgumentCaptor.forClass(VerifierEtReserverResponse.class);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutReservation.STOCK_INSUFFISANT_PARTOUT);
    }

    @Test
    void verifierEtReserver_cntsDown_statusIsAwaitingConfirmation() {
        @SuppressWarnings("unchecked")
        StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);

        when(reservationRepo.findByDemandeId("d-down")).thenReturn(Optional.empty());
        when(lieuRepo.findById("lieu-clinique-sfax"))
                .thenReturn(Optional.of(lieu("lieu-clinique-sfax", false)));
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(cntsClient.demanderApprovisionnement(
                anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        service.verifierEtReserver(VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-down").setLieuId("lieu-clinique-sfax")
                .setGroupeSanguin(GroupeSanguin.A_POS).setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE).setQuantite(2).build(), obs);

        ArgumentCaptor<StatusRuntimeException> errCap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(errCap.capture());
        assertThat(errCap.getValue().getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);

        ArgumentCaptor<Reservation> resCap = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepo, atLeastOnce()).save(resCap.capture());
        assertThat(resCap.getAllValues().get(0).getStatut())
                .isEqualTo(ReservationStatuts.EN_ATTENTE_CONFIRMATION);
    }

    @Test
    void consulterReservation_legacyStatus_mapsToEnAttenteConfirmation() {
        @SuppressWarnings("unchecked")
        StreamObserver<ConsulterReservationResponse> obs = mock(StreamObserver.class);

        Reservation r = new Reservation();
        r.setDemandeId("d-legacy");
        r.setStatut("A_CONFIRMER");
        when(reservationRepo.findByDemandeId("d-legacy")).thenReturn(Optional.of(r));

        service.consulterReservation(
                ConsulterReservationRequest.newBuilder().setDemandeId("d-legacy").build(), obs);

        ArgumentCaptor<ConsulterReservationResponse> cap =
                ArgumentCaptor.forClass(ConsulterReservationResponse.class);
        verify(obs).onNext(cap.capture());
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE_CONFIRMATION);
    }

    // ================================================================
    // Prelevement — request dates and mobile → regional CRTS
    // ================================================================

    @Test
    void enregistrerPrelevement_mobileCollection_usesRequestDateAndRegionalCrts() {
        @SuppressWarnings("unchecked")
        StreamObserver<EnregistrerPrelevementResponse> obs = mock(StreamObserver.class);

        when(prelevementRepo.findByDonId("don-mobile")).thenReturn(Optional.empty());
        when(prelevementRepo.save(any(Prelevement.class))).thenAnswer(i -> i.getArgument(0));
        when(centreResolver.resolveStockCentreId("lieu-mobile-crts-sfax"))
                .thenReturn("lieu-crts-sfax");

        tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse soapResp =
                new tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse();
        soapResp.setNumeroDonNational("DN-2026-0001");
        soapResp.setStatut("QUALIFICATION_EN_COURS");
        when(cntsClient.enregistrerPrelevement(
                anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(soapResp);

        StockNiveau crtsStock = stock("lieu-crts-sfax", "O_NEG", "CGR", 10, 10);
        when(stockRepo.findByKeyForUpdate(eq("lieu-crts-sfax"), eq("O_NEG"), anyString()))
                .thenReturn(Optional.of(crtsStock));

        service.enregistrerPrelevement(EnregistrerPrelevementRequest.newBuilder()
                .setDonId("don-mobile").setDonneurId("donneur-1")
                .setCentreId("lieu-mobile-crts-sfax").setGroupeSanguin(GroupeSanguin.O_NEG)
                .setVolumeMl(450).setDatePrelevement("2026-11-20").build(), obs);

        ArgumentCaptor<LocalDate> dateCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(cntsClient).enregistrerPrelevement(
                eq("don-mobile"), eq("O_NEG"), eq(450), eq("lieu-crts-sfax"), dateCap.capture());
        assertThat(dateCap.getValue()).isEqualTo(LocalDate.of(2026, 11, 20));

        ArgumentCaptor<EnregistrerPrelevementResponse> respCap =
                ArgumentCaptor.forClass(EnregistrerPrelevementResponse.class);
        verify(obs).onNext(respCap.capture());
        assertThat(respCap.getValue().getNumeroDonNational()).isEqualTo("DN-2026-0001");
        assertThat(crtsStock.getEnQualification()).isEqualTo(3);
        // No stock row is ever created/updated at the mobile collecting place.
        verify(stockRepo, never()).findByKeyForUpdate(eq("lieu-mobile-crts-sfax"), anyString(), anyString());
    }

    @Test
    void delivrerCertificat_usesRequestDateAndForwardsCrts() throws Exception {
        @SuppressWarnings("unchecked")
        StreamObserver<DelivrerCertificatResponse> obs = mock(StreamObserver.class);

        Prelevement p = new Prelevement();
        p.setDonId("don-cert");
        p.setCentreId("lieu-mobile-crts-sfax");
        p.setGroupeSanguin("A_POS");
        p.setNumeroDonNational("DN-2026-0002");
        when(prelevementRepo.findByDonId("don-cert")).thenReturn(Optional.of(p));
        when(centreResolver.resolveStockCentreId("lieu-mobile-crts-sfax"))
                .thenReturn("lieu-crts-sfax");

        DelivrerCertificatDonResponse soapResp = new DelivrerCertificatDonResponse();
        soapResp.setNumeroCertificat("CERT-2026-0001");
        soapResp.setDateEmission(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-11-23"));
        soapResp.setStatut("VALIDE");
        when(cntsClient.delivrerCertificatDon(
                anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(soapResp);

        service.delivrerCertificat(DelivrerCertificatRequest.newBuilder()
                .setDonId("don-cert").setNumeroDonNational("DN-2026-0002")
                .setDonneurRef("donneur-1").setCentreId("lieu-mobile-crts-sfax")
                .setDateDon("2026-11-21").build(), obs);

        ArgumentCaptor<LocalDate> dateCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(cntsClient).delivrerCertificatDon(
                eq("don-cert"), eq("DN-2026-0002"), eq("donneur-1"),
                eq("lieu-crts-sfax"), dateCap.capture());
        assertThat(dateCap.getValue()).isEqualTo(LocalDate.of(2026, 11, 21));

        ArgumentCaptor<DelivrerCertificatResponse> respCap =
                ArgumentCaptor.forClass(DelivrerCertificatResponse.class);
        verify(obs).onNext(respCap.capture());
        assertThat(respCap.getValue().getNumeroCertificat()).isEqualTo("CERT-2026-0001");
        assertThat(respCap.getValue().getDateEmission()).isEqualTo("2026-11-23");
    }

    @Test
    void delivrerCertificat_failedPrecondition_whenNoNationalNumber() {
        @SuppressWarnings("unchecked")
        StreamObserver<DelivrerCertificatResponse> obs = mock(StreamObserver.class);

        Prelevement p = new Prelevement();
        p.setDonId("don-unnumbered");
        p.setCentreId("lieu-crts-sfax");
        when(prelevementRepo.findByDonId("don-unnumbered")).thenReturn(Optional.of(p));

        service.delivrerCertificat(DelivrerCertificatRequest.newBuilder()
                .setDonId("don-unnumbered").setDateDon("2026-11-21").build(), obs);

        ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(obs).onError(cap.capture());
        assertThat(cap.getValue().getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        verifyNoInteractions(cntsClient);
    }

    // ================================================================
    // Stream contract and concurrency / idempotency
    // ================================================================

    @Test
    void stockNiveauMapper_sendsStudyFields() {
        StockNiveau s = stock("lieu-chu-hbs", "A_POS", "CGR", 15, 10);
        NiveauStockMessage msg = StockNiveauMapper.toProto(s, lieu("lieu-chu-hbs", true));

        assertThat(msg.getQuantite()).isEqualTo(15);
        assertThat(msg.getSeuilAlerte()).isEqualTo(10);
        assertThat(msg.getNiveau()).isEqualTo(Niveau.MOYEN);
        assertThat(msg.getTimestamp()).isNotBlank();
    }

    @Test
    void soapFlows_haveNoDeclaredTransaction() throws Exception {
        assertThat(DonStockGrpcService.class
                .getMethod("verifierEtReserver", VerifierEtReserverRequest.class, StreamObserver.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(DonStockGrpcService.class
                .getMethod("enregistrerPrelevement", EnregistrerPrelevementRequest.class, StreamObserver.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(DonStockGrpcService.class
                .getMethod("delivrerCertificat", DelivrerCertificatRequest.class, StreamObserver.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(RetryConfirmerJob.class.getMethod("run")
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void verifierEtReserver_concurrentSameDemandeId_isIdempotent() throws Exception {
        ConcurrentHashMap<String, Reservation> store = new ConcurrentHashMap<>();
        when(reservationRepo.findByDemandeId(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            Reservation fresh = new Reservation();
            fresh.setDemandeId(id);
            fresh.setStatut(StatutReservation.RESERVEE_STOCK_LOCAL.name());
            // First caller sees an empty Optional while the row is now reserved;
            // every later caller sees the stored answer.
            return Optional.ofNullable(store.putIfAbsent(id, fresh));
        });
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            store.putIfAbsent(r.getDemandeId(), r);
            return store.get(r.getDemandeId());
        });
        when(lieuRepo.findById("lieu-chu-hbs")).thenReturn(Optional.of(lieu("lieu-chu-hbs", true)));
        StockNiveau stock = stock("lieu-chu-hbs", "A_POS", "CGR", 20, 10);
        when(stockRepo.findByKeyForUpdate("lieu-chu-hbs", "A_POS", "CGR"))
                .thenReturn(Optional.of(stock));

        VerifierEtReserverRequest req = VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-race").setLieuId("lieu-chu-hbs")
                .setGroupeSanguin(GroupeSanguin.A_POS).setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE).setQuantite(1).build();

        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch done = new CountDownLatch(callers);
        List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    StreamObserver<VerifierEtReserverResponse> obs = mock(StreamObserver.class);
                    service.verifierEtReserver(req, obs);
                    verify(obs).onCompleted();
                } catch (Throwable t) {
                    synchronized (errors) { errors.add(t); }
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(errors).isEmpty();
        verify(stockRepo, times(1)).findByKeyForUpdate("lieu-chu-hbs", "A_POS", "CGR");
        assertThat(stock.getDisponible()).isEqualTo(19);
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
