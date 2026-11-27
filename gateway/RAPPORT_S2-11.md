# Rapport final — Phase 6 : S2-09, S2-10, S2-11 (Gateway)

## 1. Contexte Git

| Élément | Valeur |
| --- | --- |
| Branche | `feature/Gateway` |
| HEAD | `bad3806 fix(gateway): propagate request id through Reactor context` |
| Commit de référence | `de7fd43afa2d63a8765237b6623616d6bbccd93c` (tests WireMock S2-10) |
| Tag | `v1.0-stable` sur `de7fd43` |
| Distant | `origin/feature/Gateway` = `bad3806` (synchronisé avant les travaux de ce rapport) |
| Fichier local préservé | `gateway.zip` (non suivi, ~33 Mo), intact |

Aucun `git reset`, `git clean`, push forcé, suppression de fichier ni push n'a
été effectué ; aucun commit n'avait été créé avant la clôture de cette phase.

## 2. Sources des exigences

Guide officiel `docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`
sur `origin/develop` :

- **L306 — S2-09** : *Rate limiter (optional)* ; compteur en mémoire par IP,
  `RATE_LIMIT_PER_MIN`, ignorer santé et flux, 429 JSON, documenter l'absence de
  partage entre instances.
- **L310-315 — S2-10** : tests WireMock (préfixes, CORS, 401/403/200, 503, 504,
  `X-Request-Id`, SSE), puis vrais services en Compose et tableau des routes à
  jour.
- **L319-330 — S2-11** : Dockerfile à partir du modèle de Student 7, **tous les
  réglages par variables d'environnement**, démo de panne avec 503 JSON propre,
  README avec obtention de token et appel API, variable Postman `{{token}}`, et
  rédaction de la partie sécurité du rapport.

Modèle disponible : `origin/config:gateway/Dockerfile` et
`origin/config:.dockerignore` (Student 7). Son `docker-compose.yml` fixe
`SERVER_PORT: 8080` pour le gateway et expose `8080:8080`.

## 3. Travaux réalisés

### 3.1 S2-09 — aucun ajout nécessaire

Le rate limiter était **déjà implémenté** (17 tests, `RAPPORT_S2-09.md`,
documentation des limites). L'étant explicitement *optional*, il n'a pas été
réécrit. Aucun test S2-09 n'a été modifié.

### 3.2 S2-10 — vérification

Les 19 tests WireMock et les 159 tests antérieurs ont été exécutés avec succès.
Le détail figure dans **`RAPPORT_S2-10.md`** (nouveau). Le tableau des 10 routes
du README a été revérifié : 10 identifiants dans `application.yml`, 10 lignes
dans le README, différence vide.

### 3.3 S2-11 — livrables ajoutés ou complétés

| Élément | Action |
| --- | --- |
| `gateway/Dockerfile` (nouveau) | Reprend le modèle multi-stage Student 7 (Maven 3.9.9 / Temurin 17, `curl` pour le healthcheck). Contexte de build = racine du dépôt. `EXPOSE 8080 8090` pour couvrir le port Compose et le défaut local. |
| `.dockerignore` (nouveau) | Dérivé du modèle Student 7, avec exclusion supplémentaire de `gateway.zip` et `*.zip` (~33 Mo non envoyés au daemon). |
| `gateway/src/main/resources/application.yml` (modifié) | `server.port: "${SERVER_PORT:8090}"` au lieu de `8090` codé en dur. Le `SERVER_PORT=8080` du Compose Student 7 est désormais respecté, sans changer le défaut local. |
| `gateway/README.md` (modifié) | Ajout des prérequis, de `SERVER_PORT`, des commandes Docker, de l'exemple `login` + `curl` avec `jq -r .token`, de la note Postman `{{token}}`, de la démo 503, des résultats Maven et du lien vers les rapports S2-10/S2-11. Sections Routes et Rate limit conservées. |
| `gateway/RAPPORT_S2-10.md` (nouveau) | Synthèse des tests WireMock et des limites Compose/CI. |
| `gateway/RAPPORT_S2-11.md` (nouveau) | Ce rapport. |

Aucune autre dépendance ni configuration de production n'a été ajoutée. Les
secrets restent exclusivement fournis par variables d'environnement.

## 4. Commandes exécutées et résultats réels

### 4.1 Build et tests (après toutes les modifications)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp clean verify
```

```text
[INFO] Tests run: 182, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:09 min
MVN_EXIT=0
```

Répartition : 159 tests S2-02 à S2-09 + 19 WireMock S2-10 + 4 tests du correctif
`RequestIdFilter`. Exécution antérieure de `mvn -B -ntp test -Dtest="GatewayWireMock*"` :
**19 tests, 0 échec, 0 erreur, BUILD SUCCESS, code 0**.

### 4.2 Vérification du tableau des routes

```text
Routes YAML=10 ; README=10 ; identiques=True
```

### 4.3 Docker

```text
docker --version            → Docker version 29.8.2, build 7fc2dff   (exit 0)
docker compose version      → Docker Compose version v5.5.1          (exit 0)
docker info                 → échec : npipe:////./pipe/dockerDesktopLinuxEngine
                               « The system cannot find the file specified » (exit 1)
