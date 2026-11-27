package tn.iit.blood.service_don_stock;

import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tn.iit.blood.donstock.soap.client.DelivrerCertificatDonResponse;
import tn.iit.blood.donstock.soap.client.DemanderApprovisionnementResponse;
import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.grpc.DonStockGrpcService;
import tn.iit.blood.service_don_stock.grpc.StockNiveauMapper;
import tn.iit.blood.service_don_stock.grpc.StockStreamRegistry;
import tn.iit.blood.service_don_stock.model.*;
import tn.iit.blood.service_don_stock.repository.*;
import tn.iit.blood.service_don_stock.service.CentreResolver;
import tn.iit.blood.service_don_stock.soap.*;

import javax.xml.datatype.DatatypeFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S4-13 — In-process gRPC tests.
 *
 * A real gRPC server and channel are started in-process (no socket, no Spring
 * context).  Every RPC and every status code is exercised through the generated
 * stubs.  The stream test receives a snapshot, two changes, then cancels.
 */
@ExtendWith(MockitoExtension.class)
class DonStockGrpcInProcessTest {

    @Mock LieuRepository            lieuRepo;
    @Mock StockNiveauRepository     stockRepo;
    @Mock ReservationRepository     reservationRepo;
    @Mock PrelevementRepository     prelevementRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CntsWebServiceClient      cntsClient;
    @Mock CentreResolver            centreResolver;

    private StockStreamRegistry registry;
    private Server server;
    private ManagedChannel channel;
    private DonStockServiceGrpc.DonStockServiceBlockingStub blockingStub;
    private DonStockServiceGrpc.DonStockServiceStub asyncStub;

    @BeforeEach
    void setUp() throws Exception {
        registry = new StockStreamRegistry();
        DonStockGrpcService service = new DonStockGrpcService(
                lieuRepo, stockRepo, reservationRepo, prelevementRepo,
                registry, eventPublisher, cntsClient, centreResolver);

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).addService(service).directExecutor().build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        blockingStub = DonStockServiceGrpc.newBlockingStub(channel);
        asyncStub = DonStockServiceGrpc.newStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    // ================================================================
    // Every unary RPC — error statuses
    // ================================================================

