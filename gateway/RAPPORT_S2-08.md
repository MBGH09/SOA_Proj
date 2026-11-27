# Rapport technique — S2-08 : règles d'autorisation par rôle

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après réalisation de S2-08. Le build et les tests automatisés ont été **réellement exécutés le 18/11/2026** ; les chiffres cités proviennent des sorties conservées de ces exécutions. La vérification manuelle a elle aussi été réellement exécutée (gateway 8090 + backend HTTP simulé 18083). Les « microservices » de cette vérification sont un **serveur simulé, hors dépôt** : les dossiers réels des microservices ne contiennent encore qu'un `.gitkeep`, et **aucun test contre les vrais microservices n'est revendiqué**.

---

## 1. Exigences officielles et leur source

### 1.1 Source

L'étape est décrite dans le guide officiel `docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`, branche `origin/develop`, section **« S2-08 Role rules »** (lignes 286 à 304 de la version consultée).

Le guide donne trois exigences d'implémentation explicites :

1. **Supprimer** tout en-tête `X-User-Id` et `X-User-Role` qui arrive de l'extérieur.
2. **Après vérification du jeton**, ajouter `X-User-Id` et `X-User-Role` à la requête transmise. Le service Donneurs s'en sert pour les règles de propriété (« un donneur ne lit que son propre don ou certificat »), car **seul Donneurs sait qui possède un don**.
3. Retourner un **401 JSON** (pas de jeton) ou un **403** (rôle incorrect).

Critère de validation officiel (« Done when ») : **un jeton donneur reçoit 403 sur `POST /dons`, et un jeton staff reçoit 200.**

### 1.2 Tableau officiel des routes

