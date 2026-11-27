# Rapport technique — S2-04 : timeouts du client HTTP et route de flux SSE

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après réalisation de S2-04. Les tests et la démonstration manuelle ont été **réellement exécutés le 18/11/2026** ; les sorties citées proviennent de ces exécutions. Les backends utilisés sont des **serveurs simulés** clairement identifiés ; aucun test contre les vrais microservices n'est revendiqué.

---

## 1. Objectif et exigences officielles

L'étape suivant S2-03 a été identifiée dans les documents officiels du projet (`docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`, section « Student 2 », Phase 0/1) et dans la liste des tâches (`SOA_Blood_Donation_Project_v2_from_CNTS_study (1).md`) :

> **S2-04 Timeouts and the stream route.** Why: every network call needs an explicit timeout, but the live stock stream must stay open.
> 1. Set the connect and response timeouts of the Gateway HTTP client from variables (for example `GATEWAY_CONNECT_TIMEOUT_MS=2000`, `GATEWAY_RESPONSE_TIMEOUT_MS=10000`). A donation step makes Donneurs call Don & Stock with a 2 s deadline, so keep the response timeout longer than that.
> 2. For the route `/api/v1/stock/niveaux/flux`, set a negative per-route `response-timeout` in the route metadata (a negative value disables it).
> 3. Check that no filter buffers or rewrites the response of that route.
> 4. Test with `curl -N http://localhost:8080/api/v1/stock/niveaux/flux`: events must appear one by one.
> Done when: the stream stays open longer than the global timeout while other routes still time out.

**Objectif :** donner au client HTTP du Gateway des délais explicites et configurables, tout en laissant la route du flux temps réel du stock ouverte au-delà du délai global.

**Critère d'acceptation :** le flux reste ouvert plus longtemps que le timeout global, alors que les autres routes expirent bien (504).

**Périmètre respecté :** uniquement les timeouts et la route de flux. Aucun filtre `X-Request-Id` (S2-05), aucune erreur JSON personnalisée (S2-06), aucun JWT/rôle (S2-07/S2-08), aucune limitation de débit (S2-09) n'a été ajouté.

---

## 2. Environnement technique

| Élément | Valeur vérifiée | Preuve |
|---|---|---|
| JDK | **17.0.12** (Oracle, `C:\Program Files\Java\jdk-17`) | `mvn -version`, logs Java |
| Maven | **3.9.6** | `mvn -version` |
| Spring Boot | **3.3.13** | `gateway/pom.xml` |
| Spring Cloud | **2023.0.6** (Spring Cloud Gateway **4.1.9**) | `gateway/pom.xml`, jar `spring-cloud-gateway-server-4.1.9.jar` |
| Gateway | Spring Cloud Gateway réactif (WebFlux / Netty) | dépendances et logs |
| Port local | **8090** (8080 occupé par Oracle XE, adaptation conservée depuis S2-02) | `application.yml`, log `Netty started on port 8090` |
| OS | Windows 11 | `mvn -version` |

Aucune dépendance ajoutée : `gateway/pom.xml` est **inchangé**. Les propriétés utilisées sont celles de Spring Cloud Gateway 4.1.9 :
- `spring.cloud.gateway.httpclient.connect-timeout` : entier en millisecondes ;
- `spring.cloud.gateway.httpclient.response-timeout` : durée (`Duration`), donc suffixée `ms` dans la configuration ;
- métadonnée de route `response-timeout` : nombre en millisecondes, valeur négative = pas de timeout.

---

## 3. Fichiers modifiés et créés