    @Test
    void verifierEtReserver_invalidArgument_and_notFound() {
        assertThatThrownBy(() -> blockingStub.verifierEtReserver(VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d").setLieuId("l")
                .setGroupeSanguin(GroupeSanguin.A_POS).setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE).setQuantite(0).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);

        when(reservationRepo.findByDemandeId("d-missing")).thenReturn(Optional.empty());
        when(lieuRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockingStub.verifierEtReserver(VerifierEtReserverRequest.newBuilder()
                .setDemandeId("d-missing").setLieuId("missing")
                .setGroupeSanguin(GroupeSanguin.A_POS).setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE).setQuantite(1).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void consulterReservation_notFound_and_enAttenteStatus() {
        when(reservationRepo.findByDemandeId("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> blockingStub.consulterReservation(
                ConsulterReservationRequest.newBuilder().setDemandeId("unknown").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);

        Reservation r = new Reservation();
        r.setDemandeId("d-pending");
        r.setStatut("A_CONFIRMER");
        r.setReference("REF-9");
        when(reservationRepo.findByDemandeId("d-pending")).thenReturn(Optional.of(r));

        ConsulterReservationResponse resp = blockingStub.consulterReservation(
                ConsulterReservationRequest.newBuilder().setDemandeId("d-pending").build());
        assertThat(resp.getStatut()).isEqualTo(StatutReservation.EN_ATTENTE_CONFIRMATION);
        assertThat(resp.getReference()).isEqualTo("REF-9");
    }

    @Test
    void enregistrerPrelevement_invalidArgument_and_unavailable() {
        assertThatThrownBy(() -> blockingStub.enregistrerPrelevement(EnregistrerPrelevementRequest.newBuilder()
                .setDonId("don-bad").setGroupeSanguin(GroupeSanguin.A_POS).setVolumeMl(300).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);

        when(prelevementRepo.findByDonId("don-down")).thenReturn(Optional.empty());
        when(prelevementRepo.save(any(Prelevement.class))).thenAnswer(i -> i.getArgument(0));
        when(centreResolver.resolveStockCentreId("lieu-crts-sfax")).thenReturn("lieu-crts-sfax");
        when(cntsClient.enregistrerPrelevement(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenThrow(new RuntimeException("CNTS down"));

        assertThatThrownBy(() -> blockingStub.enregistrerPrelevement(EnregistrerPrelevementRequest.newBuilder()
                .setDonId("don-down").setCentreId("lieu-crts-sfax")
                .setGroupeSanguin(GroupeSanguin.A_POS).setVolumeMl(450)
                .setDatePrelevement("2026-11-20").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void delivrerCertificat_notFound_failedPrecondition_and_refused() throws Exception {
        when(prelevementRepo.findByDonId("don-none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> blockingStub.delivrerCertificat(DelivrerCertificatRequest.newBuilder()
                .setDonId("don-none").setDateDon("2026-11-20").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);

        Prelevement noNumber = new Prelevement();
        noNumber.setDonId("don-nonumber");
        noNumber.setCentreId("lieu-crts-sfax");
        when(prelevementRepo.findByDonId("don-nonumber")).thenReturn(Optional.of(noNumber));
        assertThatThrownBy(() -> blockingStub.delivrerCertificat(DelivrerCertificatRequest.newBuilder()
                .setDonId("don-nonumber").setDateDon("2026-11-20").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);

        Prelevement valid = new Prelevement();
        valid.setDonId("don-refused");
        valid.setCentreId("lieu-crts-sfax");
        valid.setNumeroDonNational("DN-1");
        when(prelevementRepo.findByDonId("don-refused")).thenReturn(Optional.of(valid));
        when(centreResolver.resolveStockCentreId("lieu-crts-sfax")).thenReturn("lieu-crts-sfax");
        when(cntsClient.delivrerCertificatDon(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new CertificatRefuseException("donor not eligible"));

        assertThatThrownBy(() -> blockingStub.delivrerCertificat(DelivrerCertificatRequest.newBuilder()
                .setDonId("don-refused").setNumeroDonNational("DN-1")
                .setDateDon("2026-11-20").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ================================================================
    // Every unary RPC — success paths
    // ================================================================

    @Test
    void verifierEtReserver_localSuccess() {
        when(reservationRepo.findByDemandeId("d-local")).thenReturn(Optional.empty());
        when(lieuRepo.findById("lieu-chu-hbs"))
                .thenReturn(Optional.of(lieu("lieu-chu-hbs", true)));
        StockNiveau stock = stock("lieu-chu-hbs", "O_NEG", "CGR", 10, 8);
        when(stockRepo.findByKeyForUpdate("lieu-chu-hbs", "O_NEG", "CGR"))
                .thenReturn(Optional.of(stock));
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        VerifierEtReserverResponse resp = blockingStub.verifierEtReserver(
                VerifierEtReserverRequest.newBuilder()
                        .setDemandeId("d-local").setLieuId("lieu-chu-hbs")
                        .setGroupeSanguin(GroupeSanguin.O_NEG).setComposant(Composant.CGR)
                        .setUrgence(Urgence.CRITIQUE).setQuantite(4).build());

        assertThat(resp.getStatut()).isEqualTo(StatutReservation.RESERVEE_STOCK_LOCAL);
        assertThat(stock.getDisponible()).isEqualTo(6);
        assertThat(stock.getReserve()).isEqualTo(4);
    }

    @Test
    void verifierEtReserver_centralSuccess() {
        when(reservationRepo.findByDemandeId("d-central")).thenReturn(Optional.empty());
        when(lieuRepo.findById("lieu-clinique-sfax"))
                .thenReturn(Optional.of(lieu("lieu-clinique-sfax", false)));
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(stockRepo.findByKeyForUpdate("lieu-clinique-sfax", "B_POS", "PLASMA"))
                .thenReturn(Optional.of(stock("lieu-clinique-sfax", "B_POS", "PLASMA", 0, 6)));

        DemanderApprovisionnementResponse soapResp = new DemanderApprovisionnementResponse();
        soapResp.setReference("REF-42");
        soapResp.setCentreSource("CRTS-SFAX");
        soapResp.setDelaiMinutes(30);
        soapResp.setQuantiteAccordee(2);
        soapResp.setStatut("ACCORDE");
        when(cntsClient.demanderApprovisionnement(anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString())).thenReturn(soapResp);

        VerifierEtReserverResponse resp = blockingStub.verifierEtReserver(
                VerifierEtReserverRequest.newBuilder()
                        .setDemandeId("d-central").setLieuId("lieu-clinique-sfax")
                        .setGroupeSanguin(GroupeSanguin.B_POS).setComposant(Composant.PLASMA)
                        .setUrgence(Urgence.HAUTE).setQuantite(2).build());

        assertThat(resp.getStatut()).isEqualTo(StatutReservation.APPROVISIONNEMENT_COMMANDE);
        assertThat(resp.getReference()).isEqualTo("REF-42");
        assertThat(resp.getCentreSource()).isEqualTo("CRTS-SFAX");
    }

    @Test
    void verifierEtReserver_shortageAndUnavailable() {
        when(reservationRepo.findByDemandeId(anyString())).thenReturn(Optional.empty());
        when(lieuRepo.findById("lieu-clinique-sfax"))
                .thenReturn(Optional.of(lieu("lieu-clinique-sfax", false)));
        when(reservationRepo.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(cntsClient.demanderApprovisionnement(anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString()))
                .thenThrow(new StockInsuffisantException("empty"))
                .thenThrow(new RuntimeException("timeout"));

        VerifierEtReserverRequest base = VerifierEtReserverRequest.newBuilder()
                .setLieuId("lieu-clinique-sfax")
                .setGroupeSanguin(GroupeSanguin.A_POS).setComposant(Composant.CGR)
                .setUrgence(Urgence.NORMALE).setQuantite(2).build();

        VerifierEtReserverResponse shortage = blockingStub.verifierEtReserver(
                base.toBuilder().setDemandeId("d-short").build());
        assertThat(shortage.getStatut()).isEqualTo(StatutReservation.STOCK_INSUFFISANT_PARTOUT);

        assertThatThrownBy(() -> blockingStub.verifierEtReserver(
                base.toBuilder().setDemandeId("d-down").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void enregistrerPrelevement_success_and_certificat_success() throws Exception {
        when(prelevementRepo.findByDonId(anyString())).thenReturn(Optional.empty());
        when(prelevementRepo.save(any(Prelevement.class))).thenAnswer(i -> i.getArgument(0));
        when(centreResolver.resolveStockCentreId("lieu-mobile-crts-sfax"))
                .thenReturn("lieu-crts-sfax");
        when(stockRepo.findByKeyForUpdate(eq("lieu-crts-sfax"), eq("O_NEG"), anyString()))
                .thenReturn(Optional.of(stock("lieu-crts-sfax", "O_NEG", "CGR", 10, 8)));

        tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse prelevResp =
                new tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse();
        prelevResp.setNumeroDonNational("DN-2026-0100");
        prelevResp.setStatut("QUALIFICATION_EN_COURS");
        when(cntsClient.enregistrerPrelevement(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(prelevResp);

        EnregistrerPrelevementResponse prelev = blockingStub.enregistrerPrelevement(
                EnregistrerPrelevementRequest.newBuilder()
                        .setDonId("don-ok").setDonneurId("donneur-1")
                        .setCentreId("lieu-mobile-crts-sfax").setGroupeSanguin(GroupeSanguin.O_NEG)
                        .setVolumeMl(450).setDatePrelevement("2026-11-19").build());
        assertThat(prelev.getNumeroDonNational()).isEqualTo("DN-2026-0100");
        assertThat(prelev.getStatut()).isEqualTo("QUALIFICATION_EN_COURS");

        Prelevement qualified = new Prelevement();
        qualified.setDonId("don-ok");
        qualified.setCentreId("lieu-mobile-crts-sfax");
        qualified.setNumeroDonNational("DN-2026-0100");
        when(prelevementRepo.findByDonId("don-ok")).thenReturn(Optional.of(qualified));

        DelivrerCertificatDonResponse certResp = new DelivrerCertificatDonResponse();
        certResp.setNumeroCertificat("CERT-2026-0100");
        certResp.setDateEmission(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-11-23"));
        certResp.setStatut("VALIDE");
        when(cntsClient.delivrerCertificatDon(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(certResp);

        DelivrerCertificatResponse cert = blockingStub.delivrerCertificat(
                DelivrerCertificatRequest.newBuilder()
                        .setDonId("don-ok").setNumeroDonNational("DN-2026-0100")
                        .setDonneurRef("donneur-1").setCentreId("lieu-mobile-crts-sfax")
                        .setDateDon("2026-11-19").build());
        assertThat(cert.getNumeroCertificat()).isEqualTo("CERT-2026-0100");
        assertThat(cert.getDateEmission()).isEqualTo("2026-11-23");
    }

    // ================================================================
    // Server streaming — snapshot, several changes, cancellation
    // ================================================================

    @Test
    void suivreNiveauxStock_sendsSnapshotThenChangesAndRemovesSubscriberOnCancel() throws Exception {
        StockNiveau s = stock("lieu-chu-hbs", "A_POS", "CGR", 25, 10);
        when(stockRepo.findByFilters(null, null)).thenReturn(List.of(s));
        when(lieuRepo.findById("lieu-chu-hbs"))
                .thenReturn(Optional.of(lieu("lieu-chu-hbs", true)));

        List<NiveauStockMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch snapshotReceived = new CountDownLatch(1);

        Context.CancellableContext context = Context.current().withCancellation();
        context.run(() -> asyncStub.suivreNiveauxStock(
                SuivreNiveauxStockRequest.newBuilder().build(),
                new StreamObserver<>() {
                    @Override public void onNext(NiveauStockMessage value) {
                        received.add(value);
                        snapshotReceived.countDown();
                    }
                    @Override public void onError(Throwable t) { }
                    @Override public void onCompleted() { }
                }));

        assertThat(snapshotReceived.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        NiveauStockMessage snapshot = received.get(0);
        assertThat(snapshot.getQuantite()).isEqualTo(25);
        assertThat(snapshot.getSeuilAlerte()).isEqualTo(10);
        assertThat(snapshot.getTimestamp()).isNotBlank();
        assertThat(registry.isEmpty()).isFalse();

        registry.publish(StockNiveauMapper.toProto(
                stock("lieu-chu-hbs", "A_POS", "CGR", 24, 10), lieu("lieu-chu-hbs", true)));
        registry.publish(StockNiveauMapper.toProto(
                stock("lieu-chu-hbs", "A_POS", "CGR", 23, 10), lieu("lieu-chu-hbs", true)));

        await(() -> received.size() == 3);
        assertThat(received).hasSize(3);

        context.cancel(null);
        await(() -> registry.isEmpty());
        assertThat(registry.isEmpty()).isTrue();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static Lieu lieu(String id, boolean stockLocal) {
        Lieu l = new Lieu();
        l.setId(id);
        l.setNom(id);
        l.setType("HOPITAL");
        l.setRegion("Sfax");
        l.setStockLocal(stockLocal);
        return l;
    }

    private static StockNiveau stock(String lieuId, String groupe, String composant,
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
