# Rapport technique — S2-05 : santé, logs et identifiant de requête

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après réalisation de S2-05. Les tests et la démonstration manuelle ont été **réellement exécutés le 18/11/2026** ; les sorties citées proviennent de ces exécutions. Le backend utilisé pour la vérification manuelle est un **serveur simulé** clairement identifié ; aucun test contre les vrais microservices n'est revendiqué.

---

## 1. Objectif et exigences officielles

L'étape suivant S2-04 a été identifiée dans les documents officiels (`docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`, section « Student 2 ») et dans la liste des tâches :

> **S2-05 Health, logs and request id.** Why: one id per request lets the team follow a call across 4 services in the logs.
> 1. Expose `/actuator/health` and log to stdout.
> 2. Write a global filter: if `X-Request-Id` is missing, create a UUID. Add it to the forwarded request and to the response, and write it in every log line.
> Done when: a call without the header gets one back, and the same id appears in the backend logs.

**Objectif :** rendre chaque requête traçable de bout en bout grâce à un identifiant unique, tout en gardant un endpoint de santé et des logs sur la sortie standard.

**Critère d'acceptation :** un appel sans `X-Request-Id` reçoit un identifiant en réponse, et **le même identifiant** arrive au backend (donc apparaît dans ses logs).

**Périmètre respecté :** uniquement la santé, les logs et `X-Request-Id`. Aucune erreur JSON personnalisée (S2-06), aucun JWT/rôle (S2-07/S2-08), aucune limitation de débit (S2-09) n'a été ajouté.

---

## 2. Environnement technique

| Élément | Valeur vérifiée | Preuve |
|---|---|---|
| JDK | **17.0.12** (Oracle, `C:\Program Files\Java\jdk-17`) | `mvn -version`, logs Java |
| Maven | **3.9.6** | `mvn -version` |
| Spring Boot | **3.3.13** | `gateway/pom.xml` |
| Spring Cloud | **2023.0.6** (Spring Cloud Gateway 4.1.9) | `gateway/pom.xml` |
| Gateway | Spring Cloud Gateway réactif (WebFlux / Netty) | dépendances et logs |
| Port local | **8090** (8080 occupé par Oracle XE) | `application.yml`, log `Netty started on port 8090` |
| OS | Windows 11 | `mvn -version` |

Aucune dépendance ajoutée : `gateway/pom.xml` est **inchangé**. Le filtre s'appuie sur l'API `GlobalFilter` de Spring Cloud Gateway et sur SLF4J/Logback déjà présents.

---

## 3. Fichiers modifiés et créés

| Fichier | État | Contenu |
|---|---|---|
| `gateway/src/main/java/tn/iit/blood/gateway/filter/RequestIdFilter.java` | Créé | Filtre global : génération/reprise de `X-Request-Id`, ajout à la requête transmise et à la réponse, MDC + logs |
| `gateway/src/main/resources/application.yml` | Modifié | Bloc `logging.pattern.console` avec `[%X{requestId:-}]` ; exposition santé déjà présente, inchangée |
| `gateway/src/test/java/tn/iit/blood/gateway/RequestIdFilterIntegrationTest.java` | Créé | 3 tests de bout en bout (UUID généré, en-tête fourni, santé) |
| `gateway/src/test/java/tn/iit/blood/gateway/RequestIdFilterTest.java` | Créé | 1 test unitaire (en-tête présent mais vide) |
| `.env.example` | Inchangé | Aucune nouvelle variable nécessaire |
| `gateway/pom.xml` | Inchangé | Aucune dépendance ajoutée |
| Routes S2-02, CORS S2-03, timeouts S2-04 | Inchangés | Configurations précédentes préservées |
| `gateway/RAPPORT_S2-02.md` à `RAPPORT_S2-04.md` | Inchangés | Rapports validés, non réécrits |

---

## 4. Travail réalisé et choix techniques

### 4.1 Endpoint de santé et logs sur la sortie standard

