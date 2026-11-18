# Rapport technique — S2-02 : configuration des routes du Gateway

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après corrections. Les tests et le test de santé ont été **ré-exécutés réellement le 18/11/2026** dans le cadre de cette mission ; les sorties citées proviennent de ces exécutions. Les éléments non vérifiés sont explicitement signalés. Aucun test simulé n'est présenté comme un test contre un vrai microservice.

---

## 1. Introduction et objectifs de S2-02

Le projet est une architecture orientée services (SOA) de gestion des dons de sang, composée de plusieurs microservices Spring Boot (donneurs, demandes, don & stock, CNTS, partenaire) et d'un front-end.

L'**API Gateway** est le point d'entrée unique côté client. Elle reçoit les requêtes HTTP, évalue des prédicats (le chemin d'URL) et les relaie vers le microservice compétent, sans logique métier.

**Objectif de S2-02 :** configurer les **neuf routes** métier, externaliser les destinations via des variables d'environnement, garantir la priorité de la route `centres/{id}/creneaux`, conserver les préfixes d'URI (aucun `StripPrefix` non justifié), puis vérifier le tout par des tests automatisés et un test de santé réel.

---

## 2. Environnement technique et versions vérifiées

| Élément | Valeur vérifiée | Preuve |
|---|---|---|
| JDK utilisé pour les builds | **17.0.12** (Oracle, `C:\Program Files\Java\jdk-17`) | `java -version` et `mvn -version` |
| Maven | **3.9.6** (Maven home `C:\tools\apache-maven-3.9.6`) | `mvn -version` |
| Spring Boot | **3.3.13** | `pom.xml` |
| Spring Cloud | **2023.0.6** (train Leyton) | `pom.xml` |
| Niveau Java | `release 17` | logs de compilation |
| Port de la Gateway | **8090** | `application.yml` et log `Netty started on port 8090` |
| Serveur | Netty / WebFlux (réactif) | dépendances et logs |
| OS | Windows 11 | `mvn -version` |

Le JDK 17 est imposé via `JAVA_HOME` pour toutes les commandes Maven et Java. Aucune dépendance supplémentaire n'a été ajoutée ; `pom.xml` reste inchangé.

---

## 3. Architecture de la Gateway et rôle dans le projet SOA

```
Client / Front-end
        |
        v
  API Gateway (Spring Cloud Gateway, port 8090)
        |                         |
        v                         v
service-donneurs : 8081     service-demandes : 8082
(donneurs, centres,         (demandes, appels,
 rendez-vous, dons,          stock HTTP)
 certificats)
```

- La Gateway est **réactive** (WebFlux/Netty), sans Spring MVC.
- Chaque route associe un **prédicat `Path`** à une **URI cible**.
- Les adresses des services sont **externalisées** : aucune n'est codée en dur dans le code Java.
- La Gateway ne porte **aucune** logique métier, sécurité, CORS ou limitation de débit en S2-02.

---

## 4. Tableau des neuf routes

| # | Identifiant | Prédicat `Path` | Destination par défaut | Variable d'environnement | Ordre |
|---|---|---|---|---|---|
| 1 | `donneurs-centres-creneaux` | `/api/v1/centres/{id}/creneaux` | `http://localhost:8081` | `DONNEURS_URL` | **-1** |
| 2 | `donneurs-centres` | `/api/v1/centres/**` | `http://localhost:8081` | `DONNEURS_URL` | 0 (défaut) |
| 3 | `donneurs-donneurs` | `/api/v1/donneurs/**` | `http://localhost:8081` | `DONNEURS_URL` | 0 (défaut) |
| 4 | `donneurs-rendez-vous` | `/api/v1/rendez-vous/**` | `http://localhost:8081` | `DONNEURS_URL` | 0 (défaut) |
| 5 | `donneurs-dons` | `/api/v1/dons/**` | `http://localhost:8081` | `DONNEURS_URL` | 0 (défaut) |
| 6 | `donneurs-certificats` | `/api/v1/certificats/**` | `http://localhost:8081` | `DONNEURS_URL` | 0 (défaut) |
| 7 | `demandes-demandes` | `/api/v1/demandes/**` | `http://localhost:8082` | `DEMANDES_URL` | 0 (défaut) |
| 8 | `demandes-appels` | `/api/v1/appels/**` | `http://localhost:8082` | `DEMANDES_URL` | 0 (défaut) |
| 9 | `demandes-stock` | `/api/v1/stock/**` | `http://localhost:8082` | `DEMANDES_URL` | 0 (défaut) |

Aucun filtre `StripPrefix` n'est déclaré.

---

## 5. Modifications réalisées

| Chemin exact | Action | Contenu / rôle |
|---|---|---|
| `gateway/src/main/resources/application.yml` | Modifié | 9 routes, variables d'environnement, port 8090, Actuator |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayRoutesConfigurationTest.java` | Créé | 5 tests de configuration des routes |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayRoutingIntegrationTest.java` | Créé | 12 cas de routage HTTP de bout en bout (serveur simulé) |
| `gateway/src/main/java/tn/iit/blood/gateway/GatewayApplication.java` | Modifié | Correction du commentaire obsolète « port 8080 » → port configurable via `application.yml` (8090) |
| `.env.example` | Modifié | Documentation des variables `DONNEURS_URL` et `DEMANDES_URL` |
| `gateway/pom.xml` | Inchangé | Aucune dépendance ajoutée |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayApplicationTests.java` | Inchangé | Test de chargement du contexte |

Extrait représentatif de `application.yml` (les 9 routes suivent le même modèle) :

```yaml
server:
  port: 8090

spring:
  cloud:
    gateway:
      routes:
        - id: donneurs-centres-creneaux
          uri: "${DONNEURS_URL:http://localhost:8081}"
          order: -1
          predicates:
            - "Path=/api/v1/centres/{id}/creneaux"

        - id: donneurs-centres
          uri: "${DONNEURS_URL:http://localhost:8081}"
          predicates:
            - "Path=/api/v1/centres/**"

        # ... 4 autres routes Donneurs ...

        - id: demandes-demandes
          uri: "${DEMANDES_URL:http://localhost:8082}"
          predicates:
            - "Path=/api/v1/demandes/**"

        # ... demandes-appels et demandes-stock ...
```

---

## 6. Explication des choix techniques

### 6.1 Priorité de `/api/v1/centres/{id}/creneaux`

Le prédicat `/api/v1/centres/**` capture tous les sous-chemins, dont `/api/v1/centres/5/creneaux`. La route spécifique `donneurs-centres-creneaux` est donc déclarée avant et reçoit `order: -1` : dans Spring Cloud Gateway, **un ordre plus petit = priorité plus élevée**. Cela garantit que la route spécifique est évaluée en premier.

### 6.2 Absence de `StripPrefix`

Aucun `StripPrefix` n'est appliqué. Le contrat OpenAPI des donneurs (`contracts/openapi/donneurs.yaml`, branche `feature/service-donneurs/S3-01-openapi-contract`) définit explicitement les chemins `/api/v1/donneurs`, `/api/v1/centres`, `/api/v1/rendez-vous`, `/api/v1/dons`, `/api/v1/certificats`. Le contrôleur Demandes implémente `@RequestMapping("/api/v1/demandes")`.

Retirer un préfixe transformerait ces chemins et provoquerait des **404**. La Gateway conserve donc l'URI telle quelle : **chemin reçu = chemin transmis**. Aucun `RewritePath` n'a non plus été ajouté, car il contredirait le contrat OpenAPI.

### 6.3 Variables d'environnement

La syntaxe `${DONNEURS_URL:http://localhost:8081}` utilise la variable si elle existe, sinon la valeur par défaut. Les noms `DONNEURS_URL` / `DEMANDES_URL` sont ceux du dépôt (gateway de référence `origin/config` et son `.env.example`) ; ils ont donc été conservés et documentés dans `.env.example`. Aucun renommage vers `*_SERVICE_URL` n'a été effectué, faute de confirmation d'une convention différente.

### 6.4 Responsabilité de `/api/v1/stock/**`

La route `demandes-stock` pointe vers `DEMANDES_URL`. Ce choix est étayé par trois éléments accessibles :

1. la consigne S2-02 affecte `/api/v1/stock/**` à Demandes ;
2. la gateway de référence `origin/config` route `/api/v1/stock/**` vers `service-demandes` ;
3. `service-don-stock` n'expose **aucun `@RestController`** : c'est un service **gRPC** (gRPC sur 9090, HTTP 8083 réservé à Actuator). Il ne peut donc pas être la cible d'une route HTTP REST.

Limite : `service-demandes` ne possède pas encore de contrôleur REST `/api/v1/stock` (seulement un client gRPC `DonStockClient`). La route est correctement configurée, mais l'endpoint HTTP reste à implémenter côté service pour un appel réel réussi.

---

## 7. Tests exécutés, commandes, résultats et preuves

### 7.1 Commandes réellement exécutées (18/11/2026)

| Commande exacte | Répertoire | Java | Résultat |
|---|---|---|---|
| `mvn -version` | `...\SOA_Proj\gateway` | 17.0.12 | Maven 3.9.6, runtime JDK 17 |
| `mvn -B -ntp clean verify` | `...\SOA_Proj\gateway` | 17.0.12 | **BUILD SUCCESS**, code de sortie **0** |
| `java -jar target\gateway-0.0.1-SNAPSHOT.jar` | `...\SOA_Proj\gateway` | 17.0.12 | Démarrage, `Netty started on port 8090` |
| `curl.exe -s -i http://localhost:8090/actuator/health` | `...\SOA_Proj\gateway` | — | **HTTP/1.1 200 OK**, `{"status":"UP"}` |
| `Stop-Process` + `Get-NetTCPConnection` + `Get-Process java` | — | — | Port 8090 libre, aucun processus Java restant |

Extrait réel de la sortie Maven :

```
[INFO] Compiling 1 source file with javac [debug parameters release 17] to target\classes
[INFO] Compiling 3 source files with javac [debug parameters release 17] to target\test-classes
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.072 s -- in tn.iit.blood.gateway.GatewayApplicationTests
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.232 s -- in tn.iit.blood.gateway.GatewayRoutesConfigurationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.273 s -- in tn.iit.blood.gateway.GatewayRoutingIntegrationTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Bilan : 18 tests exécutés, 18 réussis, 0 échec, 0 erreur, 0 ignoré.**

### 7.2 Ce que vérifient les tests

**`GatewayRoutesConfigurationTest` (5 tests) :**

1. les **9 identifiants** de routes existent et le total vaut exactement 9 ;
2. les 6 routes Donneurs pointent vers la cible surchargée `http://donneurs.test:19081` ;
3. les 3 routes Demandes pointent vers `http://demandes.test:19082` ;
4. chaque prédicat `Path` correspond au motif attendu ;
5. l'ordre de `creneaux` (-1) est strictement inférieur à celui de `centres` (0).

Ces tests prouvent que les destinations sont **surchargeables** sans modification du code.

**`GatewayRoutingIntegrationTest` (12 cas paramétrés) :**

- Gateway démarré sur un **port aléatoire** (`RANDOM_PORT`, observé `54950` lors du dernier run) ;
- **serveur HTTP simulé** (JDK `HttpServer`) répondant `proxied:<chemin>` ;
- 12 chemins envoyés via `WebTestClient`, attendus **200** avec le corps exact `proxied:<chemin>`.

**Les requêtes ont réellement atteint le serveur simulé : oui.** Le corps `proxied:<chemin>` n'est produit que par le handler du serveur simulé ; l'égalité a réussi pour les 12 cas. Cela prouve le passage à travers la Gateway et la conservation du chemin. **Il ne s'agit pas d'un test contre les vrais microservices.**

---

## 8. Résultat du test de santé

Commande de démarrage : `java -jar target\gateway-0.0.1-SNAPSHOT.jar` avec le JDK 17. Logs réels :

```
Starting GatewayApplication v0.0.1-SNAPSHOT using Java 17.0.12 with PID 15448
Netty started on port 8090 (http)
Started GatewayApplication in 3.792 seconds
```

Commande HTTP : `curl.exe -s -i http://localhost:8090/actuator/health`. Sortie réelle :

```
HTTP/1.1 200 OK
Content-Type: application/vnd.spring-boot.actuator.v3+json
Content-Length: 15

{"status":"UP"}
```

Après le test : `Get-NetTCPConnection -LocalPort 8090` ne renvoie **aucune connexion**, et `Get-Process java` ne renvoie **aucun processus**. L'arrêt est donc propre, sans processus orphelin. Aucune erreur n'a été écrite sur la sortie d'erreur.

---

## 9. Problèmes détectés et corrections effectuées

### 9.1 Corrections effectuées dans le périmètre S2-02

| Problème | Correction | Vérification |
|---|---|---|
| Commentaire de `GatewayApplication.java` indiquant encore « port 8080 » | Remplacé par une mention du port configuré dans `application.yml` (8090) | Build et tests relancés : OK |
| `.env.example` vide alors que la Gateway dépend de variables | Ajout de `DONNEURS_URL` et `DEMANDES_URL` avec commentaire | Fichier relu |

### 9.2 Incohérences confirmées, non corrigées côté Gateway (justification)

1. **Donneurs — chemin du contrôleur vs contrat.** Le contrat OpenAPI (`donneurs.yaml`, S3-01) impose `/api/v1/...` ; le `DonneurController` actuel (branches `develop` et `s3-03-s3-04-flyway-eligibility`) mappe encore `/api/donneurs`. La Gateway est **conforme au contrat** et conserve `/api/v1/...` : la correction relève du service Donneurs (tâche S3), pas du routage. Aucun `StripPrefix`/`RewritePath` n'a été ajouté, car cela contredirait le contrat.
2. **Stock.** `service-don-stock` est un service **gRPC** sans contrôleur REST ; `service-demandes` est la cible HTTP retenue par la consigne et la gateway de référence. La route est correcte, mais l'endpoint REST `/api/v1/stock` reste à implémenter côté Demandes.
3. **`StripPrefix=2` de la gateway de référence (`origin/config`).** Non repris : il produirait `/donneurs` ou `/demandes`, incompatible avec les contrôleurs et le contrat.
4. **Port 8080.** Occupé sur la machine par `OracleXETNSListener` ; le port **8090** a été conservé.
5. **Port de `service-don-stock`.** `application.properties` indique HTTP 8083 (Actuator) et gRPC 9090 ; le `docker-compose.yml` de référence force `SERVER_PORT=9090`, ce qui entre en conflit avec le port gRPC. Incohérence d'infrastructure à traiter hors S2-02.

Aucune correction n'a été appliquée aux microservices : ils ne sont pas présents localement (uniquement `.gitkeep`) et sortent du périmètre S2-02.

---

## 10. Limites restantes et éléments non vérifiés

- **Intégration avec les vrais microservices : non réalisée.** Les dossiers `service-donneurs`, `service-demandes`, `service-don-stock` et `service-partenaire` ne contiennent qu'un `.gitkeep` en local ; aucun service n'était démarré. Les tests de routage utilisent un **serveur simulé**.
- **Correspondance runtime** entre les chemins de la Gateway et les contrôleurs réels : **non vérifiée**.
- **`/api/v1/stock` réel** : **non testé** (pas de contrôleur REST stock côté Demandes).
- **Accessibilité de `localhost:8081` et `localhost:8082`** : **non vérifiée** (services absents).
- **Environnement Docker / noms de conteneurs** : **non testé**.
- **Tests de charge, sécurité, CORS, timeouts, gestion d'erreurs personnalisée** : hors périmètre S2-02, non réalisés.
- Le contenu des branches Git distantes a été **lu en lecture seule**, jamais exécuté.

---

## 11. Procédure permettant de reproduire les tests

```powershell
# Depuis le dossier gateway/
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 1) Build + tests (18 tests attendus)
mvn -B -ntp clean verify

# 2) Démarrage de la Gateway (port 8090)
java -jar target\gateway-0.0.1-SNAPSHOT.jar

# 3) Dans un autre terminal : test de santé
curl.exe -i http://localhost:8090/actuator/health
# Attendu : HTTP/1.1 200 OK puis {"status":"UP"}

# 4) Arrêt : Ctrl+C dans le terminal du jar, ou Stop-Process sur le PID affiché
```

Pour surcharger les destinations sans recompiler :

```powershell
$env:DONNEURS_URL = "http://localhost:8081"
$env:DEMANDES_URL = "http://localhost:8082"
```

---

## 12. Conclusion

S2-02 est **terminée localement** pour le composant Gateway :

- les **9 routes** sont configurées avec des prédicats `Path` conformes, des destinations externalisées et la priorité `order: -1` sur `centres/{id}/creneaux` ;
- aucun `StripPrefix` n'est appliqué, conformément au contrat OpenAPI `/api/v1/...` ;
- `mvn -B -ntp clean verify` avec JDK 17.0.12 donne **BUILD SUCCESS**, code de sortie **0**, **18 tests réussis sur 18** ;
- la Gateway démarre réellement sur **8090** (`Netty started on port 8090`) et `/actuator/health` répond **200 OK** avec `{"status":"UP"}` ; l'arrêt ne laisse ni port occupé ni processus Java.

**Réserve principale :** l'intégration avec les vrais microservices n'a pas été testée, car ils ne sont pas disponibles localement. La divergence `/api/donneurs` du contrôleur Donneurs et l'absence d'endpoint REST stock côté Demandes sont **documentées** et relèvent des tâches services, pas de la configuration Gateway.

**Prochaines étapes proposées (sans commencer S2-03) :** aligner le contrôleur Donneurs sur le contrat `/api/v1/...`, implémenter l'endpoint REST stock côté Demandes, puis réaliser un test d'intégration réel lorsque les services seront disponibles.

---

## Annexe A — `application.yml` complet (état final)

```yaml
server:
  port: 8090

spring:
  application:
    name: gateway

  cloud:
    gateway:
      routes:
        - id: donneurs-centres-creneaux
          uri: "${DONNEURS_URL:http://localhost:8081}"
          order: -1
          predicates:
            - "Path=/api/v1/centres/{id}/creneaux"

        - id: donneurs-centres
          uri: "${DONNEURS_URL:http://localhost:8081}"
          predicates:
            - "Path=/api/v1/centres/**"

        - id: donneurs-donneurs
          uri: "${DONNEURS_URL:http://localhost:8081}"
          predicates:
            - "Path=/api/v1/donneurs/**"

        - id: donneurs-rendez-vous
          uri: "${DONNEURS_URL:http://localhost:8081}"
          predicates:
            - "Path=/api/v1/rendez-vous/**"

        - id: donneurs-dons
          uri: "${DONNEURS_URL:http://localhost:8081}"
          predicates:
            - "Path=/api/v1/dons/**"

        - id: donneurs-certificats
          uri: "${DONNEURS_URL:http://localhost:8081}"
          predicates:
            - "Path=/api/v1/certificats/**"

        - id: demandes-demandes
          uri: "${DEMANDES_URL:http://localhost:8082}"
          predicates:
            - "Path=/api/v1/demandes/**"

        - id: demandes-appels
          uri: "${DEMANDES_URL:http://localhost:8082}"
          predicates:
            - "Path=/api/v1/appels/**"

        - id: demandes-stock
          uri: "${DEMANDES_URL:http://localhost:8082}"
          predicates:
            - "Path=/api/v1/stock/**"

management:
  endpoints:
    web:
      exposure:
        include: health
```

## Annexe B — Résultats détaillés des tests

| Classe de test | Tests | Réussis | Échecs | Erreurs | Ignorés |
|---|---|---|---|---|---|
| `GatewayApplicationTests` | 1 | 1 | 0 | 0 | 0 |
| `GatewayRoutesConfigurationTest` | 5 | 5 | 0 | 0 | 0 |
| `GatewayRoutingIntegrationTest` | 12 | 12 | 0 | 0 | 0 |
| **Total** | **18** | **18** | **0** | **0** | **0** |

Ces résultats proviennent du `mvn -B -ntp clean verify` exécuté le **18/11/2026** (code de sortie 0).

---

**Fin du rapport.** Aucun commit ni push n'a été effectué. Aucune fonctionnalité hors S2-02 n'a été ajoutée. S2-03 n'a pas été commencée.
