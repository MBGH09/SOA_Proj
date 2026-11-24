# Rapport technique — S2-07 : connexion et jetons JWT (bonus)

> **Note de vérification.** Ce rapport décrit **l'état final** du composant `gateway` après réalisation de S2-07. Les tests automatisés et la démonstration manuelle ont été **réellement exécutés le 18/11/2026** ; les sorties citées proviennent de ces exécutions. Les appels « backend » de la vérification manuelle sont servis par un **serveur HTTP simulé** explicitement identifié (hors dépôt). Aucun test contre les vrais microservices n'est revendiqué : leurs dossiers ne contiennent encore qu'un `.gitkeep`.

---

## 1. Introduction et objectif de l'étape

Après S2-06 (erreurs JSON normalisées), l'étape suivante identifiée dans les documents officiels est **S2-07 — Login endpoint with JWT (bonus)**.

**Objectif :** fournir au front-end un point d'entrée de connexion pour les quatre rôles du projet, émettre un JWT signé en HS256 valable une heure, et faire en sorte que le gateway vérifie lui-même ce jeton : une requête sans jeton sur une route protégée renvoie un **401 JSON**, une requête avec un jeton valide est transmise au microservice.

**Périmètre respecté :** uniquement l'authentification (401) et l'émission des jetons. Les **règles de rôle** (403) relèvent de S2-08 et ne sont pas implémentées ici ; la limitation de débit relève de S2-09. Les étapes S2-02 à S2-06 sont préservées.

---

## 2. Exigences officielles et critères d'acceptation

Source : `docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`, branche `origin/develop` (section « Student 2 »), recoupée sur `origin/config` :

> **S2-07 Login endpoint with JWT (bonus).** Why: the study defines four roles and rule R20 (interview data only for staff).
> 1. Add variables for four demo users (donor, centre staff, hospital, admin) with their passwords, and `JWT_SECRET` (at least 32 characters, set in `.env`).
> 2. Add `POST /api/v1/auth/login` in the Gateway. It returns a JWT signed with HS256, valid 1 hour, with the claims `sub`, `role` (`DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN`) and, if gap 7 is accepted, `donneurId` or `hopitalId`.
> 3. Configure the resource server to check the token with the same secret.
> 4. Permit `/actuator/health` and `/api/v1/auth/**`. Everything else needs a token, except the public routes of S2-08.
>
> **Done when:** a call without a token gives a JSON 401 and a call with a token goes through.

| Exigence officielle | Mise en œuvre | Vérification |
|---|---|---|
| Quatre utilisateurs de démonstration par variables d'environnement | `DemoUserService` + `gateway.security.demo-users.*` (`DEMO_*_USERNAME`, `DEMO_*_PASSWORD`) | 5 tests unitaires + 4 connexions manuelles |
| `JWT_SECRET` d'au moins 32 caractères, dans `.env` | `SecurityConfiguration#jwtSecretKey` refuse un secret absent ou trop court ; `.env` ignoré par Git | Test de démarrage réel (échec sans secret) + `.gitignore` |
| `POST /api/v1/auth/login` | `LoginController` | 14 tests d'intégration + appels manuels |
| JWT HS256 valable 1 heure | `JwtTokenService` (`MacAlgorithm.HS256`, `exp = iat + 3600 s`) | Tests unitaires et d'intégration (décodage de l'en-tête et du payload) |
| Claims `sub` et `role` (`DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN`) | Claims exactement `sub`, `role`, `iat`, `exp` | Tests unitaires + décodage manuel des quatre jetons |
| `donneurId` / `hopitalId` seulement si gap 7 accepté | **Gap 7 différé** : aucun de ces claims n'est émis | Tests explicites d'absence |
| Resource server vérifiant le jeton avec le même secret | `NimbusReactiveJwtDecoder` HS256 + `oauth2ResourceServer().jwt(...)` | Tests d'intégration (jeton valide, expiré, falsifié) |
| `permitAll` pour `/actuator/health` et `/api/v1/auth/**` | Chaîne de sécurité | Tests d'intégration + appels manuels |
| Routes publiques de S2-08 accessibles sans jeton | Liste blanche des routes de lecture + inscription | Tests d'intégration + appels manuels |
| **Sans jeton → 401 JSON** | `JsonAuthenticationEntryPoint` (contrat JSON de S2-06) | Tests + appel manuel |
| **Avec jeton → la requête passe** | Relais vers le microservice | Tests + appel manuel vers le backend simulé |

