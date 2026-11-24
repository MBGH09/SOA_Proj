# Rapport technique — S2-06 : erreurs JSON normalisées

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après réalisation de S2-06. Les tests automatisés et la démonstration manuelle ont été **réellement exécutés le 18/11/2026** ; les sorties citées proviennent de ces exécutions. Les situations d'erreur amont sont produites par un **port fermé** et un **serveur HTTP simulé** clairement identifiés ; aucun test contre les vrais microservices n'est revendiqué.

---

## 1. Introduction et objectif de l'étape

Après S2-05 (santé, logs, `X-Request-Id`), l'étape suivante identifiée dans les documents officiels est **S2-06 — Standard JSON errors**.

**Objectif :** garantir que le front-end reçoit **toujours** une réponse d'erreur JSON de forme identique, quel que soit le type d'échec, et **jamais** une page HTML générée par le serveur ou par Spring Boot.

---

## 2. Exigences officielles et critères d'acceptation

Source : `docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`, branche `origin/develop` (section « Student 2 », Phase 1), recoupée sur `origin/config` :

> **S2-06 Standard JSON errors.** Why: the frontend expects the same error JSON from every layer: timestamp, status, error, message, path.
> 1. Write an `ErrorWebExceptionHandler` with a high priority.
> 2. Map: connection refused to 503, response timeout to 504, unknown route to 404.
> **Done when:** stopping a service gives a JSON 503, never an HTML page.

| Exigence officielle | Mise en œuvre | Vérification |
|---|---|---|
| `ErrorWebExceptionHandler` de haute priorité | `JsonErrorWebExceptionHandler` avec `@Order(Ordered.HIGHEST_PRECEDENCE)` | Test unitaire + intégration |
| Format commun `timestamp`, `status`, `error`, `message`, `path` | Corps JSON construit dans cet ordre exact | Tests unitaires + intégration |
| Connexion refusée → **503** | Détection de `java.net.ConnectException` dans la chaîne des causes | Test unitaire + test d'intégration avec port fermé |
| Timeout de réponse → **504** | Détection de `java.util.concurrent.TimeoutException` et de `ResponseStatusException` 504 | Test unitaire + test d'intégration avec serveur lent |
| Route inconnue → **404** | `ResponseStatusException` 404 produit par Spring WebFlux | Test unitaire + test d'intégration |
| Service arrêté → **JSON 503, jamais de HTML** | Handler dédié qui écrit le JSON avant tout rendu HTML | Démonstration manuelle réelle |

**Critère d'acceptation :** arrêter un service (ou pointer une route vers un port fermé) renvoie un **503 JSON**, jamais une page HTML. **Satisfait** (section 6).

---

## 3. Environnement technique

| Élément | Valeur vérifiée | Preuve |
|---|---|---|
| JDK | **17.0.12** (Oracle, `C:\Program Files\Java\jdk-17`) | `mvn -version` / build |
| Maven | **3.9.6** | `mvn -version` |
| Spring Boot | **3.3.13** | `gateway/pom.xml` |
| Spring Cloud | **2023.0.6** (Spring Cloud Gateway **4.1.9**) | `gateway/pom.xml` |
| Gateway | Spring Cloud Gateway réactif (WebFlux / Netty) | dépendances et logs |
| Port local | **8090** (8080 occupé par Oracle XE) | `application.yml`, logs |
| OS | Windows 11 | `mvn -version` |

**Aucune dépendance ajoutée** : `gateway/pom.xml` est **inchangé**. Le handler utilise `jackson-databind`, `spring-webflux` et `spring-boot` déjà présents.

---

## 4. Modifications effectuées et fichiers concernés