- `/actuator/health` était déjà exposé depuis S2-01/S2-02 via `management.endpoints.web.exposure.include: health`. **Aucune modification n'était nécessaire** ; le bon fonctionnement est revérifié par un test et manuellement.
- Spring Boot journalise par défaut sur la console (stdout) : aucun appender fichier n'est configuré. La démonstration manuelle a bien capturé les lignes de log dans le **stdout** du processus.
- Un motif de logs explicite a été ajouté :

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%X{requestId:-}] %logger{36} - %msg%n"
```

Le champ `[%X{requestId:-}]` affiche l'identifiant placé dans le MDC (vide hors requête).

### 4.2 Filtre global `X-Request-Id`

`RequestIdFilter` implémente `GlobalFilter` avec l'ordre `Ordered.HIGHEST_PRECEDENCE` (exécution avant les autres filtres globaux) :

1. lecture de `X-Request-Id` dans la requête entrante ;
2. si absent **ou vide**, génération d'un UUID (`UUID.randomUUID().toString()`) ;
3. ajout de l'identifiant à la requête **transmise au microservice** (mutations des en-têtes) ;
4. ajout du même identifiant à la **réponse** renvoyée au client ;
5. positionnement dans le MDC + journalisation du début et de la fin de requête avec l'identifiant.

L'identifiant fourni par le client est **conservé tel quel** (non régénéré), conformément à l'énoncé. Un en-tête présent mais composé uniquement d'espaces est traité comme absent.

Le filtre ne modifie ni le corps ni le chemin de la requête : il n'interfère pas avec le routage S2-02, le CORS S2-03 ni les timeouts S2-04.

### 4.3 Choix de journalisation

- Le motif de logs s'appuie sur le MDC, ce qui permet d'afficher `[id]` sur toutes les lignes émises pendant le traitement de la requête sur le thread courant.
- Les deux lignes du filtre (début et fin) contiennent **aussi** l'identifiant explicitement dans le message, afin qu'il reste lisible même si un changement de thread réactif fait perdre le MDC.
- Le MDC est retiré à la fin de la requête pour éviter de propager un identifiant obsolète.
- Les logs restent sur la sortie standard ; aucune journalisation fichier n'a été ajoutée.

### 4.4 Compatibilité avec les étapes précédentes

- Les 10 routes S2-02/S2-04, le port 8090 et les variables `DONNEURS_URL` / `DEMANDES_URL` sont inchangés.
- La politique CORS S2-03 autorise déjà `X-Request-Id` et l'expose déjà au navigateur (`exposedHeaders`), ce qui rend l'identifiant lisible par le front-end.
- Les timeouts S2-04 restent actifs ; le filtre s'exécute avant le routage et n'altère pas les délais.

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
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterIntegrationTest
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Bilan : 35 tests exécutés, 35 réussis, 0 échec, 0 erreur, 0 ignoré.**

| Classe de test | Avant S2-05 | Après S2-05 | Réussis |
|---|---|---|---|
| `GatewayApplicationTests` | 1 | 1 | 1 |
| `GatewayRoutesConfigurationTest` | 5 | 5 | 5 |
| `GatewayRoutingIntegrationTest` | 12 | 12 | 12 |
| `GatewayCorsIntegrationTest` | 4 | 4 | 4 |
| `GatewayCorsMultipleOriginsTest` | 2 | 2 | 2 |
| `GatewayTimeoutsConfigurationTest` | 4 | 4 | 4 |
| `GatewayTimeoutIntegrationTest` | 3 | 3 | 3 |
| `RequestIdFilterIntegrationTest` | — | **3** | 3 |
| `RequestIdFilterTest` | — | **1** | 1 |
| **Total** | **31** | **35** | **35** |

### 5.2 Ce que vérifient les nouveaux tests

**`RequestIdFilterIntegrationTest` (3 tests)** — Gateway sur port aléatoire, serveur HTTP simulé qui mémorise l'en-tête reçu :

1. **sans en-tête** : la réponse contient un `X-Request-Id` valant un UUID, le backend reçoit **le même** identifiant, et cet identifiant apparaît dans les logs capturés ;
2. **avec en-tête `test-request-id-123`** : la réponse renvoie exactement cette valeur, le backend reçoit la même valeur et les logs la contiennent ;
3. **santé** : `GET /actuator/health` renvoie **200** avec `status = UP`.

**`RequestIdFilterTest` (1 test unitaire)** — en-tête présent mais vide (`"   "`) : un UUID est généré, la requête transmise porte cet UUID (et non des espaces) et la réponse aussi. Ce cas ne peut pas être envoyé par un vrai client HTTP (valeur d'en-tête invalide), d'où le test unitaire avec `MockServerWebExchange`.

### 5.3 Démonstration manuelle avec un backend simulé

Un serveur Python temporaire (hors dépôt) sur `127.0.0.1:18083` journalise l'en-tête `X-Request-Id` reçu et répond `ok`. Gateway démarré sur **8090** avec les routes pointant vers ce backend.

**Requête sans `X-Request-Id`** — en-têtes réels :

```
HTTP/1.1 200 OK
X-Request-Id: 5b702d6a-c680-4516-bd20-13ca0d9a66c7
Content-Type: text/plain; charset=UTF-8
Content-Length: 2
```

**Requête avec `X-Request-Id: demo-request-42`** :

```
HTTP/1.1 200 OK
X-Request-Id: demo-request-42
```

**Logs du backend simulé** (l'identifiant reçu est bien le même) :

```
15:52:19 X-Request-Id=5b702d6a-c680-4516-bd20-13ca0d9a66c7 path=/api/v1/donneurs
15:52:19 X-Request-Id=demo-request-42 path=/api/v1/donneurs
```

**Logs du Gateway sur stdout** (motif `[id]` + message) :

```
2026-11-18T15:52:19.545+01:00 INFO  [5b702d6a-c680-4516-bd20-13ca0d9a66c7] t.i.b.gateway.filter.RequestIdFilter - Requete recue: GET /api/v1/donneurs (requestId=5b702d6a-c680-4516-bd20-13ca0d9a66c7)
2026-11-18T15:52:19.677+01:00 INFO  [5b702d6a-c680-4516-bd20-13ca0d9a66c7] t.i.b.gateway.filter.RequestIdFilter - Requete terminee: GET /api/v1/donneurs statut=200 OK (requestId=5b702d6a-c680-4516-bd20-13ca0d9a66c7)
2026-11-18T15:52:19.716+01:00 INFO  [demo-request-42] t.i.b.gateway.filter.RequestIdFilter - Requete recue: GET /api/v1/donneurs (requestId=demo-request-42)
2026-11-18T15:52:19.722+01:00 INFO  [demo-request-42] t.i.b.gateway.filter.RequestIdFilter - Requete terminee: GET /api/v1/donneurs statut=200 OK (requestId=demo-request-42)
```

**Santé** — `curl -s -i http://localhost:8090/actuator/health` : **200 OK**, `{"status":"UP"}`.

