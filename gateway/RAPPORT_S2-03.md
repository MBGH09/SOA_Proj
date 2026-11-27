# Rapport technique — S2-03 : configuration CORS globale du Gateway

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après réalisation de S2-03. Les tests et le test de préflight ont été **réellement exécutés le 18/11/2026** ; les sorties citées proviennent de ces exécutions. Les tests utilisant un serveur simulé sont explicitement distingués d'une intégration avec les vrais microservices. Aucun résultat n'est inventé.

---

## 1. Objectif de S2-03

L'énoncé officiel (`docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`, section « Student 2 », Phase 0/1) définit S2-03 ainsi :

> **S2-03 CORS.** Why: without it the browser blocks every call from `localhost:3000`.
> 1. Add a global CORS configuration. Allowed origins come from `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`). Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Allowed headers: Authorization, Content-Type, Accept, X-Request-Id. Exposed headers: Location, X-Request-Id.
> 2. Test a preflight request: `curl -i -X OPTIONS http://localhost:8080/api/v1/donneurs -H 'Origin: http://localhost:3000' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type,authorization'`
> Done when: the answer contains `Access-Control-Allow-Origin: http://localhost:3000`.

**Objectif :** autoriser le front-end (`http://localhost:3000`) à appeler l'API Gateway depuis un navigateur, en centralisant la politique CORS au point d'entrée unique, et le prouver par une requête de préflight réelle.

**Critère d'acceptation :** la réponse au préflight contient `Access-Control-Allow-Origin: http://localhost:3000`.

La liste des tâches (`SOA_Blood_Donation_Project_v2_from_CNTS_study (1).md`) confirme : « S2-03 CORS from `CORS_ALLOWED_ORIGINS`, preflight test. »

**Périmètre respecté :** uniquement le CORS. Aucun délai d'attente (S2-04), aucun filtre `X-Request-Id` (S2-05), aucune erreur JSON (S2-06), aucun JWT/rôle (S2-07/S2-08), aucune limitation de débit (S2-09) n'a été ajouté.

---

## 2. Environnement technique

| Élément | Valeur vérifiée | Preuve |
|---|---|---|
| JDK utilisé pour le build et l'exécution | **17.0.12** (Oracle, `C:\Program Files\Java\jdk-17`) | `mvn -version`, logs Java |
| Maven | **3.9.6** (`C:\tools\apache-maven-3.9.6`) | `mvn -version` |
| Spring Boot | **3.3.13** | `gateway/pom.xml` |
| Spring Cloud | **2023.0.6** | `gateway/pom.xml` |
| Niveau Java | `release 17` | logs de compilation |
| Gateway | Spring Cloud Gateway **réactif** (WebFlux / Netty) | dépendances et logs |
| Port local du Gateway | **8090** | `application.yml`, log `Netty started on port 8090` |
| OS | Windows 11 | `mvn -version` |

Aucune dépendance supplémentaire n'a été ajoutée : `gateway/pom.xml` est **inchangé** par S2-03. Le CORS s'appuie sur la configuration `spring.cloud.gateway.globalcors`, déjà supportée par Spring Cloud Gateway.

**Adaptation locale documentée :** l'énoncé officiel utilise le port `8080`. Sur cette machine, `8080` est occupé par Oracle XE, et S2-02 a retenu `8090`. Le test officiel a donc été exécuté à l'identique sur `8090`, sans modifier la politique CORS.

---

## 3. Architecture et fichiers concernés

```
Front-end React (http://localhost:3000)
        |
        |  requête navigateur + préflight OPTIONS
        v
  API Gateway (Spring Cloud Gateway, port 8090)
   - politique CORS globale (S2-03)
   - routes S2-02
        |                         |
        v                         v
service-donneurs : 8081     service-demandes : 8082
```

Le CORS est appliqué **au niveau du Gateway**, pas dans chaque microservice : c'est le seul point d'entrée exposé au navigateur, ce qui évite de dupliquer la politique et garantit un comportement homogène.