| Fichier | État | Contenu |
|---|---|---|
| `gateway/src/main/java/tn/iit/blood/gateway/error/JsonErrorWebExceptionHandler.java` | **Créé** | `ErrorWebExceptionHandler` prioritaire : mapping des exceptions vers 503/504/404/500 et écriture du JSON |
| `gateway/src/test/java/tn/iit/blood/gateway/JsonErrorWebExceptionHandlerTest.java` | **Créé** | 6 tests unitaires du handler |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayErrorHandlingIntegrationTest.java` | **Créé** | 3 tests d'intégration (503, 504, 404) avec port fermé et serveur lent simulé |
| `gateway/src/main/resources/application.yml` | **Inchangé** | Aucune configuration nécessaire : le handler est un composant détecté automatiquement |
| `gateway/pom.xml` | **Inchangé** | Aucune dépendance ajoutée |
| Routes S2-02, CORS S2-03, timeouts S2-04, `RequestIdFilter` S2-05 | **Inchangés** | Préservés tels quels |
| `gateway/RAPPORT_S2-02.md` à `RAPPORT_S2-05.md`, `.env.example` | **Inchangés** | Rapports validés non réécrits |

---

## 5. Explications des choix techniques

### 5.1 Un handler qui remplace le gestionnaire par défaut

Spring Boot ne crée son `DefaultErrorWebExceptionHandler` que **si aucun autre bean `ErrorWebExceptionHandler` n'existe** (`@ConditionalOnMissingBean`). Déclarer `JsonErrorWebExceptionHandler` comme `@Component` **remplace donc** le rendu par défaut. Combiné à `@Order(Ordered.HIGHEST_PRECEDENCE)`, il s'exécute **avant** le `ResponseStatusExceptionHandler` interne de Spring WebFlux (qui, lui, se contenterait de fixer le statut sans corps JSON).

### 5.2 Correspondance des exceptions

La chaîne des causes est parcourue (avec une garde de profondeur) pour être robuste aux enveloppements de Reactor Netty :

| Exception détectée | Statut | Justification |
|---|---|---|
| `java.net.ConnectException` (dont `AnnotatedConnectException`) | **503** | Service arrêté / connexion refusée |
| `java.net.UnknownHostException`, `java.nio.channels.UnresolvedAddressException` | **503** | Service injoignable (nom non résolu) |
| `java.util.concurrent.TimeoutException` | **504** | Dépassement du délai de réponse (`GATEWAY_RESPONSE_TIMEOUT_MS`) |
| `ResponseStatusException` / `ErrorResponse` | **statut porté** | 504 déjà produit par SCG 4.1.9, 404 des routes inconnues |
| Toute autre exception | **500** | Filet de sécurité, message générique |

Dans Spring Cloud Gateway 4.1.9, `NettyRoutingFilter` transforme déjà le timeout de réponse en `ResponseStatusException(GATEWAY_TIMEOUT, ...)` ; le handler honore ce statut. Une connexion refusée n'est pas transformée par SCG : elle reste un `ConnectException`, d'où la détection explicite.

**Choix assumé :** le *timeout de connexion* (service injoignable) est classé **503**, tandis que le **504** est réservé au dépassement du délai de **réponse**, conformément à l'énoncé (« response timeout »).

### 5.3 Format du corps JSON

Ordre et champs garantis :

```json
{
  "timestamp": "2026-11-18T16:02:08.100876900+01:00",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Le service demande est momentanement indisponible.",
  "path": "/api/v1/donneurs",
  "requestId": "7e4788e6-0324-4f8f-98cd-2f56410adebb"
}
```

- `timestamp` : date ISO-8601 avec décalage horaire.
- `status` : code numérique.
- `error` : libellé HTTP standard (« Service Unavailable », « Gateway Timeout », « Not Found », « Internal Server Error »).
- `message` : message lisible (français) ; générique pour les 500 afin de **ne pas divulguer** de détail interne.
- `path` : chemin brut de la requête.
- `requestId` : **champ additionnel** repris de l'en-tête `X-Request-Id` posé par S2-05. Il permet de relier l'erreur aux logs, sans retirer ni renommer aucun des cinq champs exigés. Il est absent si la requête n'a pas traversé le filtre S2-05 (cas d'une route inconnue).

`Content-Type: application/json` et `Content-Length` sont fixés explicitement ; le corps est écrit directement dans la réponse réactive (pas de rendu HTML possible).

### 5.4 Cas d'une réponse déjà engagée

Si la réponse est déjà `committed` (par exemple un flux SSE interrompu en cours de diffusion), le handler ne tente pas de la réécrire : il renvoie `Mono.error(ex)`. Cela évite de corrompre un flux déjà commencé.

### 5.5 Compatibilité avec les étapes précédentes

- **S2-02 (routes)** : aucune route modifiée ; les routes inconnues passent par le 404 JSON.
- **S2-03 (CORS)** : les en-têtes CORS restent ajoutés par le filtre CORS ; le corps d'erreur est JSON.
- **S2-04 (timeouts)** : le 504 est désormais rendu par le handler au lieu du format par défaut ; le flux SSE à `response-timeout: -1` n'est pas affecté.
- **S2-05 (`X-Request-Id`)** : l'identifiant est repris dans le corps JSON lorsqu'il est disponible.

---

## 6. Tests exécutés et résultats exacts

### 6.1 Build et tests automatisés

Commande exécutée dans `gateway/`, le **18/11/2026** :

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp clean verify
```