**Arrêt** : ports **8090** et **18083** libres, aucune erreur sur la sortie d'erreur du Gateway.

### 5.4 Problème rencontré et solution

| Problème | Solution |
|---|---|
| Le cas « en-tête présent mais vide » ne peut pas être envoyé par le client HTTP de test (`WebClientRequestException: Validation failed for header 'X-Request-Id'`) | Ce cas est couvert par un **test unitaire** appelant directement le filtre avec `MockServerWebExchange` ; les cas réels restent couverts par le test d'intégration |

### 5.5 Nature exacte des tests

| Test | Nature | Backends réels ? |
|---|---|---|
| Génération / reprise de `X-Request-Id` | Réel, Gateway + serveur HTTP simulé | Non |
| Présence dans les logs | Réel (capture stdout du contexte Spring) | Sans objet |
| En-tête vide | Unitaire (MockServerWebExchange) | Sans objet |
| Santé | Réel | Sans objet |
| Intégration vrais microservices | **Non réalisée** | Non disponibles |

---

## 6. Commandes pour reproduire les tests

```powershell
# Depuis le dossier gateway/
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 1) Build + tous les tests (35 attendus)
mvn -B -ntp clean verify

# 2) Démarrage du Gateway (port 8090)
java -jar target\gateway-0.0.1-SNAPSHOT.jar

# 3) Requête sans identifiant : un X-Request-Id doit revenir
curl.exe -s -i http://localhost:8090/api/v1/donneurs

# 4) Requête avec identifiant : il doit être conservé
curl.exe -s -i -H "X-Request-Id: demo-request-42" http://localhost:8090/api/v1/donneurs

# 5) Santé
curl.exe -s -i http://localhost:8090/actuator/health
# Attendu : HTTP/1.1 200 OK + {"status":"UP"}
```

