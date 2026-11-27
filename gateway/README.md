# API Gateway — projet SOA de gestion des dons de sang

Passerelle réactive (Spring Cloud Gateway, WebFlux) placée devant les
microservices. Elle route les requêtes `/api/v1/**`, applique la politique
CORS, ajoute un identifiant de requête, gère les timeouts, vérifie les jetons
JWT, applique les règles de rôle et limite le débit par adresse IP.

- Java 17, Spring Boot 3.3.13, Spring Cloud 2023.0.6, Spring Security 6.3.10.
- Port par défaut : `8090` (`server.port` dans `src/main/resources/application.yml`).
- Les rapports détaillés par étape sont dans `RAPPORT_S2-02.md` à
  `RAPPORT_S2-09.md`.

## Démarrage

```powershell
$env:JWT_SECRET = "un-secret-de-32-caracteres-au-moins"
$env:DEMO_DONOR_PASSWORD = "..."
$env:DEMO_STAFF_PASSWORD = "..."
$env:DEMO_HOPITAL_PASSWORD = "..."
$env:DEMO_ADMIN_PASSWORD = "..."
mvn spring-boot:run
```

Sans microservices démarrés, les routes répondent `503` ou `504` ; la santé
`/actuator/health` reste disponible. Le fichier `.env.example` à la racine du
dépôt liste toutes les variables.

## Configuration

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `DONNEURS_URL` | `http://localhost:8081` | Cible du service Donneurs |
| `DEMANDES_URL` | `http://localhost:8082` | Cible du service Demandes |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Origines autorisées (séparées par des virgules) |
| `GATEWAY_CONNECT_TIMEOUT_MS` | `2000` | Timeout de connexion HTTP |
| `GATEWAY_RESPONSE_TIMEOUT_MS` | `10000` | Timeout de réponse HTTP (hors flux SSE) |
| `JWT_SECRET` | — (obligatoire) | Secret HMAC HS256, au moins 32 caractères |
| `RATE_LIMIT_ENABLED` | `true` | Active la limitation de débit |
| `RATE_LIMIT_PER_MIN` | `120` | Requêtes autorisées par IP et par minute |

## Routes

Le tableau ci-dessous reprend **les 10 définitions de routes** présentes dans
`src/main/resources/application.yml` (section `spring.cloud.gateway.routes`).
Il décrit la configuration du Gateway, pas le comportement observé des
microservices : la vérification contre les vrais services (Docker Compose)
reste à faire.

| `id` | Prédicat `Path` | Cible (variable, défaut) | `order` | Particularités | À confirmer avec les vrais services |
| --- | --- | --- | --- | --- | --- |
| `donneurs-centres-creneaux` | `/api/v1/centres/{id}/creneaux` | Donneurs — `DONNEURS_URL`, `http://localhost:8081` | `-1` | Déclarée avant `/api/v1/centres/**` et traitée en premier ; `{id}` est un segment du prédicat, le chemin est transmis inchangé | Méthodes acceptées et endpoint réel des créneaux |
| `donneurs-centres` | `/api/v1/centres/**` | Donneurs — `DONNEURS_URL`, `http://localhost:8081` | défaut | Reprend les sous-chemins de `/api/v1/centres` non couverts par la route des créneaux | Endpoints et méthodes réellement exposés sous `/api/v1/centres` |
| `donneurs-donneurs` | `/api/v1/donneurs/**` | Donneurs — `DONNEURS_URL`, `http://localhost:8081` | défaut | — | Endpoints et méthodes réellement exposés sous `/api/v1/donneurs` |
| `donneurs-rendez-vous` | `/api/v1/rendez-vous/**` | Donneurs — `DONNEURS_URL`, `http://localhost:8081` | défaut | — | Endpoints et méthodes réellement exposés sous `/api/v1/rendez-vous` |
| `donneurs-dons` | `/api/v1/dons/**` | Donneurs — `DONNEURS_URL`, `http://localhost:8081` | défaut | — | Endpoints et méthodes réellement exposés sous `/api/v1/dons` |
| `donneurs-certificats` | `/api/v1/certificats/**` | Donneurs — `DONNEURS_URL`, `http://localhost:8081` | défaut | — | Endpoints et méthodes réellement exposés sous `/api/v1/certificats` |
| `demandes-demandes` | `/api/v1/demandes/**` | Demandes — `DEMANDES_URL`, `http://localhost:8082` | défaut | — | Endpoints et méthodes réellement exposés sous `/api/v1/demandes` |
| `demandes-appels` | `/api/v1/appels/**` | Demandes — `DEMANDES_URL`, `http://localhost:8082` | défaut | — | Endpoints et méthodes réellement exposés sous `/api/v1/appels` |
| `demandes-stock-flux` | `/api/v1/stock/niveaux/flux` | Demandes — `DEMANDES_URL`, `http://localhost:8082` | `-1` | Flux SSE : `metadata.response-timeout: -1` (aucun timeout de réponse) ; route plus spécifique que `/api/v1/stock/**` | Format réel des événements SSE, fréquence d'émission et `Content-Type` du service |
| `demandes-stock` | `/api/v1/stock/**` | Demandes — `DEMANDES_URL`, `http://localhost:8082` | défaut | Route générale du stock ; ne s'applique pas au flux SSE, traité par la route précédente | Endpoints et méthodes réellement exposés sous `/api/v1/stock` |