Résultat réel :

```
EXITCODE=0
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayApplicationTests
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayCorsIntegrationTest
[INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayCorsMultipleOriginsTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayErrorHandlingIntegrationTest
[INFO] Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayRoutesConfigurationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- GatewayRoutingIntegrationTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayTimeoutIntegrationTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayTimeoutsConfigurationTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- JsonErrorWebExceptionHandlerTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterIntegrationTest
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Bilan : 44 tests exécutés, 44 réussis, 0 échec, 0 erreur, 0 ignoré.**

| Classe de test | Avant S2-06 | Après S2-06 | Réussis |
|---|---|---|---|
| `GatewayApplicationTests` | 1 | 1 | 1 |
| `GatewayRoutesConfigurationTest` | 5 | 5 | 5 |
| `GatewayRoutingIntegrationTest` | 12 | 12 | 12 |
| `GatewayCorsIntegrationTest` | 4 | 4 | 4 |
| `GatewayCorsMultipleOriginsTest` | 2 | 2 | 2 |
| `GatewayTimeoutsConfigurationTest` | 4 | 4 | 4 |
| `GatewayTimeoutIntegrationTest` | 3 | 3 | 3 |
| `RequestIdFilterIntegrationTest` | 3 | 3 | 3 |
| `RequestIdFilterTest` | 1 | 1 | 1 |
| `JsonErrorWebExceptionHandlerTest` | — | **6** | 6 |
| `GatewayErrorHandlingIntegrationTest` | — | **3** | 3 |
| **Total** | **35** | **44** | **44** |

### 6.2 Tests unitaires (`JsonErrorWebExceptionHandlerTest`, 6 tests)

Sans démarrer de serveur (`MockServerWebExchange`) :

1. connexion refusée (`RuntimeException` contenant `ConnectException`) → **503**, `Content-Type: application/json`, cinq champs présents ;
2. `ResponseStatusException(GATEWAY_TIMEOUT)` → **504** ;
3. `TimeoutException` nue → **504** ;
4. `ResponseStatusException(NOT_FOUND)` → **404** ;
5. exception inattendue (`IllegalStateException("secret interne")`) → **500**, message générique **sans** « secret interne » ;
6. réponse déjà `committed` → l'erreur est renvoyée, pas de réécriture.

### 6.3 Tests d'intégration (`GatewayErrorHandlingIntegrationTest`, 3 tests)

Gateway réel sur port aléatoire, timeout de réponse **400 ms**, timeout de connexion **800 ms** :

- `DONNEURS_URL` pointe vers un **port fermé** (socket réservé puis fermé) → connexion refusée ;
- `DEMANDES_URL` pointe vers un **serveur HTTP simulé** qui répond après **1 500 ms** → timeout ;
- une route inexistante vérifie le 404.

Résultats :

1. `GET /api/v1/donneurs` → **503**, JSON, `error = "Service Unavailable"`, `path` correct, `X-Request-Id` présent et repris dans `requestId` ;
2. `GET /api/v1/demandes` → **504**, JSON, `error = "Gateway Timeout"` ;
3. `GET /api/v1/route-inconnue` → **404**, JSON, `error = "Not Found"`.

Chaque test vérifie aussi que le corps **ne contient ni `<html` ni `<!DOCTYPE`**.

### 6.4 Démonstration manuelle réelle

Gateway démarré sur **8090**, service Donneurs pointé vers le **port fermé 18099**, Demandes vers un serveur Python **simulé et lent** (3 s) sur **18084**, timeout de réponse **800 ms**.

**① Service arrêté — `GET /api/v1/donneurs` :**

```
HTTP/1.1 503 Service Unavailable
X-Request-Id: 7e4788e6-0324-4f8f-98cd-2f56410adebb
Content-Type: application/json
Content-Length: 234

