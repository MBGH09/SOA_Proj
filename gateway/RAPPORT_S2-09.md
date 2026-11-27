# Rapport technique — S2-09 : limitation de débit (rate limiter)

> **Note de vérification.** Ce rapport décrit **l'état final** du composant
> `gateway` après réalisation de S2-09. Le build et les tests automatisés ont
> été **réellement exécutés le 18/11/2026** ; les chiffres cités proviennent
> des sorties conservées de ces exécutions. La démonstration manuelle a elle
> aussi été réellement exécutée (gateway sur le port 18090 + backend HTTP
> simulé sur le port 18083). Les « microservices » de cette vérification sont
> un **serveur simulé, hors dépôt** : les dossiers réels des microservices ne
> contiennent encore qu'un `.gitkeep`, et **aucun test contre les vrais
> microservices ni contre le flux SSE de production n'est revendiqué**.

---

## 1. Exigences officielles et critère adopté

### 1.1 Source

L'étape est décrite dans le guide officiel
`docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`,
branche `origin/develop`, section **« S2-09 Rate limiter (optional) »**
(ligne 306 de la version consultée) :

> **S2-09 Rate limiter (optional).** Why: it protects the entry point, and it
> is a small bonus topic for the defense. Write a filter with an in-memory
> counter per IP (`RATE_LIMIT_PER_MIN`, for example 120). Skip the health and
> the stream routes, and answer 429 in the JSON format. Write in the README
> that an in-memory limit is not shared between several Gateway instances.

Aucune implémentation de référence n'existe : `origin/develop:gateway` ne
contient qu'un `.gitkeep`, et les branches `origin/configuration` / `origin/config`
n'ont qu'un squelette `com.soa.gateway` sans sécurité. Aucun autre document du
dépôt ne précise S2-09.

### 1.2 Décomposition des exigences

| # | Exigence officielle | Mise en œuvre |
| --- | --- | --- |
| 1 | Filtre avec compteur **en mémoire** | `RateLimitCounter` : `ConcurrentHashMap`, aucune dépendance externe |
| 2 | Compteur **par IP** | Clé = adresse distante de la connexion |
| 3 | `RATE_LIMIT_PER_MIN`, exemple **120** | `gateway.rate-limit.per-minute`, défaut `120`, surchargé par la variable |
| 4 | **Ignorer** la santé et la route de flux | `/actuator/health` (+ sous-routes) et exactement `/api/v1/stock/niveaux/flux` |
| 5 | Réponse **429 au format JSON** | Même contrat que S2-06/S2-07/S2-08, plus `Retry-After` |
| 6 | README : limite **non partagée** entre instances | `gateway/README.md`, section « Limites d'un compteur en mémoire » |

### 1.3 Absence de « Done when » officiel et critère adopté

Le paragraphe S2-09 ne comporte **aucun critère de validation**. La ligne 304
(« a donor token gets 403 on `POST /dons`, and a staff token gets 200 »)
appartient à **S2-08**, et la section suivante (ligne 310) est **S2-10**.

Le critère adopté, validé avant implémentation, est le suivant :

> Avec `RATE_LIMIT_PER_MIN=N`, les N premières requêtes d'une même IP passent,
> la N+1e reçoit un **429 JSON** ; les routes de santé et de flux SSE ne
> reçoivent jamais 429 ; les preflights CORS ne sont pas limités ; la fenêtre
> se réinitialise après 60 secondes ; CORS et `X-Request-Id` restent présents
> sur les réponses 429 ; le README documente les limites d'un compteur en
> mémoire.

---

## 2. Architecture retenue

### 2.1 Position dans la chaîne

```
Requête
  │
  ├─ CORS (Spring Security)          → en-têtes CORS posés avant tout refus
  ├─ RateLimitWebFilter (S2-09)      → compte l'IP, ou écrit le 429
  ├─ AUTHENTICATION (JWT)            → 401 si jeton absent/invalide
  ├─ AUTHORIZATION                   → 403 si rôle insuffisant
  └─ Routage Spring Cloud Gateway    → RequestIdFilter, proxy, SSE
```

