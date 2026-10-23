# Réservation de salles avec JPA et Hibernate

Application de réservation de salles réalisée sans Spring, conformément aux
consignes du TP. Le modèle couvre les utilisateurs, salles et réservations,
avec validation, contrôle des créneaux, verrouillage optimiste, cache de second
niveau, données d'exemple, recherches JPQL/Criteria, migration SQL et statistiques
de performance.

## Prérequis

- JDK 17 ou supérieur
- Maven 3.9+
- MySQL 8 pour l'exécution locale complète

## Compiler et tester

```shell
mvn clean verify
```

Les tests utilisent H2 en mémoire. Ils vérifient le CRUD, l'initialisation
idempotente des exemples, les recherches, les conflits de créneaux, le
verrouillage optimiste, les migrations et les métriques Hibernate.

## Base de données

`src/main/resources/META-INF/persistence.xml` contient les paramètres MySQL
par défaut (base `reservation`, utilisateur `root`, mot de passe vide). Pour
personnaliser la connexion, définissez `DB_URL`, `DB_USER` et `DB_PASSWORD`.
Les propriétés `hibernate.connection.url`, `hibernate.connection.username`,
`hibernate.connection.password`, `hibernate.dialect` et
`hibernate.hbm2ddl.auto` peuvent aussi être fournies comme options `-D`.

## Exécution

Après avoir configuré MySQL, démarrez l'application en ligne de commande :

```shell
mvn exec:java -Dexec.mainClass=com.example.MainApp
```

Le démarrage vérifie la connexion, exécute la migration versionnée, initialise
un petit jeu de démonstration si la base est vide, affiche les réservations à
venir et produit le rapport des statistiques Hibernate.

La migration SQL est dans `src/main/resources/db/migration/`. Elle est suivie
dans `schema_migrations`, exécutée dans une transaction et peut être rappelée
sans réappliquer les changements déjà présents.
