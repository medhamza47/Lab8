package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.dao.GenericDAO;
import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationSearchService;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.JpaUtil;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationApplicationTest {
    private static EntityManagerFactory entityManagerFactory;
    private static final LocalDateTime BASE = LocalDateTime.of(2030, 4, 15, 9, 0);

    @BeforeAll
    static void startDatabase() {
        System.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        System.setProperty("hibernate.connection.url", "jdbc:h2:mem:reservation;DB_CLOSE_DELAY=-1");
        System.setProperty("hibernate.connection.username", "sa");
        System.setProperty("hibernate.connection.password", "");
        System.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        System.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        entityManagerFactory = JpaUtil.getEntityManagerFactory();
    }

    @AfterAll
    static void stopDatabase() {
        JpaUtil.shutdown();
        System.clearProperty("hibernate.connection.driver_class");
        System.clearProperty("hibernate.connection.url");
        System.clearProperty("hibernate.connection.username");
        System.clearProperty("hibernate.connection.password");
        System.clearProperty("hibernate.dialect");
        System.clearProperty("hibernate.hbm2ddl.auto");
    }

    @BeforeEach
    void clearDatabase() {
        entityManagerFactory.getCache().evictAll();
        EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            manager.getTransaction().begin();
            manager.createQuery("delete from Reservation").executeUpdate();
            manager.createQuery("delete from Salle").executeUpdate();
            manager.createQuery("delete from Utilisateur").executeUpdate();
            manager.getTransaction().commit();
        } catch (RuntimeException exception) {
            if (manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw exception;
        } finally {
            manager.close();
        }
    }

    @Test
    void initializesSampleDataOnlyOnce() {
        DataInitializer initializer = new DataInitializer(entityManagerFactory);
        initializer.initializeIfEmpty();
        initializer.initializeIfEmpty();

        assertEquals(3, new GenericDAO<>(Utilisateur.class, entityManagerFactory).findAll().size());
        assertEquals(3, new GenericDAO<>(Salle.class, entityManagerFactory).findAll().size());
        assertEquals(3, new GenericDAO<>(Reservation.class, entityManagerFactory).findAll().size());
    }

    @Test
    void createsSearchesAndProtectsRoomAvailability() {
        GenericDAO<Utilisateur, Long> users = new GenericDAO<>(Utilisateur.class, entityManagerFactory);
        GenericDAO<Salle, Long> rooms = new GenericDAO<>(Salle.class, entityManagerFactory);
        Utilisateur user = users.create(new Utilisateur("Test User", "test@example.com"));
        Salle room = rooms.create(new Salle("Salle Test", 18));
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();
        rooms.find(room.getId());
        rooms.find(room.getId());
        assertTrue(sessionFactory.getStatistics().getSecondLevelCacheHitCount() >= 1);
        ReservationService service = new ReservationService(entityManagerFactory);

        Reservation reservation = service.createReservation(user.getId(), room.getId(),
                BASE.plusDays(2), BASE.plusDays(2).plusHours(2));
        assertNotNull(reservation.getId());
        assertEquals(1, service.findForRoomAfter("Salle Test", BASE).size());
        assertEquals(1, service.findForRoomBetween("Salle Test",
                BASE.plusDays(2).plusHours(1), BASE.plusDays(2).plusHours(3)).size());
        assertEquals(1, new ReservationSearchService(entityManagerFactory)
                .search("Salle Test", BASE).size());
        assertThrows(IllegalStateException.class, () -> service.createReservation(user.getId(), room.getId(),
                BASE.plusDays(2).plusHours(1), BASE.plusDays(2).plusHours(3)));

        Reservation updated = service.updatePeriod(reservation.getId(), reservation.getVersion(),
                BASE.plusDays(2).plusHours(3), BASE.plusDays(2).plusHours(4));
        assertTrue(updated.getVersion() > reservation.getVersion());
        assertThrows(OptimisticLockException.class, () -> service.updatePeriod(
                reservation.getId(), reservation.getVersion(), BASE.plusDays(3), BASE.plusDays(3).plusHours(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createReservation(user.getId(), room.getId(), BASE, BASE));
    }

    @Test
    void detectsOptimisticLockingAcrossPersistenceContexts() {
        GenericDAO<Utilisateur, Long> users = new GenericDAO<>(Utilisateur.class, entityManagerFactory);
        Utilisateur persisted = users.create(new Utilisateur("Version User", "version@example.com"));
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            Utilisateur firstCopy = first.find(Utilisateur.class, persisted.getId());
            Utilisateur secondCopy = second.find(Utilisateur.class, persisted.getId());
            first.getTransaction().begin();
            firstCopy.setNom("First update");
            first.getTransaction().commit();
            second.getTransaction().begin();
            secondCopy.setNom("Stale update");
            assertThrows(RuntimeException.class, () -> {
                try {
                    second.getTransaction().commit();
                } catch (RuntimeException exception) {
                    if (second.getTransaction().isActive()) {
                        second.getTransaction().rollback();
                    }
                    throw exception;
                }
            });
        } finally {
            if (first.isOpen()) {
                first.close();
            }
            if (second.isOpen()) {
                if (second.getTransaction().isActive()) {
                    second.getTransaction().rollback();
                }
                second.close();
            }
        }
    }

    @Test
    void appliesDatabaseMigrationOnceAndReportsHibernateStatistics() throws Exception {
        MigrationExecutor.fromEnvironment().executeMigration("db/migration/v2_0_add_description.sql");
        MigrationExecutor.fromEnvironment().executeMigration("db/migration/v2_0_add_description.sql");

        String url = "jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("create table salles (id bigint primary key, nom varchar(100) not null)");
            statement.execute("create table utilisateurs (id bigint primary key, email varchar(180) not null)");
        }

        MigrationExecutor executor = new MigrationExecutor(url, "sa", "");
        executor.executeMigration("db/migration/v2_0_add_description.sql");
        executor.executeMigration("db/migration/v2_0_add_description.sql");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select count(*) from schema_migrations where version = 'db/migration/v2_0_add_description.sql'")) {
            result.next();
            assertEquals(1, result.getInt(1));
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        PerformanceReport report = new PerformanceReport(sessionFactory);
        assertTrue(report.snapshot().getTransactions() >= 1);
        assertTrue(report.snapshot().getCachePuts() >= 0);
        report.printReport();
    }
}