Le filtre est ajouté par
`http.addFilterBefore(rateLimitFilter, SecurityWebFiltersOrder.AUTHENTICATION)`,
après le traitement CORS déjà présent dans la chaîne. Ce placement couvre :

- les routes publiques, les routes protégées et les requêtes anonymes ;
- `/api/v1/auth/login`, qui est un contrôleur du gateway et non une route
  proxifiée ;
- `/actuator/health`, avec une exclusion explicite (sans elle, la supervision
  consommerait le quota).

### 2.2 Composants

| Composant | Emplacement | Responsabilité |
| --- | --- | --- |
| `RateLimitProperties` | `security/` | Binding de `gateway.rate-limit` : `enabled` (défaut `true`), `per-minute` (défaut `120`) |
| `RateLimitCounter` | `security/` | Compteur à fenêtre fixe de 60 s, atomique par clé, purge des fenêtres expirées, `reset()` pour les tests |
| `RateLimitWebFilter` | `security/` | Exclusions, incrément, écriture du 429 JSON, journalisation |
| `SecurityConfiguration` | `security/` (modifié) | Bean compteur, insertion du filtre avant `AUTHENTICATION` |

### 2.3 Choix de la clé et confiance dans les en-têtes

La clé est
`exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()`.
`X-Forwarded-For` et `Forwarded` sont **ignorés** : ces en-têtes sont fournis
par le client et falsifiables tant qu'aucun proxy de confiance n'est
configuré. Ce choix est assumé et documenté dans le README.

### 2.4 Pourquoi pas un `GlobalFilter`, pourquoi pas un `@Component`

- Un `GlobalFilter` de routage ne voit ni `/actuator/health`, ni
  `/api/v1/auth/login`, ni les requêtes anonymes refusées avant le routage :
  il ne protégerait pas réellement l'entrée.
- Un `WebFilter` annoté `@Component` serait **à la fois** auto-enregistré par
  WebFlux et inséré dans la chaîne de sécurité : la limite serait appliquée
  deux fois. L'instance est donc créée dans `SecurityConfiguration`, sans
  annotation de bean.

### 2.5 Priorité du 429 sur 401/403

Le filtre s'exécute avant l'authentification. Une fois le quota épuisé, une
requête anonyme vers une route protégée reçoit **429** et non 401 ; un jeton
valide mais non autorisé reçoit 429 et non 403. Ce comportement est celui d'une
protection d'entrée : il est **assumé, testé et documenté**. Sous le quota, les
comportements 401 et 403 de S2-08 restent inchangés (vérifié en test et en
manuel).

---

## 3. Comportement et contrat

### 3.1 Exclusions

| Chemin / méthode | Motif |
| --- | --- |
| `OPTIONS` (toutes routes) | Ne pas casser les preflights CORS |
| `/actuator/health` et `/actuator/health/**` | Supervision, ne doit jamais être limitée |
| exactement `/api/v1/stock/niveaux/flux` | Flux SSE de S2-04 : jamais compté, jamais bufferisé ni transformé |

L'exclusion du flux est **exacte** : `/api/v1/stock/niveaux` et les autres
routes `/api/v1/stock/**` restent limités.

### 3.2 Corps du 429

```json
{
  "timestamp": "2026-11-18T19:43:11.468638500+01:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Trop de requetes : la limite de la passerelle est atteinte.",
  "path": "/api/v1/centres",
  "requestId": "s209-demo-429"
}
```

Le corps est produit par `JsonErrorPayload`, comme les erreurs 4xx/5xx des
étapes précédentes. Aucune page HTML n'est renvoyée.

### 3.3 En-têtes du 429

| En-tête | Valeur |
| --- | --- |
| `Content-Type` | `application/json` |
| `Content-Length` | Taille du corps |
| `Retry-After` | Secondes restantes dans la fenêtre (1 à 60) |
| `X-Request-Id` | Identifiant entrant repris, sinon généré (même valeur que dans le corps) |
| `Access-Control-Allow-Origin` | Posé par le traitement CORS de Spring Security, déjà exécuté |

