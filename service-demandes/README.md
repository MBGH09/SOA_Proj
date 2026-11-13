# service-demandes (REST, port 8082)

Demandes de sang des hôpitaux, appels publics, compensation. Contrat : `../contracts/openapi/demandes.yaml`.

## Lancer en local

Il faut Java 17 et une base PostgreSQL vide nommée `demandes`.

```bash
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=<ton mot de passe>      # jamais dans Git
# Don & Stock simulé (tant que le client gRPC S6-09 n'existe pas) :
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

Flyway crée les tables au démarrage. Santé : <http://localhost:8082/actuator/health>. Documentation : <http://localhost:8082/swagger-ui.html>.

## Variables d'environnement

| Variable | Défaut | Rôle |
|---|---|---|
| `DEMANDES_DB_HOST` / `DEMANDES_DB_PORT` / `DEMANDES_DB_NAME` | `localhost` / `5432` / `demandes` | Base `demandes-db` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / aucun | Accès à la base |
| `SERVER_PORT` | `8082` | Port HTTP |
| `donstock.mock.mode` (profil `mock`) | `LOCAL` | `LOCAL`, `COMMANDE`, `PENURIE` ou `INDISPONIBLE` |

## Création d'une demande (S6-05)

`POST /api/v1/demandes` : (1) enregistre la demande `EN_ATTENTE` et valide, (2) appelle Don & Stock **sans transaction ouverte**, (3) enregistre le nouveau statut.

| Réponse de Don & Stock | Statut | HTTP |
|---|---|---|
| `RESERVEE_STOCK_LOCAL` | `SATISFAITE` | 201 |
| `APPROVISIONNEMENT_COMMANDE` | `APPROVISIONNEMENT_EN_COURS` | 202 |
| `STOCK_INSUFFISANT_PARTOUT` | `EN_PENURIE` (appel public : S6-07) | 202 |
| Panne technique | `EN_ATTENTE` | 202 |
| Données invalides | non enregistrée | 400 |

## Tests

```bash
./mvnw verify
```

Les tests utilisent H2 (mode PostgreSQL) avec la vraie migration Flyway : pas besoin de Docker ni de PostgreSQL.