| Fichier | État | Rôle |
|---|---|---|
| `gateway/src/main/resources/application.yml` | Modifié | Ajout du bloc `spring.cloud.gateway.globalcors` |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayCorsIntegrationTest.java` | Créé | 4 tests CORS de bout en bout (préflight + réponse proxifiée) |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayCorsMultipleOriginsTest.java` | Créé | 2 tests sur plusieurs origines séparées par des virgules |
| `.env.example` | Modifié | Ajout de `CORS_ALLOWED_ORIGINS=http://localhost:3000` |
| `gateway/src/main/resources/application.yml` (routes S2-02) | Inchangé | Les 9 routes S2-02 sont préservées |
| `gateway/pom.xml` | Inchangé | Aucune dépendance ajoutée |
| `gateway/RAPPORT_S2-02.md` | Inchangé | Rapport S2-02 validé, non modifié |

Aucun fichier de S2-02 n'a été réécrit : le bloc CORS a été **inséré avant** la clé `routes`, sans toucher aux routes existantes.

---

## 4. Travail effectué

### 4.1 Configuration CORS globale (`application.yml`)

Bloc ajouté sous `spring.cloud.gateway` :

```yaml
globalcors:
  add-to-simple-url-handler-mapping: true
  cors-configurations:
    '[/**]':
      allowedOrigins: "${CORS_ALLOWED_ORIGINS:http://localhost:3000}"
      allowedMethods:
        - GET
        - POST
        - PUT
        - PATCH
        - DELETE
        - OPTIONS
      allowedHeaders:
        - Authorization
        - Content-Type
        - Accept
        - X-Request-Id
      exposedHeaders:
        - Location
        - X-Request-Id
```

- `'[/**]'` applique la politique à **tous** les chemins, y compris ceux sans route correspondante.
- `allowedOrigins` lit `CORS_ALLOWED_ORIGINS` et retombe sur `http://localhost:3000`.
- `add-to-simple-url-handler-mapping: true` garantit que le préflight (`OPTIONS`) reçoit une réponse CORS même si aucune route ne correspond au chemin.

### 4.2 Documentation de la variable (`.env.example`)