`Retry-After` est le seul en-tête de quota ; aucun `X-RateLimit-*` n'a été
ajouté (non demandé). Il est présent sur la réponse mais n'a **pas** été ajouté
à `Access-Control-Expose-Headers`, afin de ne pas modifier la politique CORS de
S2-03 : un front cross-origin ne peut donc pas le lire directement en
JavaScript. Il peut s'appuyer sur le statut 429 et sur le corps JSON.

### 3.4 Fenêtre fixe et purge

- Fenêtre de **60 secondes** indexée sur l'horloge murale (`now / 60 000`).
- Incrément atomique par clé via `ConcurrentHashMap.compute` : deux requêtes
  simultanées de la même IP ne perdent pas d'incrément.
- Purge des fenêtres expirées lorsque la carte dépasse **10 000 clés**, au plus
  une fois par fenêtre, afin de ne pas parcourir la carte à chaque requête.
- `reset()` existe pour les tests et une remise à zéro locale ; **aucun
  endpoint HTTP** ne l'expose.

---

## 4. Fichiers créés et modifiés

### 4.1 Créés

| Fichier | Contenu |
| --- | --- |
| `gateway/src/main/java/tn/iit/blood/gateway/security/RateLimitProperties.java` | Configuration `gateway.rate-limit` |
| `gateway/src/main/java/tn/iit/blood/gateway/security/RateLimitCounter.java` | Compteur fenêtré en mémoire |
| `gateway/src/main/java/tn/iit/blood/gateway/security/RateLimitWebFilter.java` | Filtre et réponse 429 |
| `gateway/src/test/java/tn/iit/blood/gateway/security/RateLimitCounterTest.java` | 8 tests unitaires à horloge contrôlée |
| `gateway/src/test/java/tn/iit/blood/gateway/GatewayRateLimitIntegrationTest.java` | 9 tests d'intégration bout en bout |
| `gateway/README.md` | Documentation du gateway, dont les limites du compteur |
| `gateway/RAPPORT_S2-09.md` | Ce rapport |

### 4.2 Modifiés

| Fichier | Modification |
| --- | --- |
| `gateway/src/main/java/tn/iit/blood/gateway/security/SecurityConfiguration.java` | `RateLimitProperties` activé, bean `RateLimitCounter`, insertion du filtre avant `AUTHENTICATION` |
| `gateway/src/main/resources/application.yml` | Bloc `gateway.rate-limit` (`enabled`, `per-minute`) |
| `gateway/src/test/resources/application.properties` | `gateway.rate-limit.enabled=false` pour préserver les suites existantes |
| `.env.example` | `RATE_LIMIT_ENABLED`, `RATE_LIMIT_PER_MIN` |

### 4.3 Inchangés

- `pom.xml` : aucune dépendance ajoutée.
- Routes, CORS, timeouts, claims JWT, matrice de rôles de S2-08.
- Tests et rapports S2-02 à S2-08.
- README racine du dépôt.

---

## 5. Tests automatisés

### 5.1 Tests unitaires — `RateLimitCounterTest` (8)

Horloge injectée (`AtomicLong`), aucune attente réelle.

1. N requêtes autorisées, N+1 refusée.
2. Réinitialisation au début de la fenêtre suivante.
3. Clés (IP) indépendantes.
4. `Retry-After` = temps restant dans la fenêtre (55 s puis 1 s, arrondi supérieur).
5. Purge des fenêtres expirées au-delà du seuil.
6. Aucune suppression des clés de la fenêtre courante.
7. `reset()` remet tous les compteurs à zéro.
8. Rejet des configurations invalides (limite < 1, seuil < 1, horloge nulle).

### 5.2 Tests d'intégration — `GatewayRateLimitIntegrationTest` (9)

Contexte Spring réel, `permit-all=false`, `rate-limit.enabled=true`,
`rate-limit.per-minute=3`, backend HTTP simulé, compteur remis à zéro avant
chaque test.