| Route | Qui peut l'appeler |
| --- | --- |
| `GET /centres`, `GET /centres/{id}/creneaux`, `GET /appels` | tout le monde (public) |
| `GET /certificats/{numero}` | tout le monde (validité, date, centre uniquement) |
| `POST /donneurs` (inscription) | tout le monde (un nouveau donneur doit pouvoir s'inscrire) |
| `/donneurs/{id}/**`, `/rendez-vous/**` | `DONOR` pour ses propres données, `STAFF_CENTRE`, `ADMIN` |
| `POST /dons`, étapes `entretien`, `prelevement`, `repos`, `POST /donneurs/{id}/ajournements`, `POST /centres/{id}/creneaux` | `STAFF_CENTRE`, `ADMIN` |
| `GET /dons/{id}` (contient les données d'entretien) | `STAFF_CENTRE`, `ADMIN` |
| `GET /dons/{id}/certificat` | le donneur de ce don, `STAFF_CENTRE`, `ADMIN` |
| `POST /demandes`, `PATCH /demandes/{id}`, `POST /demandes/{id}/compensations`, `POST` et `PATCH /appels` | `HOPITAL`, `ADMIN` |
| `GET /stock/**` | `HOPITAL`, `STAFF_CENTRE`, `ADMIN` |

Les quatre rôles officiels sont `DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN`. Les préfixes d'API réels du dépôt sont `/api/v1/**` : les règles ci-dessus y sont appliquées telles quelles.

### 1.3 Décision de périmètre : gap 7 différé

Les JWT émis par S2-07 ne contiennent que `sub`, `role`, `iat`, `exp`. La consigne de mission interdit d'ajouter `donneurId` ou `hopitalId` sans décision explicite sur le gap 7.

Conséquence assumée et documentée : le gateway applique **exactement les règles de rôle**, et **délègue les règles de propriété** au service Donneurs via `X-User-Id`/`X-User-Role`, conformément au point 2 du guide. `X-User-Id` transporte le `sub` (nom de connexion) et **n'est pas présenté comme un identifiant métier de donneur ou d'hôpital**. Les deux règles « pour ses propres données » et « le donneur de ce don » ne sont donc **pas** garanties par le gateway (voir § 8).

---

## 2. Modifications réalisées

### 2.1 Matrice d'autorisation (`SecurityConfiguration`)

La chaîne de filtres existante (sans état, sans CSRF, sans formulaire, CORS activé) est conservée. Le bloc `authorizeExchange` est remplacé par la matrice complète du tableau officiel :

- routes publiques S2-08 déclarées **avant** toute règle générique ;
- `GET /api/v1/dons/*/certificat` déclaré **avant** `GET /api/v1/dons/**`, sinon la règle STAFF/ADMIN l'emporterait et interdisrait le donneur ;
- `POST /api/v1/donneurs` public et `POST /api/v1/donneurs/*/ajournements` STAFF/ADMIN déclarés **avant** `/api/v1/donneurs/**`, sinon la règle DONOR les capturerait ;
- `GET /api/v1/dons/**` (donc `GET /dons/{id}`) réservé à `STAFF_CENTRE` et `ADMIN` ;
- `PATCH /api/v1/appels/*` réservé à `HOPITAL` et `ADMIN` ;
- toute route non listée reste `authenticated()` (au moins un jeton valide) ;
- `gateway.security.permit-all` reste l'échappatoire réservée aux tests (voir § 8.4).

Le convertisseur JWT est inchangé : le claim `role` devient l'autorité `ROLE_<role>`, ce qui permet d'utiliser `hasAnyRole(...)`. Aucun rôle n'est lu ailleurs que dans le jeton vérifié.

### 2.2 Réponse 403 JSON (`JsonAccessDeniedHandler`, nouveau)

Un utilisateur authentifié mais non autorisé reçoit un **403** produit par un `ServerAccessDeniedHandler` dédié, avec exactement le contrat d'erreur existant (`timestamp`, `status`, `error`, `message`, `path`, `requestId`) :

```json
{
  "timestamp": "2026-11-18T19:12:01.862971300+01:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Acces refuse : le role de l'utilisateur ne permet pas cette operation.",
  "path": "/api/v1/dons",
  "requestId": "manual-s208-403"
}
```

Le 403 ne porte **pas** `WWW-Authenticate` : le client est authentifié, seul son rôle ne permet pas l'opération. Le 401 conserve ce défi (`Bearer`).

### 2.3 En-têtes d'identité (`UserIdentityHeaderFilter`, nouveau)

Filtre global de plus haute priorité (`HIGHEST_PRECEDENCE + 10`), exécuté avant le routage :

1. suppression **inconditionnelle** de `X-User-Id` et `X-User-Role` fournis par le client, y compris sur les routes publiques ;
2. si — et seulement si — l'authentification courante est un jeton JWT vérifié, réécriture des deux en-têtes à partir de `sub` et `role` ;
3. dans tous les autres cas (anonyme, route publique), les en-têtes restent absents.

Aucun en-tête du client ne peut donc usurper une identité ou un rôle.

### 2.4 401 rendu totalement paresseux (`JsonAuthenticationEntryPoint`)

Le point d'entrée 401 existant réutilise désormais `RequestIdSupport` (identifiant partagé avec le 403). Surtout, son effet de bord est déplacé dans un `Mono.defer`.

**Problème réel détecté pendant la vérification manuelle :** sur chaque 403, le journal affichait aussi une ligne 401, et le risque existait de voir un statut ou un en-tête `WWW-Authenticate` parasites. Cause : `ExceptionTranslationWebFilter` construit le `Mono` du point d'entrée comme **repli du 403** avant de savoir s'il servira. Le `Mono.defer` garantit que le 401 n'écrit que s'il est réellement souscrit. Après correctif : **31 403, 9 401**, tous légitimes (voir § 7.4).

### 2.5 Centralisation des rôles (`UserRoles`, nouveau)

Constantes `DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN` utilisées par la matrice **et** par `DemoUserService` (qui construisait auparavant ses rôles avec des chaînes littérales). Une seule source de vérité pour les noms de rôles.

### 2.6 Ce qui n'a pas été modifié

- Aucun claim JWT ajouté (`sub`, `role`, `iat`, `exp` uniquement).
- Aucune route, aucun timeout, aucune règle CORS modifiés.
- `/actuator/health`, `/api/v1/auth/**` et le preflight `OPTIONS` restent publics.
- Les tests et rapports S2-02 à S2-07 sont intacts.

---

## 3. Règles d'accès par rôle (tableau)

`oui` = accès transmis au service ; `403` = refus JSON ; `public` = accessible sans jeton. Les identifiants littéraux sont ceux des tests (`42`, `7`, `1`, `9`, `3`), les règles utilisant des jokers.

| Route | Public | `DONOR` | `STAFF_CENTRE` | `HOPITAL` | `ADMIN` |
| --- | --- | --- | --- | --- | --- |
| `GET /api/v1/centres` | public | — | — | — | — |
| `GET /api/v1/centres/{id}/creneaux` | public | — | — | — | — |
| `GET /api/v1/appels` | public | — | — | — | — |
| `GET /api/v1/certificats/{numero}` | public | — | — | — | — |
| `POST /api/v1/donneurs` | public | — | — | — | — |
| `/api/v1/donneurs/{id}/**` | non | oui (1) | oui | 403 | oui |
| `/api/v1/rendez-vous/**` | non | oui (1) | oui | 403 | oui |
| `POST /api/v1/dons` | non | 403 | oui | 403 | oui |
| `POST /api/v1/dons/{id}/entretien` | non | 403 | oui | 403 | oui |
| `POST /api/v1/dons/{id}/prelevement` | non | 403 | oui | 403 | oui |
| `POST /api/v1/dons/{id}/repos` | non | 403 | oui | 403 | oui |
| `POST /api/v1/donneurs/{id}/ajournements` | non | 403 | oui | 403 | oui |
| `POST /api/v1/centres/{id}/creneaux` | non | 403 | oui | 403 | oui |
| `GET /api/v1/dons/{id}` | non | 403 | oui | 403 | oui |
| `GET /api/v1/dons/{id}/certificat` | non | oui (1) | oui | 403 | oui |
| `POST /api/v1/demandes` | non | 403 | 403 | oui | oui |
| `PATCH /api/v1/demandes/{id}` | non | 403 | 403 | oui | oui |
| `POST /api/v1/demandes/{id}/compensations` | non | 403 | 403 | oui | oui |
| `POST /api/v1/appels` | non | 403 | 403 | oui | oui |
| `PATCH /api/v1/appels/{id}` | non | 403 | 403 | oui | oui |
| `GET /api/v1/stock/**` | non | 403 | oui | oui | oui |
| `GET /api/v1/demandes`, `GET /api/v1/demandes/{id}` | non | authentifié | authentifié | authentifié | authentifié |
| `/actuator/health`, `/api/v1/auth/**`, `OPTIONS /**` | public | — | — | — | — |

(1) Le gateway contrôle le **rôle**, pas la propriété de la ligne : voir § 8.1. Pour `GET /dons/{id}/certificat`, un donneur est autorisé quel que soit l'identifiant du don au niveau du gateway ; c'est Donneurs qui doit vérifier qu'il est bien le donneur de ce don.

Toute route non listée dans le tableau officiel (par exemple `GET /api/v1/demandes`) reste protégée par `authenticated()` : un jeton valide de n'importe quel rôle passe, mais **aucune règle de rôle n'a été inventée**.

---

## 4. Routes publiques et protégées

### 4.1 Publiques (aucun jeton requis)

| Route | Justification |
| --- | --- |
| `OPTIONS /**` | préflight CORS (S2-03) |
| `GET /actuator/health`, `/actuator/health/**` | sonde de santé (S2-07) |
| `/api/v1/auth/**` | connexion (S2-07) |
| `GET /api/v1/centres` | annuaire des centres |
| `GET /api/v1/centres/{id}/creneaux` | créneaux d'un centre |
| `GET /api/v1/appels` | appels aux dons |
| `GET /api/v1/certificats/{numero}` | vérification publique d'un certificat |
| `POST /api/v1/donneurs` | inscription d'un nouveau donneur |

### 4.2 Protégées

Toutes les autres routes exigent un jeton valide :

- **jeton absent, expiré, falsifié ou de signature invalide → 401 JSON** ;
- **jeton valide mais rôle non autorisé → 403 JSON** ;
- **jeton valide et rôle autorisé → requête routée** vers le microservice (avec les en-têtes d'identité réinjectés).

---

## 5. Tests ajoutés et résultats réels

### 5.1 Fichier ajouté

`src/test/java/tn/iit/blood/gateway/GatewayAuthorizationIntegrationTest.java` — **77 tests**, tous en intégration : le gateway démarre réellement sur un port aléatoire avec `gateway.security.permit-all=false` (sécurité active), et un `HttpServer` local simule les microservices en renvoyant le chemin reçu et les en-têtes d'identité effectivement transmis.

### 5.2 Détail des 77 tests

| Test | Nombre | Ce qui est vérifié |
| --- | --- | --- |
| `roleMatrixMatchesTheOfficialTable` (paramétré) | **68** | 17 routes × 4 rôles : 200 avec le corps attendu pour un rôle autorisé, 403 JSON pour un rôle interdit |
| `donorTokenGets403OnCreateDonation` | 1 | critère officiel : 403 JSON, bon message, pas de `WWW-Authenticate` |
| `staffTokenGets200OnCreateDonation` | 1 | critère officiel : 200 et identité `staff`/`STAFF_CENTRE` côté service |
| `anonymousRequestsReceiveJson401OnRoleProtectedRoutes` | 1 | 401 JSON sur `POST`, `GET`, `PATCH` protégés |
| `forbiddenResponseKeepsCorsAndRequestIdHeaders` | 1 | 403 : CORS, `X-Request-Id` entrant restitué, `requestId` dans le corps |
| `tokenWithUnknownRoleIsForbiddenNotUnauthorized` | 1 | rôle `CHEF` inconnu : jeton valide → **403**, pas 401 |
| `expiredOrTamperedTokenIsStillRejectedBeforeTheRoleCheck` | 1 | jeton expiré et jeton illisible → **401** avant tout contrôle de rôle |
| `identityHeadersAreReplacedFromTheVerifiedToken` | 1 | `X-User-Id: admin` + `X-User-Role: ADMIN` falsifiés remplacés par `donor`/`DONOR` |
| `identityHeadersAreRemovedOnPublicRoutes` | 1 | route publique : en-têtes falsifiés supprimés (`none`/`none`) |
| `identityHeadersAreRemovedWhenTheTokenIsRejected` | 1 | 401 : aucun en-tête d'identité renvoyé |

Les 17 routes couvertes par la matrice sont : `POST /dons`, `POST /dons/{id}/entretien`, `POST /dons/{id}/prelevement`, `POST /dons/{id}/repos`, `GET /dons/{id}`, `GET /dons/{id}/certificat`, `POST /donneurs/{id}/ajournements`, `POST /centres/{id}/creneaux`, `GET /donneurs/{id}`, `GET /rendez-vous`, `POST /rendez-vous`, `POST /demandes`, `PATCH /demandes/{id}`, `POST /demandes/{id}/compensations`, `POST /appels`, `PATCH /appels/{id}`, `GET /stock/**`.

### 5.3 Résultat du build complet

```
Tests run: 142, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Les 65 tests de S2-02 à S2-07 restent verts. Répartition : `DemoUserServiceTest` 5, `GatewayApplicationTests` 1, **`GatewayAuthorizationIntegrationTest` 77**, `GatewayCorsIntegrationTest` 4, `GatewayCorsMultipleOriginsTest` 2, `GatewayErrorHandlingIntegrationTest` 3, `GatewayRoutesConfigurationTest` 5, `GatewayRoutingIntegrationTest` 12, `GatewaySecurityIntegrationTest` 14, `GatewayTimeoutIntegrationTest` 3, `GatewayTimeoutsConfigurationTest` 4, `JsonErrorWebExceptionHandlerTest` 6, `JwtTokenServiceTest` 2, `RequestIdFilterIntegrationTest` 3, `RequestIdFilterTest` 1.

### 5.4 Nature exacte des tests

- **Automatisés, réellement exécutés** : les 142 tests ci-dessus.
- **Non réalisables localement** : tests contre les vrais microservices, le front-end et le SSE de production. Leurs dossiers ne contiennent qu'un `.gitkeep`. La vérification manuelle utilise un backend simulé explicitement identifié.

---

## 6. Commande de build et résultat

Exécutée dans `gateway/`, avec JDK 17 (`C:\Program Files\Java\jdk-17`) et Maven 3.9.6 :

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -B -ntp clean verify
```

Résultat réel :

| Élément | Valeur |
| --- | --- |
| Code de sortie de la commande | **0** (`EXITCODE=0`) |
| Ligne de résultat Maven | **`BUILD SUCCESS`** |
| Tests | **142 exécutés, 142 réussis, 0 échec, 0 erreur, 0 ignoré** |
| Durée totale | 01:37 min |
| Artefact | `target/gateway-0.0.1-SNAPSHOT.jar` |

---

## 7. Démonstrations manuelles et résultats

### 7.1 Conditions réelles

- Gateway : `target/gateway-0.0.1-SNAPSHOT.jar` démarré sur le port **8090**, `JWT_SECRET` et mots de passe fournis par variables d'environnement locales (aucun secret dans un fichier suivi).
- Backend : serveur Python simulé sur le port **18083**, qui répond `ok:<méthode> <chemin> userId=<X-User-Id> role=<X-User-Role>`. Ce n'est **pas** un microservice réel.
- Script de vérification : 8 sections, **86 contrôles assertés** (83 affichés `OK`, plus le contrat 403 et les deux contrôles d'en-têtes comparés explicitement à la valeur attendue).
- Résultat final : **`RESULTAT: 0 echec(s)`**.

### 7.2 Contenu et résultats par section

| Section | Contrôles | Résultat |
| --- | --- | --- |
| 1. Connexion des quatre comptes (S2-07 inchangé) | 4 connexions | 200 ; claims `sub,role,exp,iat` ; `ttl=3600s` ; rôles `DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN` |
| 2. Critère officiel S2-08 | 2 | `POST /dons` donor → **403**, staff → **200** |
| 3. Matrice officielle (16 routes × 4 rôles) | 64 | 200 pour chaque rôle autorisé, 403 pour chaque rôle interdit |
| 4. Contrat JSON du 403 | 1 | JSON seul (pas de HTML), `WWW-Authenticate` absent, CORS `http://localhost:3000`, `requestId` entrant restitué |
| 5. Accès anonyme aux routes protégées | 6 | 401 JSON |
| 6. Routes publiques sans jeton | 6 | `centres`, `creneaux`, `appels`, `certificats`, `POST donneurs`, `health` → 200 |
| 7. En-têtes d'identité | 2 | falsifiés remplacés (`donor`/`DONOR`) ou supprimés (`none`/`none`) |
| 8. Non-régression S2-03 à S2-07 | 5 | preflight 200, CORS sur 401, jeton expiré 401, jeton falsifié 401, route inconnue 404 |

Extraits réels :

```
[POST /api/v1/dons jeton donor] status=403 ct=application/json
  body={"timestamp":"...","status":403,"error":"Forbidden",
        "message":"Acces refuse : le role de l'utilisateur ne permet pas cette operation.",
        "path":"/api/v1/dons","requestId":"manual-s208-403"}
  HTML=False / WWW-Authenticate present=False / requestId echo=True

[POST /api/v1/dons jeton staff] status=200
  body=ok:POST /api/v1/dons userId=staff role=STAFF_CENTRE

[GET /rendez-vous donor + en-tetes falsifies] body=ok:GET /api/v1/rendez-vous userId=donor role=DONOR
[GET /centres public + en-tetes falsifies]  body=ok:GET /api/v1/centres userId=none role=none
```

### 7.3 Non-régression vérifiée manuellement

CORS (preflight et en-têtes sur 401), `X-Request-Id` (généré ou restitué), routage, timeouts, 404 JSON, 503 JSON et `/actuator/health` n'ont pas régressé : les contrôles correspondants sont passés.

### 7.4 Journal du gateway après correction du 401 paresseux

| Événement | Nombre | Analyse |
| --- | --- | --- |
| `Reponse d'erreur 403` | **31** | tous les refus de rôle de la matrice et du contrat 403 |
| `Reponse d'erreur 401` | **9** | uniquement les requêtes réellement anonymes ou à jeton invalide/expiré |

Aucune ligne 401 parasite n'accompagne plus un 403.

---

## 8. Limitations, notamment celles liées au gap 7

### 8.1 Propriété des données : non applicable au gateway sans gap 7

Le guide demande qu'un donneur ne lise que **ses** données et que le certificat d'un don ne soit accessible qu'à **son** donneur. Le gateway ne peut pas le garantir :

- le JWT ne contient **ni `donneurId` ni `hopitalId`** (gap 7 différé) ;
- `sub` est un **nom de connexion**, pas un identifiant de donneur ; le traiter comme tel serait une invention ;
- même avec un identifiant métier, le gateway ne connaît pas le propriétaire d'un don : le guide précise que **seul Donneurs** le sait.

Ce qui est réellement implémenté : la règle de **rôle**, plus la transmission fiable de `X-User-Id` (`sub`) et `X-User-Role` (rôle vérifié). Le service Donneurs, **inexistant localement**, devra appliquer la propriété :

- `GET /dons/{id}/certificat` : vérifier que `X-User-Id` correspond au donneur du don lorsque `X-User-Role = DONOR` ;
- `/donneurs/{id}/**` : vérifier que l'identifiant demandé appartient bien au donneur authentifié ;
- documenter la correspondance entre `sub` et l'identifiant donneur (gap 7, ou table de correspondance côté Donneurs).

Tant que ce service n'existe pas, **la règle « le donneur de ce don » n'est pas satisfaite de bout en bout** ; elle est seulement rendue possible par les en-têtes transmis.

### 8.2 Routes absentes du tableau officiel

`GET /api/v1/demandes` et `GET /api/v1/demandes/{id}` ne figurent pas dans le tableau S2-08. Elles restent donc `authenticated()` : tout rôle disposant d'un jeton valide peut les appeler. Aucune restriction `HOPITAL`/`ADMIN` n'a été inventée. Si la lecture des demandes doit être réservée aux hôpitaux et aux administrateurs, c'est une décision à prendre (voir § 10).

### 8.3 Écritures sous `/donneurs/{id}/**`

Le tableau autorise `DONOR` sur tout `/donneurs/{id}/**`. Le gateway applique cette règle littéralement. Si des sous-chemins d'écriture autres que `ajournements` (déjà réservé au staff) venaient à exister, un donneur pourrait y accéder au niveau du gateway : la propriété et la nature de l'opération devront être contrôlées par Donneurs. Aucune route de ce type n'existe aujourd'hui dans la configuration.

### 8.4 `gateway.security.permit-all`

Cette propriété, héritée de S2-07, désactive **toutes** les règles d'accès. Elle est nécessaire pour ne pas modifier les tests S2-02 à S2-06, qui ne portent pas sur la sécurité, et n'est **pas définie** dans `application.yml`. Elle reste néanmoins présente dans le code de production : une variable d'environnement mal placée pourrait la activer. Les tests S2-08 forcent explicitement `permit-all=false`. Un durcissement (profil de test dédié, ou suppression une fois les anciens tests adaptés) reste à décider.

### 8.5 Portée réelle de la vérification

Les microservices, le front-end et le SSE de production n'existent pas localement. Les résultats manuels prouvent le comportement du **gateway** face à un backend simulé, pas l'intégration complète.

### 8.6 403 sans `WWW-Authenticate`

Choix assumé : un 403 signale un problème de rôle, pas un défi d'authentification. Les clients qui s'appuieraient sur `WWW-Authenticate` pour déclencher une reconnexion ne le feront pas sur un 403 — comportement attendu.

---

## 9. Fichiers créés et modifiés

### 9.1 Créés

| Fichier | Rôle |
| --- | --- |
| `gateway/src/main/java/tn/iit/blood/gateway/security/UserRoles.java` | constantes des quatre rôles officiels |
| `gateway/src/main/java/tn/iit/blood/gateway/security/JsonAccessDeniedHandler.java` | 403 JSON dédié |
| `gateway/src/main/java/tn/iit/blood/gateway/security/RequestIdSupport.java` | résolution partagée du `requestId` (401/403) |
| `gateway/src/main/java/tn/iit/blood/gateway/filter/UserIdentityHeaderFilter.java` | suppression puis réinjection de `X-User-Id`/`X-User-Role` |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayAuthorizationIntegrationTest.java` | 77 tests de la matrice, du 401/403 et des en-têtes |
| `gateway/RAPPORT_S2-08.md` | ce rapport |

### 9.2 Modifiés

| Fichier | Modification |
| --- | --- |
| `gateway/src/main/java/tn/iit/blood/gateway/security/SecurityConfiguration.java` | matrice de rôles S2-08, `accessDeniedHandler`, Javadoc |
| `gateway/src/main/java/tn/iit/blood/gateway/security/JsonAuthenticationEntryPoint.java` | `RequestIdSupport` + `Mono.defer` (401 paresseux) |
| `gateway/src/main/java/tn/iit/blood/gateway/security/DemoUserService.java` | rôles via `UserRoles` |

### 9.3 Inchangés

Aucun test ni rapport S2-02 à S2-07 modifié. Aucun fichier de configuration de routes, CORS ou timeout modifié. Aucun secret ajouté dans un fichier suivi ; `.env` reste ignoré.

### 9.4 État Git

Aucun commit, aucun push. `gateway/` reste non suivi ; seuls `.env.example` et `.gitignore` apparaissent modifiés (travaux S2-07), et `gateway.zip` reste non suivi.

---

## 10. Points restant à valider

1. **Gap 7 et propriété des données.** Confirmer que l'application de la propriété est bien déléguée au service Donneurs, et décider du mécanisme d'identité métier (`donneurId`/`hopitalId` dans le JWT, ou correspondance `sub` → donneur côté Donneurs). En l'état, la règle « le donneur de ce don » n'est **pas** garantie de bout en bout.
2. **Lecture des demandes.** `GET /api/v1/demandes` et `GET /api/v1/demandes/{id}` ne sont pas dans le tableau officiel. Faut-il les réserver à `HOPITAL`/`ADMIN`, ou les laisser accessibles à tout porteur de jeton ?
3. **`permit-all`.** Faut-il conserver cette échappatoire de test dans le code de production, ou la déplacer/la supprimer ?
4. **403 sans `WWW-Authenticate`.** Valider ce choix de contrat pour le front-end.
5. **`X-User-Id` = `sub`.** Valider que Donneurs utilisera bien le nom de connexion (ou une correspondance documentée) et non `sub` comme identifiant métier.
6. **Tests contre les vrais services.** Ils restent à faire dès que les microservices et le front-end seront disponibles (S2-10 et intégration Compose).

---

## Conclusion et statut final

S2-08 est implémentée conformément au tableau officiel :

- matrice de rôles complète sur les routes listées ;
- **401 JSON** sans jeton / jeton invalide, **403 JSON** pour un rôle non autorisé, contrat d'erreur identique à S2-02 à S2-07 ;
- suppression puis réinjection sûre des en-têtes d'identité ;
- critère officiel vérifié : **donor 403 sur `POST /dons`, staff 200** ;
- **build `mvn -B -ntp clean verify` : code de sortie 0, `BUILD SUCCESS`, 142/142 tests** ;
- **vérification manuelle : 86 contrôles assertés, 0 échec**.

Les règles de **propriété** (« ses propres données », « le donneur de ce don ») ne sont **pas** présentées comme satisfaites : elles dépendent du gap 7 et du service Donneurs, et sont documentées comme telles.

**Statut : en attente de validation explicite. Aucune étape postérieure à S2-08 n'a été commencée ; aucun commit ni push n'a été effectué.**