**Critère d'acceptation :** un appel sans jeton renvoie un **401 JSON** et un appel avec jeton **traverse** le gateway. **Satisfait** (sections 6.1 et 6.6).

**Décision explicite sur le gap 7.** Le guide conditionne les claims `donneurId`/`hopitalId` à l'acceptation du gap 7. Après validation du porteur du projet, le **gap 7 est différé** : le jeton ne contient que `sub`, `role`, `iat`, `exp`. Conséquence assumée : S2-08 ne pourra pas identifier « le donneur lui-même » à partir du jeton ; ce point devra être rouvert lors de S2-08 ou d'une étape ultérieure.

---

## 3. Environnement technique

| Élément | Valeur vérifiée | Preuve |
|---|---|---|
| JDK | **17.0.12** (Oracle, `C:\Program Files\Java\jdk-17`) | Build Maven |
| Maven | **3.9.6** | Build Maven |
| Spring Boot | **3.3.13** | `gateway/pom.xml` |
| Spring Cloud | **2023.0.6** (Spring Cloud Gateway **4.1.9**) | `gateway/pom.xml` |
| Spring Security | starter `spring-boot-starter-oauth2-resource-server` (Security 6.3.x, Nimbus JOSE) | `gateway/pom.xml`, build |
| Serveur | Spring Cloud Gateway réactif (WebFlux / Netty) | logs |
| Port local du gateway | **8090** (8080 occupé par Oracle XE) | `application.yml`, logs |
| Port du backend simulé | **18083** (uniquement pour la vérification manuelle) | script hors dépôt |
| OS | Windows 11 | environnement |