1. Trois requêtes proxifiées (`200`), la quatrième reçoit `429`.
2. Contrat JSON complet + `Retry-After` entre 1 et 60 + CORS + `X-Request-Id`.
3. `/actuator/health` : 8 appels jamais limités et non comptés (le quota reste
   intact ensuite).
4. Flux SSE : 6 appels authentifiés en `text/event-stream`, jamais limités, et
   non comptés.
5. Preflights `OPTIONS` : 6 appels `200` avec CORS, non comptés.
6. `/api/v1/auth/login` est limité comme les autres routes.
7. Sous le quota : anonyme sur route protégée → **401 JSON**.
8. Sous le quota : jeton donneur sur `POST /dons` → **403 JSON**.
9. Quota épuisé : une requête anonyme reçoit **429** avant l'authentification.

### 5.3 Résultats réels

| Suite | Tests | Échecs | Erreurs |
| --- | --- | --- | --- |
| `RateLimitCounterTest` | 8 | 0 | 0 |
| `GatewayRateLimitIntegrationTest` | 9 | 0 | 0 |
| **Nouveaux tests S2-09** | **17** | **0** | **0** |
| Suites S2-02 à S2-08 (inchangées) | 142 | 0 | 0 |
| **Total** | **159** | **0** | **0** |

### 5.4 Nature exacte des tests

Les tests d'intégration utilisent un `HttpServer` du JDK comme backend simulé.
Ils ne démarrent **aucun** microservice réel, ne testent **pas** le flux SSE de
production et ne prouvent pas le comportement derrière un proxy ou un
répartiteur de charge. Les tests unitaires couvrent la logique du compteur avec
une horloge contrôlée ; le passage réel d'une fenêtre a été vérifié
manuellement (voir § 7).

---

## 6. Build

Commande exécutée dans `gateway` :

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp clean verify
```

| Élément | Résultat |
| --- | --- |
| JDK | 17.0.12 |
| Tests | **159 exécutés, 0 échec, 0 erreur, 0 ignoré** |
| Résultat Maven | `BUILD SUCCESS` |
| Code de sortie réel | **`EXITCODE=0`** |
| Durée | 1 min 43 s |
| Artefact | `target/gateway-0.0.1-SNAPSHOT.jar` |

Aucun test existant n'a été supprimé ni désactivé pour obtenir ce résultat. La
seule mesure de non-régression est la désactivation de la limitation dans les
**ressources de test communes**, les tests S2-09 l'activant explicitement.

---

## 7. Démonstration manuelle

### 7.1 Conditions réelles

| Élément | Valeur |
| --- | --- |
| Gateway | JAR issu de `clean verify`, port **18090** (`--server.port`) |
| Backend simulé | `HttpServer` Java, `127.0.0.1:18083`, hors dépôt |
| `RATE_LIMIT_ENABLED` | `true` |
| `RATE_LIMIT_PER_MIN` | `5` |
| Sécurité | mode réel (`permit-all` absent), utilisateurs de démonstration |
| Flux SSE simulé | `Content-Type: text/event-stream`, corps `data: {"stock":42}` |

Un login initial puis une attente de 61 s ont été utilisés pour partir d'une
fenêtre propre. Le même délai a servi à vérifier la réinitialisation.

### 7.2 Résultats

| Contrôle | Résultat observé |
| --- | --- |
| 5 × `GET /api/v1/centres` | `200, 200, 200, 200, 200` |
| 6e `GET /api/v1/centres` | **`429`** |
| Corps du 429 | JSON conforme, `requestId=s209-demo-429` |
| `Content-Type` du 429 | `application/json` |
| `Retry-After` | `49` (secondes restantes) |
| `Access-Control-Allow-Origin` | `http://localhost:3000` |
| `X-Request-Id` | `s209-demo-429` (valeur entrante reprise) |
| 8 × `GET /actuator/health` | 8 × `200` |
| 6 × `GET /api/v1/stock/niveaux/flux` | 6 × `200`, `text/event-stream`, corps `data: {"stock":42}` |
| 6 × `OPTIONS /api/v1/dons` | 6 × `200` avec `Access-Control-Allow-Origin` |
| `GET /api/v1/centres` après ces exclusions | **`429`** : la santé, le flux et les preflights n'ont pas été comptés |
| Après 61 s, `GET /api/v1/centres` | **`200`** : fenêtre réinitialisée |
| Anonyme `GET /api/v1/rendez-vous` | **`401`** JSON |
| Login donneur puis `POST /api/v1/dons` | login `200`, puis **`403`** JSON |