```

Le client est installé mais le **démon Docker n'est pas démarré**. La construction
de l'image (`docker build -f gateway/Dockerfile -t soa-gateway:s2-11 .`) n'a donc
**pas pu être exécutée** ; le Dockerfile n'a été validé que par revue statique
(par rapport au modèle de Student 7).

### 4.4 Démo de panne : 503 JSON propre

Le JAR issu du `clean verify` a été lancé sur le port `18090` avec les variables
`JWT_SECRET` et `DEMO_*_PASSWORD` renseignées, **sans aucun backend sur 8081/8082**.

```text
GET http://127.0.0.1:18090/actuator/health
{"status":"UP"}

GET http://127.0.0.1:18090/api/v1/centres
HTTP/1.1 503 Service Unavailable
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Request-Id: b0afb449-6d09-4594-861d-c1b366b5af04
Content-Type: application/json
Content-Length: 233
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0
Referrer-Policy: no-referrer

{"timestamp":"2026-11-24T14:26:48.545008700+01:00","status":503,"error":"Service Unavailable","message":"Le service demande est momentanement indisponible.","path":"/api/v1/centres","requestId":"b0afb449-6d09-4594-861d-c1b366b5af04"}
```

Le processus a été arrêté ensuite ; aucun port `18090` ni processus JAR résiduel
n'a été laissé. Ce résultat confirme le 503 JSON attendu, avec `X-Request-Id`,
sans page HTML. Il est aussi couvert automatiquement par
`GatewayWireMockBackendDownIntegrationTest`.

## 5. Limites restantes

1. **Docker** : démon indisponible → image non construite, healthcheck Compose non exécuté.
2. **Vrais microservices** : les dossiers `service-donneurs`, `service-demandes`,
   `service-don-stock`, `service-partenaire` et `frontend` ne contiennent qu'un
   `.gitkeep`. Aucun `curl` S2-10 contre les vrais services n'est possible.
3. **Compose / CI** : `docker-compose.yml` et `.gitlab-ci.yml` font 2 octets
   (vides) dans cette branche. La condition « tests verts en CI » et la
   conformité du tableau des routes au comportement réel ne peuvent pas être
   affirmées. Ces fichiers appartiennent à Student 7 et n'ont pas été modifiés.
4. **Compose Student 7** : le service `gateway` du modèle ne fournit que
   `SERVER_PORT=8080`. Il devra aussi recevoir `JWT_SECRET` (≥ 32 caractères ;
   sinon `SecurityConfiguration` lève `IllegalStateException` au démarrage,
   ligne 76), les quatre `DEMO_*_PASSWORD` (aucune valeur par défaut) et
   `DONNEURS_URL=http://service-donneurs:8081` /
   `DEMANDES_URL=http://service-demandes:8082` : sans ces URL, les défauts
   `localhost:8081`/`8082` désignent le conteneur du Gateway lui-même.
5. **`.env.example`** est vide (2 octets) dans la branche actuelle ; le README
   porte la liste des variables et précise ce point.
6. **Rate limiter** : compteur local non partagé entre instances (limite déjà
   documentée en S2-09).

## 6. Fichiers modifiés ou créés pendant cette phase

| Fichier | Nature |
| --- | --- |
| `gateway/Dockerfile` | créé |
| `.dockerignore` (racine) | créé |
| `gateway/src/main/resources/application.yml` | modifié (`SERVER_PORT`) |
| `gateway/README.md` | modifié (prérequis, config, commandes, API, 503, tests, rapports) |
| `gateway/RAPPORT_S2-10.md` | créé |
| `gateway/RAPPORT_S2-11.md` | créé |

Non modifiés : `pom.xml`, tout autre fichier `src/main/**` et `src/test/**`,
`docker-compose.yml`, `.gitlab-ci.yml`, `.env.example`, `.gitignore`, les
rapports S2-02 à S2-09, et `gateway.zip`.

## 7. État Git final

```text
## feature/Gateway...origin/feature/Gateway
 M gateway/README.md
 M gateway/src/main/resources/application.yml
?? .dockerignore
?? gateway/Dockerfile
?? gateway/RAPPORT_S2-10.md
?? gateway/RAPPORT_S2-11.md
?? gateway.zip
```

Les constats ci-dessus décrivent l'état **avant** le commit Phase 6 attendu.
Aucun `git push` n'a été effectué ; `gateway.zip` reste non suivi et inchangé.

## Conclusion

S2-09 est confirmé conforme et facultatif, S2-10 est vérifié avec 19 tests
WireMock et 182 tests au total, et les livrables S2-11 disponibles localement
(Dockerfile, `.dockerignore`, configuration par environnement, README, démo 503
et rapports) sont en place. Les seules validations restantes dépendent de
l'environnement extérieur : démon Docker, vrais microservices et
Compose/CI de Student 7.
