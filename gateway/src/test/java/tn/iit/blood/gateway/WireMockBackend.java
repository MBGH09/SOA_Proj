package tn.iit.blood.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

/**
 * Petit support commun aux tests S2-10.
 *
 * <p>WireMock joue le role des microservices Donneurs et Demandes : il ne
 * s'agit pas des vrais services. Le serveur ecoute sur l'adresse de bouclage
 * et sur un port alloue dynamiquement, afin que plusieurs builds puissent
 * s'executer sans collision.</p>
 */
final class WireMockBackend {

    private WireMockBackend() {
    }

    /**
     * Alloue un port libre puis le libere immediatement.
     *
     * <p>Utile au test 503 : le serveur WireMock doit redemarrer sur le
     * <em>meme</em> port apres son arret, car l'URL injectee dans le gateway
     * est figee au demarrage du contexte Spring.</p>
     */
    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Demarre un serveur WireMock sur un port dynamique. */
    static WireMockServer startDynamicPortServer() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options()
                .bindAddress("127.0.0.1")
                .dynamicPort());
        server.start();
        return server;
    }

    /** Demarre un serveur WireMock sur le port indique (deja reserve). */
    static WireMockServer startOnPort(int port) {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options()
                .bindAddress("127.0.0.1")
                .port(port));
        server.start();
        return server;
    }

    /** URL de base utilisee pour les variables DONNEURS_URL / DEMANDES_URL. */
    static String baseUrl(WireMockServer server) {
        return "http://127.0.0.1:" + server.port();
    }
}