Extrait du corps 429 réellement observé :

```json
{"timestamp":"2026-11-18T19:43:11.468638500+01:00","status":429,"error":"Too Many Requests","message":"Trop de requetes : la limite de la passerelle est atteinte.","path":"/api/v1/centres","requestId":"s209-demo-429"}
```

### 7.3 Journal du gateway

Sur toute la démonstration, le filtre a produit **exactement deux lignes
WARN**, correspondant aux deux seuls refus 429 :

```text
WARN [] t.i.b.g.security.RateLimitWebFilter - Reponse d'erreur 429 pour GET /api/v1/centres depuis 127.0.0.1 (requestId=s209-demo-429, retryAfter=49s)
WARN [] t.i.b.g.security.RateLimitWebFilter - Reponse d'erreur 429 pour GET /api/v1/centres depuis 127.0.0.1 (requestId=e7863480-6a7b-4b2c-a150-c132d0b3612e, retryAfter=49s)
```

Aucune ligne n'est émise pour les requêtes autorisées : le journal ne devient
pas bruyant en fonctionnement normal.

### 7.4 Ce que la démonstration ne prouve pas

- L'isolation entre deux adresses IP est prouvée par les tests unitaires, pas
  manuellement : la machine de démonstration n'a qu'une seule IP source vue par
  le gateway.
- Le comportement derrière un proxy, un NAT ou plusieurs instances n'est pas
  testé.
- Le flux SSE réel de `service-demandes` n'existe pas encore ; le flux simulé
  vérifie seulement que la route est exclue et que le corps traverse sans être
  transformé.

---

## 8. Limitations

### 8.1 Compteur local, non partagé entre instances

> **La limite est locale à chaque instance du Gateway. Elle n'est pas partagée
> entre plusieurs instances.**

Avec N instances derrière un répartiteur de charge, un client peut consommer
jusqu'à N × `RATE_LIMIT_PER_MIN` requêtes par minute, et un redémarrage remet
les compteurs à zéro. Une limite réellement distribuée exigerait un stockage
partagé (Redis) ou un API management, hors périmètre de S2-09. Cette mention
figure dans `gateway/README.md`, conformément à l'exigence officielle.

### 8.2 Fenêtre fixe

La fenêtre est fixe : autour d'une frontière de minute, un client peut envoyer
jusqu'à deux fois la limite en très peu de temps (par exemple N requêtes en fin
de fenêtre puis N au début de la suivante). C'est le compromis simple retenu
par l'énoncé (« counter per minute ») ; une fenêtre glissante ou un
_token bucket_ n'a pas été implémenté.

### 8.3 NAT, proxy et `X-Forwarded-For`

Tous les clients derrière une même adresse IP publique partagent un quota. Le
gateway n'utilise pas `X-Forwarded-For`, qui serait falsifiable. Derrière un
proxy de confiance, il faudrait configurer explicitement la résolution de
l'adresse réelle ; ce n'est pas fait ici.

### 8.4 Croissance mémoire

Les fenêtres expirées sont purgées lorsque la carte dépasse 10 000 clés, au
plus une fois par fenêtre. Un afflux d'IP distinctes dans une même minute peut
donc dépasser temporairement ce seuil ; la croissance n'est pas durable, mais
la borne n'est pas stricte.

### 8.5 Portée de la protection

Ce mécanisme protège l'entrée contre des rafales et des boucles simples. Il ne
remplace ni l'authentification, ni les règles de rôle, ni un quota métier par
utilisateur, ni une limitation distribuée. Le login est limité par IP, pas par
compte : un attaquant distribué peut toujours tenter plusieurs identifiants
depuis plusieurs IP.

### 8.6 Absence des vrais microservices