Pour la démonstration complète (même identifiant dans les logs du backend), lancer le backend simulé de l'annexe sur `18083`, puis démarrer le Gateway avec :

```powershell
$env:DONNEURS_URL = "http://127.0.0.1:18083"
$env:DEMANDES_URL = "http://127.0.0.1:18083"
java -jar target\gateway-0.0.1-SNAPSHOT.jar
```

---

## 7. Limites et points restant à vérifier

- **Vrais microservices : non testés.** Les dossiers de services ne contiennent pas d'implémentation locale et aucun service n'était démarré ; le « backend » de la démonstration est un serveur simulé. Le critère « même identifiant dans les logs du backend » est donc vérifié sur un backend simulé, pas sur `service-donneurs` ou `service-demandes`.
- **Propagation MDC sur tous les threads réactifs : non garantie.** Le motif de logs affiche l'identifiant tant que le MDC est positionné sur le thread courant ; sans bibliothèque de propagation de contexte, certaines lignes émises sur d'autres threads réactifs peuvent ne pas le porter. Les deux lignes du filtre contiennent explicitement l'identifiant, ce qui est vérifié.
- **Préflight CORS :** les requêtes `OPTIONS` de préflight sont traitées par le filtre CORS (S2-03) avant les filtres globaux du Gateway ; elles ne reçoivent donc pas de `X-Request-Id`. L'énoncé ne l'exige pas.
- **Actuator :** `/actuator/health` n'est pas une route métier et ne passe pas par `RequestIdFilter` ; il n'est pas censé porter l'en-tête.
- **Front-end réel / navigateur : non testé.** La lecture de `X-Request-Id` côté navigateur est autorisée par la configuration CORS existante, mais n'a pas été observée dans un vrai navigateur.
- **Format des erreurs :** les réponses d'erreur utilisent encore le format par défaut de Spring Boot ; la normalisation JSON relève de S2-06.

---

## 8. État final de validation

**S2-05 est terminée localement pour le composant Gateway.**

- `/actuator/health` reste exposé et répond **200 OK** avec `{"status":"UP"}` ; les logs sont émis sur la **sortie standard**, avec un motif incluant `[requestId]`.
- Le filtre global `RequestIdFilter` génère un UUID si `X-Request-Id` est absent ou vide, **conserve** l'identifiant fourni, l'ajoute à la **requête transmise** au backend et à la **réponse** client, et le journalise.
- Le critère d'acceptation est satisfait : un appel sans en-tête reçoit un UUID, et le **même** identifiant est reçu par le backend (vérifié sur backend simulé) et apparaît dans les logs du Gateway.
- `mvn -B -ntp clean verify` (JDK 17.0.12, Maven 3.9.6) : **EXITCODE=0**, **BUILD SUCCESS**, **35 tests réussis sur 35** (31 précédents préservés + 4 nouveaux).
- Les configurations S2-02 (routes), S2-03 (CORS) et S2-04 (timeouts, flux SSE) restent fonctionnelles ; leurs tests passent toujours.

**Réserve principale :** aucun test avec les vrais microservices ni avec un vrai front-end n'a été réalisé, faute de services disponibles localement.

**Hors périmètre, non commencé :** S2-06 (erreurs JSON normalisées), S2-07/S2-08 (JWT et rôles), S2-09 (limitation de débit).

Aucun commit ni push n'a été effectué. S2-05 attend la validation explicite.

---

## Annexe — backend HTTP simulé utilisé pour la démonstration manuelle

Script Python temporaire (hors dépôt), exécuté sur `127.0.0.1:18083` :

```python
import sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        request_id = self.headers.get("X-Request-Id")
        sys.stderr.write("%s X-Request-Id=%s path=%s\n"
                         % (time.strftime("%H:%M:%S"), request_id, self.path))
        sys.stderr.flush()
        body = b"ok"
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()

    def log_message(self, *args):
        pass

ThreadingHTTPServer(("127.0.0.1", 18083), Handler).serve_forever()
```

Ce serveur ne représente **pas** un microservice du projet : il sert uniquement à vérifier la transmission de l'identifiant de bout en bout.