**Dépendance ajoutée :** `spring-boot-starter-oauth2-resource-server` (fournit Spring Security WebFlux, le décodeur/encodeur JWT Nimbus et l'intégration `oauth2ResourceServer`). Aucune autre dépendance n'a été ajoutée.

---

## 4. Modifications effectuées et fichiers concernés

| Fichier | État | Contenu |
|---|---|---|
| `gateway/src/main/java/tn/iit/blood/gateway/security/SecurityConfiguration.java` | **Créé** | Chaîne Spring Security : routes publiques, authentification par jeton, entrée 401 JSON, encodeur/décodeur HS256, CORS |
| `gateway/src/main/java/tn/iit/blood/gateway/security/SecurityProperties.java` | **Créé** | Propriétés `gateway.security.*` (`jwt.secret`, `demo-users`, `permit-all`) |
| `gateway/src/main/java/tn/iit/blood/gateway/security/DemoUser.java` | **Créé** | Enregistrement `key`, `username`, `password`, `role` |
| `gateway/src/main/java/tn/iit/blood/gateway/security/DemoUserService.java` | **Créé** | Charge les quatre comptes, fixe les rôles, refuse un compte incomplet ou un nom en double |
| `gateway/src/main/java/tn/iit/blood/gateway/security/JwtTokenService.java` | **Créé** | Émission HS256, TTL 1 h, claims `sub`, `role`, `iat`, `exp` |
| `gateway/src/main/java/tn/iit/blood/gateway/security/JsonAuthenticationEntryPoint.java` | **Créé** | 401 JSON pour absence de jeton **et** jeton invalide/expiré, avec `WWW-Authenticate: Bearer` |
| `gateway/src/main/java/tn/iit/blood/gateway/security/LoginController.java` | **Créé** | `POST /api/v1/auth/login` : 200 `{"token":"..."}`, 400 champs manquants, 401 identifiants invalides |
| `gateway/src/main/java/tn/iit/blood/gateway/security/LoginRequest.java` | **Créé** | Corps `{username, password}` |
| `gateway/src/main/java/tn/iit/blood/gateway/security/LoginResponse.java` | **Créé** | Réponse `{token}` |
| `gateway/src/main/java/tn/iit/blood/gateway/error/JsonErrorPayload.java` | **Créé** | Fabrique du corps JSON commun, partagée avec S2-06 |
| `gateway/src/main/java/tn/iit/blood/gateway/error/JsonErrorWebExceptionHandler.java` | **Refactoré** | Utilise `JsonErrorPayload` ; comportement et contrat S2-06 inchangés (les 9 tests S2-06 passent) |
| `gateway/pom.xml` | **Modifié** | Ajout de `spring-boot-starter-oauth2-resource-server` |
| `gateway/src/main/resources/application.yml` | **Modifié** | Ajout du bloc `gateway.security.*` avec placeholders `JWT_SECRET` et `DEMO_*` ; aucune route modifiée |
| `gateway/src/test/resources/application.properties` | **Créé** | Secret et comptes de test fictifs ; `gateway.security.permit-all=true` pour les tests S2-02 à S2-06 |
| `gateway/src/test/java/.../DemoUserServiceTest.java` | **Créé** | 5 tests unitaires |
| `gateway/src/test/java/.../JwtTokenServiceTest.java` | **Créé** | 2 tests unitaires |
| `gateway/src/test/java/.../GatewaySecurityIntegrationTest.java` | **Créé** | 14 tests d'intégration |
| `.env.example` | **Modifié** | `JWT_SECRET` et huit variables `DEMO_*` avec **placeholders explicites** |
| `.gitignore` (racine) | **Modifié** | Ajout de `.env` |
| Étapes S2-02 à S2-06 | **Préservées** | Routes, CORS, timeouts, filtre `X-Request-Id`, contrat JSON : aucun comportement modifié |
| `gateway/RAPPORT_S2-02.md` à `RAPPORT_S2-06.md` | **Inchangés** | Rapports validés non réécrits |

Aucun commit ni push n'a été effectué.

---

## 5. Explications des choix techniques

### 5.1 Où vit l'authentification

Le gateway est un serveur **réactif** (WebFlux). La configuration utilise donc `@EnableWebFluxSecurity` et une `SecurityWebFilterChain`, pas la variante servlet. Le jeton est vérifié **localement** par le gateway avec le secret partagé : aucun appel réseau supplémentaire n'est nécessaire pour authentifier une requête.

### 5.2 Secret et signature

- Algorithme **HS256** (secret partagé), conformément à l'énoncé.
- `JWT_SECRET` provient de l'environnement et **n'a aucune valeur par défaut** exploitable. Au démarrage, `jwtSecretKey` échoue si le secret est vide ou fait moins de **32 caractères**.
- Un seul bean `SecretKey` alimente l'**encodeur** (`NimbusJwtEncoder`) et le **décodeur** (`NimbusReactiveJwtDecoder`), ce qui garantit que signature et vérification utilisent exactement le même secret.
- Le décodeur applique le validateur standard : signature, `exp`, `nbf`. Aucun émetteur (`iss`) n'est imposé.

### 5.3 Contenu du jeton

| Claim | Valeur | Justification |
|---|---|---|
| `sub` | nom d'utilisateur de démonstration | identité authentifiée |
| `role` | `DONOR`, `STAFF_CENTRE`, `HOPITAL` ou `ADMIN` | rôle officiel, converti en autorité `ROLE_<role>` pour préparer S2-08 |
| `iat` | date d'émission | norme JWT |
| `exp` | `iat` + **3600 s** | validité d'une heure exigée |

**Pas de `iss`.** Un premier essai ajoutait un émetteur `blood-gateway` ; le type retourné par `Jwt#getIssuer()` est une `URL`, ce qui provoquait une conversion indésirable et un test en échec. L'émetteur n'étant pas demandé par l'énoncé, il a été **retiré** plutôt que contourné.

**Pas de `donneurId` ni `hopitalId`.** Gap 7 différé (section 2).

### 5.4 Utilisateurs de démonstration

- Quatre clés fixes : `donor`, `staff`, `hopital`, `admin`.
- Rôles **fixés par le code** (`DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN`) : une variable d'environnement ne peut pas changer un rôle.
- Noms d'utilisateur par défaut (`donor`, `staff`, `hopital`, `admin`), surchargeables par `DEMO_*_USERNAME`.
- Mots de passe **sans valeur par défaut**, fournis par `DEMO_*_PASSWORD`.
- Le service refuse de démarrer si un compte est incomplet ou si deux comptes partagent le même nom : une configuration de démonstration doit être explicite, pas silencieusement dégradée.
- La comparaison des mots de passe utilise `MessageDigest.isEqual` (comparaison en temps constant) sur les octets UTF-8. Ce n'est **pas** un stockage haché : il s'agit de comptes de démonstration locaux, pas d'un annuaire réel (limite assumée en section 9).

### 5.5 Réponse de connexion

`POST /api/v1/auth/login` accepte `{"username":"...","password":"..."}` et renvoie :

```json
{"token":"eyJhbGciOiJIUzI1NiJ9...."}
```

Ce contrat (`token` dans un objet JSON) correspond à l'exemple d'appel du guide (S2-11). Les erreurs réutilisent **le contrat JSON de S2-06** :

| Cas | Statut | `message` |
|---|---|---|
| Champs absents ou vides | **400** | `Nom d'utilisateur et mot de passe obligatoires.` |
| Mot de passe faux | **401** | `Identifiants invalides.` |
| Utilisateur inconnu | **401** | `Identifiants invalides.` (même message : pas d'énumération de comptes) |

### 5.6 Deux origines possibles du 401

Spring Security intercepte une requête **avant** le routage du gateway. Sans composant dédié, la réponse serait vide ou HTML. Deux chemins distincts produisent pourtant un 401 :

1. **Aucun jeton** : `authorizeExchange` refuse l'accès → `exceptionHandling().authenticationEntryPoint(...)`.
2. **Jeton présent mais invalide** (expiré, signature incorrecte, malformé) : c'est le filtre `BearerTokenAuthenticationFilter` qui rejette la requête. Ce filtre possède son propre point d'entrée ; il faut donc aussi le configurer via `oauth2ResourceServer().authenticationEntryPoint(...)`.

Les deux chemins utilisent le **même** `JsonAuthenticationEntryPoint`, ce qui garantit un corps identique. Ce second branchement a été ajouté après un premier essai où un jeton expiré renvoyait un 401 **sans corps JSON**.

Le point d'entrée :

- écrit `timestamp`, `status`, `error`, `message`, `path` et `requestId` ;
- renvoie `WWW-Authenticate: Bearer` ;
- fixe `Content-Type: application/json` et `Content-Length` ;
- reprend `X-Request-Id` de l'en-tête entrant s'il existe, sinon en génère un et le renvoie, car le filtre de routage S2-05 ne s'exécute pas sur les chemins non routés ;
- journalise l'événement en `WARN` avec la méthode, le chemin et l'identifiant de requête.

### 5.7 Routes publiques et anticipation de S2-08

L'énoncé demande de laisser passer `/actuator/health` et `/api/v1/auth/**`, puis « tout le reste sauf les routes publiques de S2-08 ». La liste blanche de S2-08 est donc appliquée **dès maintenant** :

| Méthode | Chemin | Justification (S2-08) |
|---|---|---|
| `GET` | `/api/v1/centres` | lecture publique |
| `GET` | `/api/v1/centres/*/creneaux` | lecture publique |
| `GET` | `/api/v1/appels` | lecture publique |
| `GET` | `/api/v1/certificats/*` | vérification publique d'un certificat (sans données personnelles) |
| `POST` | `/api/v1/donneurs` | inscription d'un nouveau donneur |
| `OPTIONS` | tout chemin | préflight CORS |

`/actuator/health` (et `/actuator/health/**`), `/api/v1/auth/**` sont également publics. **Toute autre requête exige un jeton valide.**

**Important :** cette liste ne contient **aucune règle de rôle** (pas de 403). Un jeton `DONOR` peut donc appeler n'importe quelle route protégée à ce stade. C'est exactement le périmètre de S2-08, qui ajoutera `hasRole(...)`.

### 5.8 CORS avant la sécurité

La politique CORS de S2-03 est déclarée dans `application.yml` (`spring.cloud.gateway.globalcors`). Un filtre CORS est ajouté **avant** la chaîne de sécurité (`cors(Customizer.withDefaults())` + `CorsConfigurationSource` réactive équivalente) pour que les réponses **401** portent aussi `Access-Control-Allow-Origin`. Sans cela, le navigateur masquerait le corps JSON de l'erreur au front-end. Les valeurs restent pilotées par `CORS_ALLOWED_ORIGINS`.

### 5.9 Compatibilité avec les tests S2-02 à S2-06

Ces tests ne portent pas sur la sécurité : ils appellent des routes protégées sans jeton. Plutôt que de les réécrire (ce qui aurait modifié des étapes validées), les ressources de test définissent `gateway.security.permit-all=true`. Cette propriété :

- vaut **`false` par défaut** dans `SecurityProperties` ;
- **n'est jamais définie** dans `application.yml` de production ;
- est explicitement remise à `false` par `GatewaySecurityIntegrationTest`.

Les tests de sécurité utilisent en plus un **secret et des mots de passe de test fictifs**, propres à `src/test/resources/application.properties`.

---

## 6. Tests exécutés et résultats exacts

### 6.1 Build et tests automatisés

Commande exécutée depuis `gateway/` :

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp clean verify
```

Résultat réel (une ligne par classe de test, sortie Surefire du build final) :

```
[INFO] Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- DemoUserServiceTest
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayApplicationTests
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayCorsIntegrationTest
[INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayCorsMultipleOriginsTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayErrorHandlingIntegrationTest
[INFO] Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayRoutesConfigurationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- GatewayRoutingIntegrationTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- GatewaySecurityIntegrationTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayTimeoutIntegrationTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- GatewayTimeoutsConfigurationTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- JsonErrorWebExceptionHandlerTest
[INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- JwtTokenServiceTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterIntegrationTest
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
[INFO] Tests run: 65, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **EXITCODE = 0**, `BUILD SUCCESS`, JAR produit : `gateway/target/gateway-0.0.1-SNAPSHOT.jar`.
- **65 tests, 65 réussis**, 0 échec, 0 erreur, 0 ignoré.
- Détail : **44 tests** existants (S2-01 à S2-06, inchangés) + **21 nouveaux** tests S2-07.

### 6.2 Tests unitaires (7 nouveaux, sans serveur)

**`DemoUserServiceTest` — 5 tests**

1. les quatre utilisateurs sont chargés dans l'ordre officiel avec les rôles `DONOR`, `STAFF_CENTRE`, `HOPITAL`, `ADMIN` ;
2. un nom d'utilisateur inconnu n'est pas trouvé ;
3. deux comptes portant le même nom empêchent le démarrage ;
4. un mot de passe vide empêche le démarrage (message nommant `DEMO_ADMIN_PASSWORD`) ;
5. un utilisateur manquant empêche le démarrage.

**`JwtTokenServiceTest` — 2 tests**

1. le jeton est signé **HS256**, contient `sub`, `role`, `iat`, `exp`, et la durée `exp - iat` vaut exactement **1 heure** ;
2. aucun claim `donneurId` ni `hopitalId` n'est présent (gap 7 différé).

### 6.3 Tests d'intégration (`GatewaySecurityIntegrationTest`, 14 tests)

Le gateway démarre réellement sur un port aléatoire, sécurité **active** (`permit-all=false`). Un `HttpServer` du JDK joue le rôle des microservices et répond `proxied:<chemin>`. **Ce ne sont pas les vrais microservices.**

1. la connexion `donor` renvoie un jeton HS256 avec `sub`, `role`, `iat`, `exp`, TTL de 3600 s et **sans** `donneurId`/`hopitalId` ;
2. les quatre comptes se connectent et reçoivent leur rôle officiel ;
3. un jeton `DONOR` valide atteint la route protégée `/api/v1/rendez-vous` (200, réponse du backend) ;
4. sans jeton, `/api/v1/rendez-vous` renvoie **401 JSON** avec `WWW-Authenticate: Bearer` et un `requestId` cohérent avec l'en-tête ;
5. un mauvais mot de passe renvoie **401 JSON** « Identifiants invalides. » ;
6. un utilisateur inconnu renvoie **401 JSON** avec le même message ;
7. des champs manquants renvoient **400 JSON** ;
8. un jeton **expiré** (signé avec le bon secret) renvoie **401 JSON** ;
9. un jeton **falsifié** renvoie **401 JSON** ;
10. les routes publiques (`/centres`, `/centres/1/creneaux`, `/appels`, `/certificats/...`, `POST /donneurs`) atteignent le backend **sans jeton**, et `/actuator/health` répond `UP` ;
11. `POST /api/v1/demandes` reste protégé (401 sans jeton) ;
12. le préflight `OPTIONS` est accepté sans jeton ;
13. une réponse **401 porte les en-têtes CORS** ;
14. une route inconnue renvoie **401 sans jeton**, puis **404 JSON** avec un jeton valide.

Chaque corps d'erreur est contrôlé : présence de `timestamp`, `status`, `error`, `message`, `path`, et **absence de HTML**.

### 6.4 Démonstration manuelle réelle du 18/11/2026 (16:30–16:32)

**Dispositif.** Gateway réellement démarré depuis le JAR (`java -jar target/gateway-0.0.1-SNAPSHOT.jar`) sur **8090**, avec :

- `JWT_SECRET` local de 48 caractères (valeur non consignée ici, non commitée) ;
- quatre mots de passe locaux de démonstration (non commités) ;
- `DONNEURS_URL` et `DEMANDES_URL` pointant vers un **serveur HTTP simulé** Python sur **18083**, qui répond `ok:<méthode> <chemin>` et journalise `X-Request-Id`.

Requêtes envoyées avec `System.Net.Http.HttpClient` (PowerShell), en-têtes et corps réellement observés :

| # | Requête | Statut | Constat réel |
|---|---|---|---|
| 1 | `POST /api/v1/auth/login` `donor` | **200** | `{"token":"..."}` ; `alg=HS256`, `sub=donor`, `role=DONOR`, claims `sub,role,exp,iat`, TTL `3600 s`, aucun `donneurId`/`hopitalId` |
| 2 | idem `staff` | **200** | `role=STAFF_CENTRE`, TTL `3600 s` |
| 3 | idem `hopital` | **200** | `role=HOPITAL`, TTL `3600 s` |
| 4 | idem `admin` | **200** | `role=ADMIN`, TTL `3600 s` |
| 5 | login `donor` + mauvais mot de passe | **401** | JSON `Identifiants invalides.` + `requestId` |
| 6 | login utilisateur `inconnu` | **401** | même JSON, même message |
| 7 | login sans mot de passe | **400** | JSON `Nom d'utilisateur et mot de passe obligatoires.` |
| 8 | `GET /api/v1/rendez-vous` sans jeton, `Origin: http://localhost:3000`, `X-Request-Id: manual-s207-401` | **401** | `application/json`, `WWW-Authenticate: Bearer`, `Access-Control-Allow-Origin: http://localhost:3000`, `requestId=manual-s207-401` dans l'en-tête **et** le corps, aucun HTML |
| 9 | `GET /api/v1/rendez-vous` avec jeton `donor` | **200** | `ok:GET /api/v1/rendez-vous` (transmis au backend simulé) |
| 10 | `POST /api/v1/demandes` avec jeton `hopital` | **200** | `ok:POST /api/v1/demandes` |
| 11 | `GET /api/v1/centres` sans jeton | **200** | transmis |
| 12 | `GET /api/v1/centres/1/creneaux` sans jeton | **200** | transmis |
| 13 | `GET /api/v1/appels` sans jeton | **200** | transmis |
| 14 | `GET /api/v1/certificats/CNTS-2026-0001` sans jeton | **200** | transmis |
| 15 | `POST /api/v1/donneurs` sans jeton | **200** | transmis (inscription publique) |
| 16 | `GET /actuator/health` sans jeton | **200** | `{"status":"UP"}` |
| 17 | préflight `OPTIONS /api/v1/rendez-vous` (`Origin`, `Access-Control-Request-Method: GET`, `Access-Control-Request-Headers: authorization`) | **200** | `Access-Control-Allow-Origin: http://localhost:3000` |
| 18 | jeton **expiré signé avec le vrai secret** | **401** | JSON `Un jeton d'acces valide est requis.`, `WWW-Authenticate: Bearer`, aucun HTML |
| 19 | jeton valide dont la **signature est altérée** | **401** | même JSON, aucun HTML |
| 20 | `GET /api/v1/route-inconnue` sans jeton | **401** | JSON (la sécurité précède le routage) |
| 21 | `GET /api/v1/route-inconnue` avec jeton `donor` | **404** | JSON `Aucune ressource ne correspond au chemin demande.` (`requestId` absent : la requête n'a pas été routée, donc le filtre S2-05 n'a pas tourné — comportement S2-06 inchangé) |
| 22 | backend simulé **arrêté**, `GET /api/v1/donneurs` avec jeton `donor` | **503** | `application/json` : `{"timestamp":"...","status":503,"error":"Service Unavailable","message":"Le service demande est momentanement indisponible.","path":"/api/v1/donneurs","requestId":"1a3954ac-..."}`, aucun HTML |

**Propagation de `X-Request-Id`.** Le journal du backend simulé montre le même identifiant que celui renvoyé au client, par exemple :

```
16:32:02 GET /api/v1/rendez-vous X-Request-Id=2d49b905-2b93-4643-929f-bc39bdb80021
16:32:02 POST /api/v1/demandes X-Request-Id=ad43cea4-58cc-41b0-8682-d0c54a76ad5d
16:32:02 GET /api/v1/centres X-Request-Id=eaa614b2-6322-4de1-bc27-9301dc7b57ea
```

**Journal du gateway.** Les traces confirment les deux chemins de 401 et le 503 :

```
WARN  t.i.b.g.s.JsonAuthenticationEntryPoint - Reponse d'erreur 401 pour GET /api/v1/rendez-vous (requestId=manual-s207-401)
INFO  t.i.b.g.security.LoginController - Connexion reussie pour donor (role=DONOR, requestId=...)
WARN  t.i.b.g.e.JsonErrorWebExceptionHandler - Reponse d'erreur 503 pour GET /api/v1/donneurs (message=Le service demande est momentanement indisponible.)
```

### 6.5 Contrôle négatif réel : démarrage sans configuration

Deux démarrages réels du JAR **sans** les variables attendues ont été exécutés le 18/11/2026 :

| Configuration | Résultat réel |
|---|---|
| mots de passe `DEMO_*_PASSWORD` vides, `JWT_SECRET` absent | **échec du démarrage** : `IllegalStateException: Utilisateur de demonstration incomplet : donor. Definir DEMO_DONOR_USERNAME et DEMO_DONOR_PASSWORD.` |
| quatre mots de passe renseignés, `JWT_SECRET` absent | **échec du démarrage** : `IllegalStateException: JWT_SECRET est obligatoire et doit contenir au moins 32 caracteres.` |

Le port 8090 n'est pas resté ouvert après ces échecs. Cela confirme qu'aucune valeur secrète par défaut ne permet un démarrage implicite.

### 6.6 Non-régression professionnelle

- Les **44 tests antérieurs** (S2-01 à S2-06) passent sans modification de leurs assertions.
- Le contrat JSON de S2-06 est **réutilisé** (pas dupliqué) et ses 9 tests restent verts.
- Le filtre `X-Request-Id` fonctionne toujours pour les routes routées (vérifié manuellement et par tests).
- La vérification manuelle a confirmé : route normale avec jeton **200**, CORS **200**, santé **200**, route inconnue **404 JSON**, service arrêté **503 JSON**, jamais de HTML.

### 6.7 Nature exacte des tests

| Type | Ce qui est réellement testé | Ce qui n'est pas testé |
|---|---|---|
| Tests unitaires (7) | Règles de chargement des comptes, contenu et signature du JWT, absence des claims du gap 7 | Aucun serveur, aucun réseau |
| Tests d'intégration (14) | Gateway réel sur port aléatoire, sécurité réelle, backend **simulé** (`com.sun.net.httpserver`) | Vrais microservices, vraie base, vrai front-end |
| Vérification manuelle (22 cas) | JAR réel, réseau local réel, backend **simulé** Python sur 18083 | Vrais microservices (dossiers vides), navigateur, SSE de production |

**Aucun test contre les vrais microservices n'est revendiqué.** Les services `service-donneurs`, `service-demandes`, `service-don-stock` et `service-partenaire` ne contiennent encore qu'un `.gitkeep`.

---

## 7. Commandes pour reproduire les tests

```powershell
# Depuis le dossier gateway/

# 1) Build + tous les tests (65 attendus)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp clean verify

# 2) Tests S2-07 seuls
mvn -B -ntp "-Dtest=DemoUserServiceTest,JwtTokenServiceTest,GatewaySecurityIntegrationTest" test

# 3) Démarrage réel : le secret et les quatre mots de passe sont locaux
$env:JWT_SECRET = "<secret local d'au moins 32 caracteres>"
$env:DEMO_DONOR_PASSWORD = "<mot de passe local>"
$env:DEMO_STAFF_PASSWORD = "<mot de passe local>"
$env:DEMO_HOPITAL_PASSWORD = "<mot de passe local>"
$env:DEMO_ADMIN_PASSWORD = "<mot de passe local>"
java -jar target/gateway-0.0.1-SNAPSHOT.jar

# 4) Connexion
curl.exe -s -X POST http://localhost:8090/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"donor\",\"password\":\"<mot de passe local>\"}"

# 5) Sans jeton : 401 JSON
curl.exe -s -i http://localhost:8090/api/v1/rendez-vous

# 6) Avec jeton : la requete atteint le microservice (ou 503 si le service est arrete)
curl.exe -s -i http://localhost:8090/api/v1/rendez-vous -H "Authorization: Bearer <token>"

# 7) Sante (publique)
curl.exe -s http://localhost:8090/actuator/health
```

> **Sans `JWT_SECRET` valide (≥ 32 caractères) ou sans les quatre mots de passe, l'application refuse de démarrer.** C'est le comportement voulu : aucune valeur secrète n'est stockée dans le dépôt.

---

## 8. Problèmes rencontrés et corrections

| Problème | Cause | Correction |
|---|---|---|
| `Jwt#getIssuer()` renvoyait une `URL` convertie de façon inattendue, un test échouait | claim `iss` ajouté puis lu comme `URL` | Suppression du claim `iss`, non exigé par l'énoncé ; validateur standard sans émetteur imposé |
| Un jeton **expiré** renvoyait un 401 **sans corps JSON** | le filtre `BearerTokenAuthenticationFilter` a son propre point d'entrée, distinct de `exceptionHandling()` | Ajout de `oauth2ResourceServer().authenticationEntryPoint(authenticationEntryPoint)` |
| Les tests S2-02 à S2-06 appelaient des routes protégées sans jeton | sécurité active par défaut | Propriété de test `gateway.security.permit-all=true`, remise à `false` par les tests S2-07 |
| Le helper de test de connexion appelait deux fois `/auth/login` | erreur de rédaction dans le helper | Appel unique, résultat réutilisé |
| Décodage Base64URL des jetons dans le script de vérification | absence de remplissage `=` | Ajout d'une fonction de décodage avec padding |
| Une ligne de script PowerShell contenait une conversion Base64 erronée | reste de rédaction | Ligne supprimée avant exécution |
| Guillemets PowerShell sur `-Dtest=...` | PowerShell interprète la virgule | Argument passé entre guillemets : `"-Dtest=A,B"` |

Aucune étape validée S2-02 à S2-06 n'a été modifiée fonctionnellement. Le seul fichier partagé touché est `JsonErrorWebExceptionHandler`, refactoré pour appeler `JsonErrorPayload` **sans changer son contrat ni son comportement** (ses 9 tests restent verts et la vérification manuelle reproduit exactement les mêmes corps JSON).

---

## 9. Limites restantes

1. **Aucun test avec les vrais microservices** : les dossiers des services sont vides ; la vérification manuelle utilise un backend simulé. Les tests finaux avec les vrais services restent à faire (étapes suivantes / S2-10).
2. **Gap 7 différé** : le jeton ne porte ni `donneurId` ni `hopitalId`, et le gateway ne transmet pas `X-User-Id`/`X-User-Role`. S2-08 ne pourra donc pas appliquer « un donneur ne voit que ses propres données » à partir du jeton ; il faudra soit accepter le gap 7, soit définir une autre source d'identité.
3. **Aucune règle de rôle (403)** : S2-07 ne fait que l'authentification. Un jeton `DONOR` peut appeler toute route protégée. C'est le périmètre de S2-08, non commencé.
4. **Pas de révocation ni de liste de jetons** : un JWT émis reste valable jusqu'à expiration (1 h). Aucun logout serveur, aucun refresh token.
5. **Comptes de démonstration en clair** : les mots de passe sont comparés en mémoire à des valeurs d'environnement, sans hachage ni annuaire. Acceptable pour une démonstration, pas pour la production.
6. **Pas de limitation de débit sur `/auth/login`** : une attaque par force brute n'est pas ralentie. Cela relève de S2-09 (bonus).
7. **CORS configuré à deux endroits** : `spring.cloud.gateway.globalcors` (S2-03) et `CorsConfigurationSource` (sécurité). Les deux lisent `CORS_ALLOWED_ORIGINS` et les mêmes méthodes/en-têtes, mais toute évolution future doit être répercutée aux deux endroits.
8. **404 sans `requestId`** : une route inconnue n'étant pas routée, le filtre S2-05 ne s'exécute pas ; le corps 404 ne contient donc pas `requestId`. Comportement S2-06 inchangé, non corrigé ici pour ne pas modifier une étape validée.
9. **Secrets locaux** : la vérification manuelle a utilisé un secret et des mots de passe locaux non consignés dans ce rapport et non commités. `.env.example` ne contient que des placeholders.

---

## 10. Conclusion et statut final

**S2-07 est réalisée localement et son critère d'acceptation est satisfait :**

- `POST /api/v1/auth/login` authentifie les quatre comptes de démonstration et renvoie un JWT **HS256 valable une heure** avec les claims `sub`, `role`, `iat`, `exp` ;
- le resource server vérifie le jeton avec le **même secret** ;
- **sans jeton** : **401 JSON** (contrat S2-06), avec `WWW-Authenticate: Bearer`, en-têtes CORS et `X-Request-Id` ;
- **avec jeton** : la requête **traverse** le gateway et atteint le backend ;
- un jeton expiré ou falsifié renvoie également un **401 JSON**, jamais du HTML ;
- `/actuator/health`, `/api/v1/auth/**` et les routes publiques de S2-08 sont accessibles sans jeton ;
- les étapes S2-02 à S2-06 sont préservées ;
- le gap 7 est **explicitement différé** et son absence est testée ;
- **build réel** : `mvn -B -ntp clean verify`, **EXITCODE = 0**, `BUILD SUCCESS`, **65 tests / 65 réussis**, 0 échec.

**État :** S2-07 terminée localement, en attente de validation explicite. **Aucun commit, aucun push.** S2-08 (règles de rôle) n'est **pas** commencée.