Les dossiers des microservices ne contiennent encore qu'un `.gitkeep`. Tous les
contrôles de cette étape utilisent un backend simulé. Aucun test contre les
vrais services, le vrai flux SSE ou une topologie multi-instances n'est
revendiqué.

---

## 9. Non-régression S2-02 à S2-08

### 9.1 Suites existantes

Les **142** tests des étapes précédentes restent verts et n'ont pas été
modifiés. Le total passe à **159** avec les 17 tests S2-09.

### 9.2 Désactivation dans les ressources de test

`gateway/src/test/resources/application.properties` contient désormais
`gateway.rate-limit.enabled=false`. Sans cela, `GatewayAuthorizationIntegrationTest`
envoie environ 155 requêtes en quelques secondes depuis `127.0.0.1` et
franchirait la limite de 120/min. Les tests S2-09 activent explicitement la
limitation avec `per-minute=3`.

En production, la limitation est active par défaut (`enabled=true`,
`per-minute=120`).

### 9.3 Éléments non modifiés

- Matrice de rôles, routes publiques/protégées, préfixes d'API.
- CORS, timeouts, route SSE, `RequestIdFilter`, `UserIdentityHeaderFilter`.
- Claims JWT (`sub`, `role`, `iat`, `exp`) et conversion des rôles.
- Contrats 401/403/404/500/503/504.
- `pom.xml` et dépendances.

### 9.4 `gateway.security.permit-all`

Ce commutateur reste une échappatoire **réservée aux tests**. Il n'est pas
défini dans `application.yml` ; la limitation est insérée dans la branche
sécurisée, donc `permit-all=true` n'active pas le filtre. Le point de savoir
s'il faut maintenir ou retirer cette échappatoire reste ouvert (voir § 10).

---

## 10. État Git et points restant à valider

### 10.1 État Git

- Aucun commit, aucun push effectués.
- Dernier commit du dépôt inchangé : `14a8c62 Revert accidental merge of CNTS Spring WS into main`.
- `git status --short` montre uniquement les fichiers de travail déjà non
  suivis (`gateway/`, `gateway.zip`) et les modifications locales attendues
  (`.env.example`, `.gitignore`).
- Les rapports S2-02 à S2-08 n'ont pas été modifiés.

### 10.2 Points antérieurs toujours ouverts (non bloquants pour S2-09)

1. **Gap 7 / identité métier** : les JWT ne portent pas d'identifiant de
   donneur ou d'hôpital ; la propriété des données reste déléguée à Donneurs.
2. **Lecture des demandes** : `GET /api/v1/demandes` et `/{id}` restent
   `authenticated()` ; le rôle attendu n'est pas tranché.
3. **`permit-all`** : maintenir ou retirer l'échappatoire de test.
4. **403 sans `WWW-Authenticate`** : contrat côté front à confirmer.

### 10.3 Points spécifiques à S2-09

1. La limite de 120/min par défaut est un choix de l'énoncé ; elle n'a pas été
   calibrée sur une charge réelle.
2. Le 429 avant 401/403 est assumé ; si le front doit distinguer
   systématiquement « non authentifié » de « trop de requêtes », ce point devra
   être réévalué.
3. Le compteur local ne convient pas à un déploiement multi-instances sans
   limite partagée ; cette limite est documentée mais non corrigée.

---

## Conclusion

S2-09 est implémentée conformément au périmètre officiel et aux décisions
validées : compteur en mémoire par IP, limite configurable (120/min par défaut),
exclusions de la santé, du flux SSE et des preflights, réponse 429 JSON avec
`Retry-After`, CORS et `X-Request-Id`, purge des fenêtres expirées, et
documentation explicite de l'absence de partage entre instances.

Le build complet passe avec **159 tests, 0 échec, `EXITCODE=0`**, dont les
**142 tests existants inchangés**. La démonstration manuelle confirme les
comportements attendus, y compris la réinitialisation après 60 s et la
non-régression des 401/403 de S2-08 sous le quota.

**S2-09 est livrée localement et attend une validation explicite.** Aucun
commit, aucun push, et S2-10 n'a pas été commencée.