```dotenv
# Origines autorisees par la politique CORS du gateway (tache S2-03).
# Plusieurs origines peuvent etre separees par des virgules, par exemple :
# CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Aucun secret n'a été ajouté.

### 4.3 Tests automatisés ajoutés

**`GatewayCorsIntegrationTest` (4 tests)** — Gateway démarré sur un port aléatoire, avec un serveur HTTP simulé en aval :

1. préflight depuis `http://localhost:3000` → **200**, `Access-Control-Allow-Origin` exact, méthodes et en-têtes autorisés présents ;
2. préflight pour chacune des 6 méthodes autorisées (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`) → accepté ;
3. préflight depuis une origine non autorisée (`http://evil.example`) → **403** et **aucun** `Access-Control-Allow-Origin` ;
4. requête `GET` réelle avec `Origin` → réponse proxifiée **200** contenant `Access-Control-Allow-Origin` et `Access-Control-Expose-Headers` avec `Location` et `X-Request-Id`.

**`GatewayCorsMultipleOriginsTest` (2 tests)** — contexte Spring avec `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200` :

1. les deux origines configurées sont acceptées ;
2. une troisième origine (`http://localhost:5000`) est refusée (403).

Ces tests utilisent un **serveur simulé** pour la réponse proxifiée ; le préflight, lui, est traité directement par le Gateway et ne dépend d'aucun backend réel.

---

## 5. Décisions techniques et justifications

### 5.1 `globalcors` plutôt qu'un filtre programmé

La configuration déclarative `spring.cloud.gateway.globalcors` est la méthode recommandée par Spring Cloud Gateway pour une politique globale. Elle évite du code Java dédié, reste cohérente avec le style déclaratif de `application.yml` (routes S2-02) et couvre automatiquement tous les chemins via `'[/**]'`. Aucune classe `CorsWebFilter` n'a donc été nécessaire.

### 5.2 Variable `CORS_ALLOWED_ORIGINS` et origines multiples

Le nom imposé par l'énoncé est respecté. La valeur est injectée par `${CORS_ALLOWED_ORIGINS:http://localhost:3000}` : la variable d'environnement surcharge la valeur par défaut sans recompilation. Spring Boot convertit une valeur séparée par des virgules en liste d'origines ; ce comportement est vérifié par `GatewayCorsMultipleOriginsTest`. Ce choix est aussi cohérent avec la convention du dépôt : le `.env.example` de référence (`origin/config`) contient déjà `CORS_ALLOWED_ORIGINS`.

### 5.3 `allowedOrigins` et non `allowedOriginPatterns`

L'énoncé parle d'origines autorisées explicites (`http://localhost:3000`). `allowedOrigins` applique une comparaison stricte, plus sûre qu'un motif générique. Aucun joker (`*`) n'est utilisé, et `allowCredentials` n'a pas été activé : l'énoncé ne le demande pas, et une origine explicite suffit au front-end.

### 5.4 En-têtes autorisés / exposés

- **Autorisés** (envoyés par le navigateur) : `Authorization`, `Content-Type`, `Accept`, `X-Request-Id` — conformes à l'énoncé et anticipent le jeton JWT (S2-07) et le futur identifiant de requête (S2-05) sans les implémenter.
- **Exposés** (lisibles par le navigateur) : `Location` (créations `201` avec en-tête `Location`) et `X-Request-Id` (traçabilité S2-05).

Aucune autre méthode, origine ou en-tête n'a été ajouté au-delà de l'énoncé.

### 5.5 Aucune modification de S2-02

Les 9 routes, les variables `DONNEURS_URL` / `DEMANDES_URL`, le port 8090 et le test de santé restent identiques. Le bloc CORS est purement additif. Aucun `StripPrefix`, `RewritePath` ou autre filtre de route n'a été introduit.

---

## 6. Tests exécutés et résultats réels

### 6.1 Build et tests automatisés

Commande réellement exécutée dans `gateway/`, le **18/11/2026** :

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp clean verify
```

Résultat réel :

```
EXITCODE=0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in tn.iit.blood.gateway.GatewayApplicationTests
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in tn.iit.blood.gateway.GatewayCorsIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in tn.iit.blood.gateway.GatewayCorsMultipleOriginsTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in tn.iit.blood.gateway.GatewayRoutesConfigurationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- in tn.iit.blood.gateway.GatewayRoutingIntegrationTest
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Bilan : 24 tests exécutés, 24 réussis, 0 échec, 0 erreur, 0 ignoré.** Les 18 tests de S2-02 restent tous verts ; 6 tests CORS ont été ajoutés.

| Classe de test | Avant S2-03 | Après S2-03 | Réussis |
|---|---|---|---|
| `GatewayApplicationTests` | 1 | 1 | 1 |
| `GatewayRoutesConfigurationTest` | 5 | 5 | 5 |
| `GatewayRoutingIntegrationTest` | 12 | 12 | 12 |
| `GatewayCorsIntegrationTest` | — | **4** | 4 |
| `GatewayCorsMultipleOriginsTest` | — | **2** | 2 |
| **Total** | **18** | **24** | **24** |

### 6.2 Test de préflight réel demandé par S2-03

Gateway démarré avec `java -jar target\gateway-0.0.1-SNAPSHOT.jar` (log : `Netty started on port 8090`). Commande exécutée (port local 8090 au lieu de 8080) :

```powershell
curl.exe -s -i -X OPTIONS "http://localhost:8090/api/v1/donneurs" `
  -H "Origin: http://localhost:3000" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: content-type,authorization"
```

Réponse réelle :

```
HTTP/1.1 200 OK
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
Access-Control-Allow-Headers: content-type, authorization
Access-Control-Expose-Headers: Location, X-Request-Id
content-length: 0
```

Le critère d'acceptation est **satisfait** : la réponse contient `Access-Control-Allow-Origin: http://localhost:3000`.

### 6.3 Origine refusée (vérification complémentaire)

```powershell
curl.exe -s -i -X OPTIONS "http://localhost:8090/api/v1/donneurs" `
  -H "Origin: http://evil.example" -H "Access-Control-Request-Method: POST"
```

Réponse réelle :

```
HTTP/1.1 403 Forbidden
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
content-length: 0
```

Aucun `Access-Control-Allow-Origin` n'est renvoyé : le navigateur bloquera l'appel. La politique n'est donc pas permissive à tort.

### 6.4 Santé et arrêt propre

```powershell
curl.exe -s -i http://localhost:8090/actuator/health
```

```
HTTP/1.1 200 OK
Content-Type: application/vnd.spring-boot.actuator.v3+json
Content-Length: 15

{"status":"UP"}
```

Après arrêt : `Get-NetTCPConnection -LocalPort 8090` ne renvoie **aucune connexion** et `Get-Process java` ne renvoie **aucun processus**. Aucune erreur sur la sortie standard d'erreur.

### 6.5 Nature exacte des tests

| Test | Nature | Backend réel ? |
|---|---|---|
| Préflight CORS (200 / 403) | Réel, contre le Gateway démarré | Aucun backend nécessaire (préflight traité par le Gateway) |
| En-têtes CORS sur réponse proxifiée | Réel, contre le Gateway + **serveur simulé** | Non |
| Multi-origines | Réel, contexte Spring complet | Aucun backend nécessaire (préflight) |
| 9 routes S2-02 | Serveur simulé | Non |
| `/actuator/health` | Réel | Sans objet |

**Aucun test contre les vrais microservices n'a été réalisé**, car ils ne sont pas disponibles localement (voir section 8).

---

## 7. Problèmes rencontrés et solutions

| Problème | Solution appliquée | Vérification |
|---|---|---|
| L'énoncé officiel utilise le port `8080`, occupé localement par Oracle XE | Test exécuté à l'identique sur `8090`, port déjà retenu en S2-02 ; adaptation documentée | Préflight 200 sur 8090 |
| Le préflight doit fonctionner même sans route ou sans backend | `add-to-simple-url-handler-mapping: true` et configuration `'[/**]'` | Préflight 200 sans microservice |
| `CORS_ALLOWED_ORIGINS` est une variable « plurielle » | Valeur unique par défaut, conversion Spring Boot des listes séparées par virgules | `GatewayCorsMultipleOriginsTest` : 2 origines acceptées, 1 refusée |
| Vérifier aussi les en-têtes CORS d'une réponse réelle (non préflight) | Serveur HTTP simulé + requête `GET` avec `Origin` | `proxiedResponseExposesTheConfiguredHeaders` : exposés `Location`, `X-Request-Id` |
| Aucun backend réel disponible pour un test de bout en bout | Tests limités au préflight et à un serveur simulé, clairement identifiés | Section 8 |

Aucun problème non résolu dans le périmètre S2-03.

---

## 8. Limites et éléments restant à valider

- **Intégration avec les vrais microservices : non réalisée.** Les dossiers `service-donneurs`, `service-demandes`, `service-don-stock`, `service-partenaire` ne contiennent qu'un `.gitkeep` en local et aucun service n'était démarré. Les réponses proxifiées ont été validées avec un **serveur simulé**, pas avec les vrais services.
- **Navigateur réel (front-end React) : non testé.** Le critère d'acceptation est vérifié au niveau HTTP (en-tête `Access-Control-Allow-Origin`), pas via une application navigateur exécutée.
- **Origines de production :** seule la valeur locale `http://localhost:3000` est configurée. Les origines Docker/production devront être fournies via `CORS_ALLOWED_ORIGINS` au déploiement (S2-11).
- **Authentification / `allowCredentials` :** non activés ; le jeton JWT et les règles de rôle relèvent de S2-07/S2-08 et ne sont pas testés ici.
- **SSE (`/api/v1/stock/niveaux/flux`) :** le CORS s'applique à la route, mais le flux temps réel et les délais relèvent de S2-04 et n'ont pas été testés.
- **`Access-Control-Expose-Headers` sur préflight :** observé dans la réponse réelle du préflight ; le comportement déterminant pour le navigateur reste celui de la réponse réelle, couvert par test avec le serveur simulé.

---

## 9. Procédure pour reproduire les tests

```powershell
# Depuis le dossier gateway/
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 1) Build + tous les tests (24 attendus)
mvn -B -ntp clean verify

# 2) Démarrage du Gateway (port 8090)
java -jar target\gateway-0.0.1-SNAPSHOT.jar

# 3) Dans un autre terminal : préflight CORS (critère S2-03)
curl.exe -s -i -X OPTIONS "http://localhost:8090/api/v1/donneurs" `
  -H "Origin: http://localhost:3000" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: content-type,authorization"
# Attendu : HTTP/1.1 200 OK + Access-Control-Allow-Origin: http://localhost:3000

# 4) Origine refusée (attendu : HTTP/1.1 403 Forbidden)
curl.exe -s -i -X OPTIONS "http://localhost:8090/api/v1/donneurs" `
  -H "Origin: http://evil.example" -H "Access-Control-Request-Method: POST"

# 5) Santé
curl.exe -s -i http://localhost:8090/actuator/health
# Attendu : HTTP/1.1 200 OK + {"status":"UP"}

# 6) Arrêt : Ctrl+C, ou arrêt du PID affiché au démarrage
```

Pour changer les origines autorisées sans recompiler :

```powershell
$env:CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://localhost:4200"
java -jar target\gateway-0.0.1-SNAPSHOT.jar
```

---

## 10. Conclusion et état final de S2-03

**S2-03 est terminée localement pour le composant Gateway.**

- La politique **CORS globale** est configurée dans `gateway/src/main/resources/application.yml` (`globalcors`), avec les origines issues de `CORS_ALLOWED_ORIGINS` (défaut `http://localhost:3000`), les 6 méthodes, les 4 en-têtes autorisés et les 2 en-têtes exposés exigés.
- Le **critère d'acceptation officiel est satisfait** : le préflight réel renvoie `HTTP/1.1 200 OK` avec `Access-Control-Allow-Origin: http://localhost:3000`, ainsi que les méthodes, en-têtes autorisés et en-têtes exposés attendus.
- Une origine non autorisée est correctement rejetée (**403**, aucun en-tête CORS).
- `mvn -B -ntp clean verify` (JDK 17.0.12, Maven 3.9.6) donne **EXITCODE=0**, **BUILD SUCCESS**, **24 tests réussis sur 24** (18 de S2-02 préservés + 6 nouveaux).
- La Gateway démarre réellement sur **8090** et `/actuator/health` répond **200 OK** avec `{"status":"UP"}` ; l'arrêt ne laisse ni port occupé ni processus Java.
- `.env.example` documente `CORS_ALLOWED_ORIGINS`. Aucune dépendance ni fichier S2-02 n'a été modifié.

**Réserve principale :** l'intégration avec les vrais microservices et un navigateur réel n'a pas été testée, car les services ne sont pas disponibles localement. Les tests de réponse proxifiée utilisent un serveur simulé, ce qui est explicitement indiqué.

**Hors périmètre, non commencé :** S2-04 (timeouts), S2-05 (logs / `X-Request-Id`), S2-06 (erreurs JSON), S2-07/S2-08 (JWT et rôles), S2-09 (limitation de débit).

Aucun commit ni push n'a été effectué. S2-03 attend la validation explicite.