{"timestamp":"2026-11-18T16:02:08.100876900+01:00","status":503,"error":"Service Unavailable","message":"Le service demande est momentanement indisponible.","path":"/api/v1/donneurs","requestId":"7e4788e6-0324-4f8f-98cd-2f56410adebb"}
```

**② Timeout de réponse — `GET /api/v1/demandes` :**

```
HTTP/1.1 504 Gateway Timeout
X-Request-Id: bf6e47aa-5f22-4db8-b768-e1e0c5fc1a14
Content-Type: application/json
Content-Length: 237

{"timestamp":"2026-11-18T16:02:08.971275700+01:00","status":504,"error":"Gateway Timeout","message":"Le service demande n'a pas repondu dans le delai imparti.","path":"/api/v1/demandes","requestId":"bf6e47aa-5f22-4db8-b768-e1e0c5fc1a14"}
```

**③ Route inconnue — `GET /api/v1/route-inconnue` :**

```
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 175

{"timestamp":"2026-11-18T16:02:09.003272+01:00","status":404,"error":"Not Found","message":"Aucune ressource ne correspond au chemin demande.","path":"/api/v1/route-inconnue"}
```

**④ Vérification « jamais de HTML » (corps réels) :**

```
corps 503: HTML=False debut={"timestamp":"2026-11-18T16:02:09.105784900+01:00","status":503,"error
corps 504: HTML=False debut={"timestamp":"2026-11-18T16:02:09.935570200+01:00","status":504,"error
corps 404: HTML=False debut={"timestamp":"2026-11-18T16:02:09.961786400+01:00","status":404,"error
```

**⑤ Santé :** `GET /actuator/health` → **200**, `{"status":"UP"}`.

**⑥ Logs Gateway** (stdout) :

```
2026-11-18T16:02:08.119+01:00 WARN  [7e4788e6-0324-4f8f-98cd-2f56410adebb] t.i.b.g.e.JsonErrorWebExceptionHandler - Reponse d'erreur 503 pour GET /api/v1/donneurs (message=Le service demande est momentanement indisponible.)
2026-11-18T16:02:08.971+01:00 WARN  [] t.i.b.g.e.JsonErrorWebExceptionHandler - Reponse d'erreur 504 pour GET /api/v1/demandes (message=Le service demande n'a pas repondu dans le delai imparti.)
2026-11-18T16:02:09.004+01:00 WARN  [] t.i.b.g.e.JsonErrorWebExceptionHandler - Reponse d'erreur 404 pour GET /api/v1/route-inconnue (message=Aucune ressource ne correspond au chemin demande.)
```

**⑦ Arrêt :** ports **8090** et **18084** libres, aucune erreur sur la sortie d'erreur du Gateway.

### 6.5 Non-régression manuelle (route normale, CORS, santé)

Backend rapide simulé sur `18083`, timeouts par défaut :

- `GET /api/v1/donneurs` → **200**, `X-Request-Id: 30b91a0d-7eb8-4069-8f1a-e043dfdfad0f`, identifiant reçu par le backend ;
- préflight `OPTIONS /api/v1/donneurs` avec `Origin: http://localhost:3000` → **200**, `Access-Control-Allow-Origin: http://localhost:3000`, `Access-Control-Expose-Headers: Location, X-Request-Id` ;
- `GET /actuator/health` → **200**, `{"status":"UP"}`.

### 6.6 Nature exacte des tests

| Test | Nature | Vrais microservices ? |
|---|---|---|
| Mapping des exceptions et format JSON | Unitaire (MockServerWebExchange) | Sans objet |
| Connexion refusée (503) | Intégration réelle Gateway + **port fermé** | Non |
| Timeout de réponse (504) | Intégration réelle Gateway + **serveur simulé** | Non |
| Route inconnue (404) | Intégration réelle Gateway | Sans objet |
| Démonstration manuelle 503/504/404 | Réelle, Gateway + **port fermé** + **serveur Python simulé** | Non |
| Intégration avec `service-donneurs` / `service-demandes` | **Non réalisée** | Non disponibles |

---

## 7. Commandes pour reproduire les tests

```powershell
# Depuis le dossier gateway/
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 1) Build + tous les tests (44 attendus)
mvn -B -ntp clean verify

# 2) Démarrage du Gateway (port 8090)
java -jar target\gateway-0.0.1-SNAPSHOT.jar

# 3) Service arrêté : 503 JSON (aucun service ne doit ecouter sur 8081 par defaut)
curl.exe -s -i http://localhost:8090/api/v1/donneurs

# 4) Route inconnue : 404 JSON
curl.exe -s -i http://localhost:8090/api/v1/route-inconnue

# 5) Sante : 200 UP
curl.exe -s -i http://localhost:8090/actuator/health
```

Pour le **504**, lancer un backend lent sur `18084` (script en annexe) et démarrer le Gateway avec :

```powershell
$env:DONNEURS_URL = "http://127.0.0.1:18099"   # port ferme
$env:DEMANDES_URL = "http://127.0.0.1:18084"   # backend lent
$env:GATEWAY_CONNECT_TIMEOUT_MS = "800"
$env:GATEWAY_RESPONSE_TIMEOUT_MS = "800"
java -jar target\gateway-0.0.1-SNAPSHOT.jar

curl.exe -s -i http://localhost:8090/api/v1/donneurs   # 503
curl.exe -s -i http://localhost:8090/api/v1/demandes   # 504
curl.exe -s -i http://localhost:8090/api/v1/route-inconnue   # 404
```

Aucune variable d'environnement nouvelle n'est nécessaire pour S2-06 ; `GATEWAY_CONNECT_TIMEOUT_MS` et `GATEWAY_RESPONSE_TIMEOUT_MS` (S2-04) sont réutilisées.

---

## 8. Problèmes rencontrés et corrections

| Point | Analyse | Correction |
|---|---|---|
| Risque que le `ResponseStatusExceptionHandler` interne écrive une réponse vide avant le handler | Spring WebFlux applique les `WebExceptionHandler` dans l'ordre ; ce handler interne n'est pas ordonné et se place donc en dernier | `@Order(Ordered.HIGHEST_PRECEDENCE)` garantit l'exécution en premier ; vérifié par les tests 404 et 504 |
| Une connexion refusée n'est pas convertie en statut par Spring Cloud Gateway | Elle remonte telle quelle (souvent `AnnotatedConnectException`) | Parcours de la chaîne des causes à la recherche de `ConnectException` → 503 |
| Le timeout de réponse est déjà transformé par SCG 4.1.9 | `NettyRoutingFilter` produit `ResponseStatusException(GATEWAY_TIMEOUT)` | Le handler honore le statut porté ; `TimeoutException` nue également couverte |
| Ne pas divulguer de détail interne sur une erreur 500 | Le message de l'exception peut contenir des informations sensibles | Message générique « Erreur interne de la passerelle. » ; test dédié |
| Réponse déjà engagée (flux SSE) | Réécrire le corps corromprait le flux | Garde `response.isCommitted()` → `Mono.error(ex)` |
| MDC absent sur la ligne de log du handler (504/404) | Même limite que S2-05 : le contexte réactif peut changer de thread et le MDC est retiré en fin de filtre | Sans conséquence : le `requestId` est lu depuis l'en-tête de réponse et repris dans le corps JSON pour les routes existantes |

Aucune régression n'a nécessité de modifier S2-02, S2-03, S2-04 ou S2-05. Le build a réussi **du premier coup** après implémentation.

---

## 9. Limites restantes

- **Vrais microservices non testés.** `service-donneurs`, `service-demandes` et `service-don-stock` ne sont pas disponibles localement ; le 503 est démontré avec un **port fermé**, le 504 avec un **serveur simulé**. Le critère « arrêter un service » est donc reproduit fidèlement au niveau réseau (connexion refusée), mais pas avec les véritables processus métier.
- **Contrat inter-couches non vérifié.** Le format commun `timestamp/status/error/message/path` est garanti **côté Gateway** ; les services métier devront adopter le même format pour que « toutes les couches » soient réellement identiques.
- **`requestId` additionnel.** Le champ est présent en plus des cinq champs exigés, uniquement lorsque le filtre S2-05 a tourné. Il n'est pas présent pour une route inconnue.
- **Codes non standard.** Un `ResponseStatusException` portant un code hors `HttpStatus` connu retombe sur 500 ; les codes utilisés par le projet sont standard.
- **Front-end réel / navigateur non testé.**
- **Compression / erreurs de bas niveau.** Les erreurs survenant avant l'entrée dans la chaîne WebFlux (ex. échec TLS au niveau Netty) ne sont pas couvertes par ce handler.
- **Aucun test avec le vrai flux SSE de production** (même limite que S2-04).

---

## 10. Conclusion et statut final

**S2-06 est terminée localement pour le composant Gateway.**

- Un `ErrorWebExceptionHandler` de **plus haute priorité** (`JsonErrorWebExceptionHandler`) remplace le gestionnaire par défaut et produit **toujours** un corps JSON.
- Correspondances vérifiées : **connexion refusée → 503**, **timeout de réponse → 504**, **route inconnue → 404**, autre erreur → **500**.
- Format commun respecté : `timestamp`, `status`, `error`, `message`, `path` (+ `requestId` lorsqu'il est disponible).
- `Content-Type: application/json` ; **aucune page HTML** observée sur les trois cas d'erreur.
- `mvn -B -ntp clean verify` (JDK 17.0.12, Maven 3.9.6) : **EXITCODE=0**, **BUILD SUCCESS**, **44 tests réussis sur 44** (35 précédents préservés + 9 nouveaux).
- Non-régression vérifiée : route normale 200 avec `X-Request-Id`, préflight CORS 200, `/actuator/health` 200.
- Aucune modification de `application.yml`, de `pom.xml`, des routes, du CORS, des timeouts ni du filtre S2-05.

**Réserve principale :** aucun test avec les vrais microservices ni avec les vraies couches métier ; les cas d'erreur sont reproduits avec un port fermé et un serveur simulé, clairement identifiés.

**Hors périmètre, non commencé :** S2-07/S2-08 (JWT et rôles), S2-09 (limitation de débit).

Aucun commit ni push n'a été effectué. **S2-06 attend la validation explicite.**

---

## Annexe — serveur HTTP lent simulé (vérification manuelle du 504)

Script Python temporaire (hors dépôt), exécuté sur `127.0.0.1:18084` :

```python
import sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        time.sleep(3)
        body = b"late response"
        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=UTF-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            sys.stderr.write("connexion fermee par le gateway (timeout attendu)\n")
            sys.stderr.flush()

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (time.strftime("%H:%M:%S"), fmt % args))
        sys.stderr.flush()

ThreadingHTTPServer(("127.0.0.1", 18084), Handler).serve_forever()
```

Ce serveur ne représente **pas** un microservice du projet : il sert uniquement à provoquer un dépassement du timeout de réponse côté Gateway.
