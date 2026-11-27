# Rapport technique — S2-10 : tests avec backend simulé (WireMock)

## 1. Source et périmètre

Guide officiel : `docs/review/Engineer-guides-step-by-step-from-the-first-day-to-the-defense-v2-CNTS-study.md`
(branche `origin/develop`), lignes 310-315.

> Utiliser WireMock comme backend et vérifier : le transfert de chaque préfixe,
> le preflight CORS, 401 sans token, 403 avec le mauvais rôle, 200 avec le bon
> rôle, 503 quand le backend est arrêté, 504 quand il est lent, l'en-tête
> `X-Request-Id`, et le passage d'une réponse en flux (le premier événement
> arrive avant la fin de la réponse). Puis tester avec les vrais services en
> Compose (un curl par route, avec et sans token) et mettre à jour le tableau
> des routes du README.

Ce rapport couvre la **partie WireMock**, réellement exécutable ici. La partie
« vrais services en Compose » est documentée comme non vérifiable dans la
section 5.

## 2. Fichiers de test WireMock

| Fichier | Tests | Rôle |
| --- | --- | --- |
| `src/test/java/.../GatewayWireMockRoutingIntegrationTest.java` | 15 | 10 routes sur **deux backends distincts**, CORS, 504, `X-Request-Id`, SSE |
| `src/test/java/.../GatewayWireMockSecurityIntegrationTest.java` | 3 | 401 / 403 / 200 avec WireMock |
| `src/test/java/.../GatewayWireMockBackendDownIntegrationTest.java` | 1 | 503 backend arrêté, puis reprise après redémarrage sur le même port |
| `src/test/java/.../WireMockBackend.java` | — | utilitaire : ports dynamiques, démarrage/arrêt |
| `pom.xml` | — | `org.wiremock:wiremock-standalone:3.13.2`, scope `test` |

## 3. Correspondance exigence → preuve

| Exigence du guide | Test / assertion |
| --- | --- |
| Transfert de chaque préfixe | 10 cas paramétrés, chemin transmis inchangé ; vérification du backend attendu **et** zéro requête sur l'autre backend |
| Preflight CORS | `OPTIONS` → 200, en-têtes CORS, aucun appel backend |
| 401 sans token | 401 JSON, backend non appelé |
| 403 mauvais rôle | jeton `DONOR` sur `POST /api/v1/dons` → 403 JSON |
| 200 bon rôle | jeton `STAFF_CENTRE` → 200 avec corps WireMock |
| 503 backend arrêté | arrêt réel du serveur, 503 JSON, puis redémarrage et retour à 200 |
| 504 backend lent | délai WireMock 2500 ms > timeout de test 800 ms |
| `X-Request-Id` | valeur fournie et valeur générée, vérifiées dans la réponse **et** dans la requête reçue par WireMock |
| Flux SSE | ≥ 2 événements décodés, premier = `un`, durée > 800 ms (timeout global court-circuité par `response-timeout: -1`), `premier < fin − 500 ms` |

Le test SSE mesure un timestamp par événement via `doOnNext`. L'écart minimal
de 500 ms avec la fin écarte un faux succès dû à une réponse entièrement
bufferisée puis livrée d'un coup (dans ce cas `premier ≈ fin`).

## 4. Résultats réellement exécutés

Commande :

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B -ntp test -Dtest="GatewayWireMock*"
```

| Suite | Tests | Échecs | Erreurs | Ignorés |
| --- | --- | --- | --- | --- |
| `GatewayWireMockRoutingIntegrationTest` | 15 | 0 | 0 | 0 |
| `GatewayWireMockSecurityIntegrationTest` | 3 | 0 | 0 | 0 |
| `GatewayWireMockBackendDownIntegrationTest` | 1 | 0 | 0 | 0 |
| **Total WireMock** | **19** | **0** | **0** | **0** |

`BUILD SUCCESS`, code de sortie `0`.

Suite complète (après le correctif `RequestIdFilter` de S2-11) :

```text
mvn -B -ntp clean verify
Tests run: 182, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — MVN_EXIT=0 — Total time: 02:09 min
```

Répartition : 159 tests S2-02 à S2-09 + 19 tests WireMock S2-10 + 4 tests du
correctif `RequestIdFilter`. Aucun test existant n'a été supprimé, renommé ou
affaibli.

## 5. Tableau des routes et limites

`gateway/README.md` contient un tableau de **10 routes** strictement dérivé de
`src/main/resources/application.yml` (vérification automatique : 10 identifiants
YAML = 10 lignes du README). Il précise les `order: -1`, la route SSE, l'absence
de réécriture de chemin, les variables `DONNEURS_URL` / `DEMANDES_URL` et les
informations restant à confirmer avec les vrais microservices.

**Non vérifié :**

- `docker-compose.yml` et `.gitlab-ci.yml` font 2 octets (fichiers vides) : aucun
  `curl` contre les vrais services n'a pu être exécuté, et la condition « tests
  verts en CI » ne peut pas être affirmée.
- La conformité du tableau au comportement réel des microservices reste donc à
  valider en phase Compose/CI.