Précisions vérifiables dans le fichier de configuration :

- **Aucun filtre de réécriture** (`StripPrefix`, `RewritePath`) n'est
  configuré : les chemins sont transmis tels quels. Les tests de routage
  S2-02 et S2-10 le vérifient côté Gateway.
- **Priorité** : `order: -1` place `donneurs-centres-creneaux` et
  `demandes-stock-flux` avant les routes génériques correspondantes. À `order`
  égal (valeur par défaut), l'ordre de déclaration du fichier est conservé.
- **Méthodes HTTP et endpoints métier** : `application.yml` ne contient que
  des prédicats `Path`. Aucune méthode ni aucun endpoint métier n'est déduit
  ici. Les règles d'accès (routes publiques, 401, 403) sont définies dans
  `SecurityConfiguration.java` et couvertes par les tests S2-08.
- Ces routes sont simulées avec WireMock dans les tests S2-10 ; elles n'ont
  pas été validées contre les microservices réels, absents du dépôt.

## Limitation de débit (S2-09)

Le filtre est inséré dans la chaîne Spring Security, après le traitement CORS
et avant l'authentification. Il s'applique donc aussi à `/api/v1/auth/login` et
aux requêtes anonymes.

- **Clé** : adresse distante de la connexion (socket). L'en-tête
  `X-Forwarded-For` n'est **pas** utilisé : il est fourni par le client et
  falsifiable tant qu'aucun proxy de confiance n'est configuré.
- **Fenêtre fixe** de 60 secondes. Au-delà de `RATE_LIMIT_PER_MIN` requêtes dans
  la fenêtre, la réponse est un **429** JSON :
  `timestamp`, `status`, `error`, `message`, `path`, `requestId`, avec
  `Retry-After` (secondes restantes), `X-Request-Id` et les en-têtes CORS.
- **Exclusions** : `/actuator/health` et ses sous-routes, exactement
  `/api/v1/stock/niveaux/flux` (flux SSE, jamais compté ni transformé), et les
  preflights `OPTIONS`.
- Une fois le quota épuisé, le 429 est produit **avant** l'authentification :
  il peut donc précéder un 401 ou un 403. C'est le comportement attendu d'une
  protection d'entrée.

### Limites d'un compteur en mémoire

> **La limite est locale à chaque instance du Gateway. Elle n'est pas partagée
> entre plusieurs instances.** Avec N instances derrière un répartiteur de
> charge, un client peut consommer jusqu'à N × `RATE_LIMIT_PER_MIN` requêtes
> par minute, et un redémarrage remet tous les compteurs à zéro.

Autres limites assumées :

- **Fenêtre fixe** : autour d'une frontière de minute, un client peut envoyer
  jusqu'à deux fois la limite en peu de temps.
- **NAT / proxy** : tous les clients derrière une même adresse IP publique
  partagent un seul quota.
- **Mémoire** : les fenêtres expirées sont purgées lorsque la carte dépasse
  10 000 clés, mais un afflux d'IP distinctes dans une même minute peut
  dépasser temporairement ce seuil.
- **Portée** : ce mécanisme protège l'entrée contre des rafales ou des boucles
  simples ; il ne remplace ni une authentification, ni un quota métier par
  utilisateur, ni une protection distribuée (Redis, API management).

## Tests

```powershell
mvn -B -ntp clean verify
```

La limitation est désactivée dans `src/test/resources/application.properties`
pour ne pas modifier les suites S2-02 à S2-08 ; les tests S2-09 l'activent
explicitement avec une limite basse. Les tests unitaires du compteur utilisent
une horloge contrôlée et n'attendent jamais 60 secondes.