| Fichier | État | Contenu |
|---|---|---|
| `gateway/src/main/resources/application.yml` | Modifié | Bloc `httpclient` (connect/response timeouts depuis variables) + route `demandes-stock-flux` avec `metadata.response-timeout: -1` |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayTimeoutsConfigurationTest.java` | Créé | 4 tests de configuration (variables, métadonnées, prédicat) |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayTimeoutIntegrationTest.java` | Créé | 3 tests de bout en bout (504 des routes normales, flux préservé) |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayRoutesConfigurationTest.java` | Modifié (minimal) | Ajout de `demandes-stock-flux` et passage de 9 à 10 routes |
| `.env.example` | Modifié | Ajout de `GATEWAY_CONNECT_TIMEOUT_MS` et `GATEWAY_RESPONSE_TIMEOUT_MS` |
| `gateway/RAPPORT_S2-02.md`, `gateway/RAPPORT_S2-03.md` | Inchangés | Rapports validés, non réécrits |
| `gateway/pom.xml` | Inchangé | Aucune dépendance |
| Configuration CORS (S2-03) | Inchangée | Bloc `globalcors` conservé tel quel |
| Routes S2-02 existantes | Inchangées | Les 9 routes restent identiques |

**Justification de la modification du test S2-02 :** S2-04 exige un `response-timeout` négatif **par route** pour `/api/v1/stock/niveaux/flux`. Une route dédiée plus spécifique (`order: -1`) est donc ajoutée devant la route générale `/api/v1/stock/**`. Le nombre total de routes passe de 9 à 10 ; le test `allExpectedRoutesAreDefined` est ajusté en conséquence (`hasSize(10)` + nouvel identifiant). Les assertions fonctionnelles de S2-02 (cibles, prédicats, priorité de `creneaux`) restent inchangées et passent toujours.

---

## 4. Travail réalisé et choix techniques

### 4.1 Timeouts globaux configurables

Ajout dans `application.yml` sous `spring.cloud.gateway` :

```yaml
httpclient:
  connect-timeout: "${GATEWAY_CONNECT_TIMEOUT_MS:2000}"
  response-timeout: "${GATEWAY_RESPONSE_TIMEOUT_MS:10000}ms"
```

- `connect-timeout` est un entier en millisecondes (type `Integer` dans `HttpClientProperties` de SCG 4.1.9), donc **sans suffixe**.
- `response-timeout` est une `Duration` : la valeur de la variable est suffixée `ms` à la résolution.
- Valeurs par défaut : **2 000 ms** en connexion, **10 000 ms** en réponse. Le délai de réponse reste ainsi **supérieur au délai de 2 s** que Donneurs applique vers Don & Stock, comme exigé.
- Les variables surchargent la configuration sans recompilation.

### 4.2 Route dédiée au flux SSE

Ajout avant la route générale du stock :

```yaml
- id: demandes-stock-flux
  uri: "${DEMANDES_URL:http://localhost:8082}"
  order: -1
  metadata:
    response-timeout: -1
  predicates:
    - "Path=/api/v1/stock/niveaux/flux"
```

- **Prédicat exact** sur `/api/v1/stock/niveaux/flux`.
- **`order: -1`** : priorité supérieure à la route générale `/api/v1/stock/**` (ordre par défaut 0), donc le flux est bien aiguillé vers cette route.
- **`response-timeout: -1`** : dans le code de `NettyRoutingFilter` (SCG 4.1.9), une valeur négative renvoie `null` et **aucun timeout de réponse n'est appliqué** à cette route.
- La route générale `demandes-stock` conserve le timeout global : les autres endpoints du stock expirent normalement.

**Pourquoi une route dédiée plutôt que la métadonnée sur `/api/v1/stock/**` ?** L'énoncé vise explicitement la route `/api/v1/stock/niveaux/flux`. Une route dédiée limite la désactivation du timeout au seul flux, sans affaiblir les autres appels du stock. C'est la lecture littérale et la plus sûre de l'exigence.

### 4.3 Absence de filtre qui tamponne ou réécrit la réponse

- Aucun filtre `filters:` n'est déclaré sur les routes du stock : ni `RewritePath`, ni `SetResponseHeader`, ni filtre de bufferisation.
- Aucun filtre global ajouté en S2-04. Les filtres actifs sont ceux de Spring Cloud Gateway (`NettyRoutingFilter`, `NettyWriteResponseFilter`), qui transmettent le corps en flux.
- Le seul filtre global existant est le CORS de S2-03, qui ajoute des en-têtes et ne lit pas le corps.
- Le test manuel confirme un transfert **chunked** et deux événements reçus à 3 s d'intervalle, donc sans bufferisation de la réponse complète.

### 4.4 Compatibilité avec S2-02 et S2-03

- Les 9 routes S2-02 sont conservées à l'identique ; une 10e route spécialisée est ajoutée.
- La configuration CORS S2-03 est intacte : le préflight a été revérifié manuellement (section 5.3).
- Le port 8090 et `DONNEURS_URL` / `DEMANDES_URL` restent inchangés.
- Les réponses 504 observées utilisent le format d'erreur **par défaut de Spring Boot**. Elles ne constituent pas le format JSON normalisé de S2-06, qui n'est pas commencé.

---

## 5. Tests réellement exécutés et résultats

### 5.1 Build et tests automatisés

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
[INFO] Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayRoutesConfigurationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- GatewayRoutingIntegrationTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayTimeoutIntegrationTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayTimeoutsConfigurationTest
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Bilan : 31 tests exécutés, 31 réussis, 0 échec, 0 erreur, 0 ignoré.**

| Classe de test | Avant S2-04 | Après S2-04 | Réussis |
|---|---|---|---|
| `GatewayApplicationTests` | 1 | 1 | 1 |
| `GatewayRoutesConfigurationTest` | 5 | 5 | 5 |
| `GatewayRoutingIntegrationTest` | 12 | 12 | 12 |
| `GatewayCorsIntegrationTest` | 4 | 4 | 4 |
| `GatewayCorsMultipleOriginsTest` | 2 | 2 | 2 |
| `GatewayTimeoutsConfigurationTest` | — | **4** | 4 |
| `GatewayTimeoutIntegrationTest` | — | **3** | 3 |
| **Total** | **24** | **31** | **31** |

### 5.2 Ce que vérifient les nouveaux tests

**`GatewayTimeoutsConfigurationTest` (4 tests)** — contexte Spring avec `GATEWAY_CONNECT_TIMEOUT_MS=1234` et `GATEWAY_RESPONSE_TIMEOUT_MS=4321` :

1. `HttpClientProperties` reçoit bien **1234 ms** en connexion et **4321 ms** en réponse depuis les variables ;
2. la route `demandes-stock-flux` possède `response-timeout = -1` dans ses métadonnées ;
3. son prédicat `Path` vaut exactement `/api/v1/stock/niveaux/flux` ;
4. les routes `donneurs-donneurs`, `demandes-demandes` et `demandes-stock` **ne désactivent pas** le timeout global.

**`GatewayTimeoutIntegrationTest` (3 tests)** — Gateway sur port aléatoire, timeout global **600 ms**, serveur HTTP simulé retardant ses réponses de **1 200 ms** :

1. `GET /api/v1/donneurs` → **504** (le délai global s'applique) ;
2. `GET /api/v1/stock` → **504** (la route générale du stock garde le timeout global) ;
3. `GET /api/v1/stock/niveaux/flux` → **200**, corps contenant `data: un` **et** `data: deux`, durée mesurée **≥ 1 500 ms** (soit bien au-delà du timeout global de 600 ms), grâce à `response-timeout: -1`.

Il s'agit d'un **serveur simulé**, pas des vrais microservices.

### 5.3 Démonstration manuelle avec un backend SSE simulé

Un petit serveur Python temporaire (`ThreadingHTTPServer`, hors dépôt) a été utilisé :
- `/api/v1/stock/niveaux/flux` : attend **3 s** avant les en-têtes, puis envoie `data: un`, attend **3 s**, envoie `data: deux` (transfert chunked) ;
- toute autre route : attend **3 s** puis répond `late response`.

Gateway démarré sur **8090** avec un timeout global abaissé à **1 500 ms** :

```powershell
$env:DONNEURS_URL = "http://127.0.0.1:18082"
$env:DEMANDES_URL = "http://127.0.0.1:18082"
$env:GATEWAY_CONNECT_TIMEOUT_MS = "2000"
$env:GATEWAY_RESPONSE_TIMEOUT_MS = "1500"
java -jar target\gateway-0.0.1-SNAPSHOT.jar
```

**Flux SSE** — `curl -s -N -i http://localhost:8090/api/v1/stock/niveaux/flux` :

```
HTTP/1.1 200 OK
transfer-encoding: chunked
Content-Type: text/event-stream; charset=UTF-8
Cache-Control: no-cache

data: un

data: deux

DUREE_FLUX_MS=6225
```

Le serveur simulé a journalisé l'événement 1 à `15:43:25` et l'événement 2 à `15:43:28`, soit **3 s d'écart** : les événements arrivent bien l'un après l'autre et le flux reste ouvert **6,2 s**, très au-delà du timeout global de 1,5 s.

**Route Donneurs (backend lent)** — `curl -s -i http://localhost:8090/api/v1/donneurs` :

```
HTTP/1.1 504 Gateway Timeout
Content-Type: application/json
Content-Length: 135

{"timestamp":"2026-11-18T14:43:29.567+00:00","path":"/api/v1/donneurs","status":504,"error":"Gateway Timeout","requestId":"17befcb6-2"}
DUREE_DONNEURS_MS=1589
```

**Route stock générale** — `curl -s -i http://localhost:8090/api/v1/stock` : **504 Gateway Timeout** (le timeout global s'applique).

**Régression CORS (S2-03)** — préflight `OPTIONS /api/v1/donneurs` avec `Origin: http://localhost:3000` : **200 OK** et `Access-Control-Allow-Origin: http://localhost:3000` (configuration S2-03 intacte).

**Santé** — `curl -s -i http://localhost:8090/actuator/health` : **200 OK**, `{"status":"UP"}`.

**Arrêt** : ports **8090** et **18082** libres, aucun processus laissé en vie. Une exception côté serveur Python après la fermeture de connexion par le Gateway (route en timeout) est attendue et sans effet sur le Gateway.

### 5.4 Nature exacte des tests

| Test | Nature | Backends réels ? |
|---|---|---|
| Timeouts globaux (504 sur routes lentes) | Réel, Gateway + serveur HTTP simulé | Non |
| Flux SSE préservé au-delà du timeout | Réel, Gateway + serveur SSE simulé | Non |
| Variables de configuration | Contexte Spring complet | Sans objet |
| CORS / santé | Réel | Sans objet |
| Intégration vrais microservices / vrai front | **Non réalisée** | Non disponibles |

---

## 6. Commandes pour reproduire les tests

```powershell
# Depuis le dossier gateway/
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 1) Build + tous les tests (31 attendus)
mvn -B -ntp clean verify

# 2) Démarrage du Gateway (port 8090)
java -jar target\gateway-0.0.1-SNAPSHOT.jar

# 3) Santé
curl.exe -s -i http://localhost:8090/actuator/health
# Attendu : HTTP/1.1 200 OK + {"status":"UP"}
```

Pour reproduire la démonstration manuelle du flux, lancer un backend SSE simulé sur `18082` (script en annexe), puis :

```powershell
$env:DONNEURS_URL = "http://127.0.0.1:18082"
$env:DEMANDES_URL = "http://127.0.0.1:18082"
$env:GATEWAY_CONNECT_TIMEOUT_MS = "2000"
$env:GATEWAY_RESPONSE_TIMEOUT_MS = "1500"
java -jar target\gateway-0.0.1-SNAPSHOT.jar

# Flux : deux événements espacés de 3 s malgré le timeout global de 1,5 s
curl.exe -s -N -i http://localhost:8090/api/v1/stock/niveaux/flux

# Routes normales : 504 après ~1,5 s
curl.exe -s -i http://localhost:8090/api/v1/donneurs
curl.exe -s -i http://localhost:8090/api/v1/stock
```

Les timeouts se surchargent sans recompilation via `GATEWAY_CONNECT_TIMEOUT_MS` et `GATEWAY_RESPONSE_TIMEOUT_MS` (documentés dans `.env.example`).

---

## 7. Limites et points restant à vérifier

- **Vrais microservices : non testés.** `service-donneurs`, `service-demandes` et `service-don-stock` ne sont pas disponibles localement ; les backends utilisés sont des serveurs simulés (JDK `HttpServer` dans les tests, Python pour la démonstration manuelle).
- **Vrai flux SSE de production : non testé.** Le flux `/api/v1/stock/niveaux/flux` n'existe pas encore côté `service-demandes` (aucun contrôleur REST stock) ; la démonstration porte sur le comportement du Gateway face à un flux SSE simulé.
- **Front-end réel / navigateur : non testé.**
- **Timeout de connexion : vérifié par configuration uniquement.** Le test d'intégration ne simule pas une connexion TCP qui ne s'établit pas (ce cas est peu déterministe localement) ; la valeur issue de la variable et son application par `HttpClientFactory` sont validées par la configuration et la documentation de SCG 4.1.9.
- **Sémantique exacte du timeout global dans SCG 4.1.9 :** `NettyRoutingFilter` applique un `.timeout(...)` à l'attente de la réponse amont. Le backend simulé retarde donc l'envoi des en-têtes pour rendre le timeout observable ; le corps du flux est ensuite transmis en chunked. La route à `-1` n'applique aucun de ces timeouts.
- **Format des erreurs 504 :** format par défaut de Spring Boot, pas encore le JSON normalisé de S2-06.
- **`service-don-stock` reste un service gRPC** ; le délai de 2 s évoqué par l'énoncé concerne l'appel interne Donneurs → Don & Stock et n'est pas testé ici.

---

## 8. État final de validation

**S2-04 est terminée localement pour le composant Gateway.**

- Les timeouts **connect** et **response** du client HTTP sont lus depuis `GATEWAY_CONNECT_TIMEOUT_MS` (défaut 2 000 ms) et `GATEWAY_RESPONSE_TIMEOUT_MS` (défaut 10 000 ms, > 2 s).
- La route dédiée `demandes-stock-flux` (`/api/v1/stock/niveaux/flux`, `order: -1`) porte `metadata.response-timeout: -1`, ce qui désactive le timeout de réponse pour le flux uniquement.
- Aucun filtre ne tamponne ni ne réécrit la réponse de cette route ; le transfert observé est **chunked** et progressif.
- `mvn -B -ntp clean verify` (JDK 17.0.12, Maven 3.9.6) : **EXITCODE=0**, **BUILD SUCCESS**, **31 tests réussis sur 31** (24 précédents préservés + 7 nouveaux).
- Démonstration réelle : le flux reste ouvert **6,2 s** (deux événements espacés de 3 s) malgré un timeout global de **1,5 s**, alors que `/api/v1/donneurs` et `/api/v1/stock` répondent **504** en ~1,5 s. Critère d'acceptation **satisfait**.
- Les configurations S2-02 (routes, port, variables) et S2-03 (CORS) restent fonctionnelles ; le préflight CORS et `/actuator/health` ont été revérifiés.

**Réserve principale :** aucun test avec les vrais microservices ni avec le vrai flux SSE de production n'a été réalisé, faute de services disponibles localement.

**Hors périmètre, non commencé :** S2-05 (logs / `X-Request-Id`), S2-06 (erreurs JSON), S2-07/S2-08 (JWT et rôles), S2-09 (limitation de débit).

Aucun commit ni push n'a été effectué. S2-04 attend la validation explicite.

---

## Annexe — backend SSE simulé utilisé pour la démonstration manuelle

Script Python temporaire (hors dépôt), exécuté sur `127.0.0.1:18082` :

```python
import sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (time.strftime("%H:%M:%S"), fmt % args))
        sys.stderr.flush()

    def do_GET(self):
        if self.path.startswith("/api/v1/stock/niveaux/flux"):
            time.sleep(3)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=UTF-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
            self._chunk(b"data: un\n\n")
            time.sleep(3)
            self._chunk(b"data: deux\n\n")
            self.wfile.write(b"0\r\n\r\n")
            self.wfile.flush()
        else:
            time.sleep(3)
            body = b"late response"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=UTF-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            self.wfile.flush()

    def _chunk(self, data):
        self.wfile.write(("%X\r\n" % len(data)).encode() + data + b"\r\n")
        self.wfile.flush()
        self.log_message("evenement envoye (%d octets)", len(data))

ThreadingHTTPServer(("127.0.0.1", 18082), Handler).serve_forever()
```

Ce serveur ne représente **pas** un microservice du projet : il sert uniquement à prouver le comportement des timeouts du Gateway.
