package com.example.util;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

public class DataInitializer {
    private static final Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());
    private final EntityManagerFactory entityManagerFactory;

    public DataInitializer() {
        this(JpaUtil.getEntityManagerFactory());
    }

    public DataInitializer(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void initializeIfEmpty() {
        EntityManager manager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = manager.getTransaction();
        try {
            transaction.begin();
            Long existingUsers = manager.createQuery(
                    "select count(u) from Utilisateur u", Long.class).getSingleResult();
            if (existingUsers > 0) {
                transaction.commit();
                LOGGER.info("Sample data already exists; initialization skipped");
                return;
            }

            Utilisateur alice = new Utilisateur("Alice Dupont", "alice.dupont@example.com");
            Utilisateur karim = new Utilisateur("Karim Benali", "karim.benali@example.com");
            Utilisateur sara = new Utilisateur("Sara Amrani", "sara.amrani@example.com");
            List.of(alice, karim, sara).forEach(manager::persist);

            Salle roomA = new Salle("Salle A", 20);
            Salle roomB = new Salle("Salle B", 12);
            Salle conference = new Salle("Salle de conférence", 40);
            List.of(roomA, roomB, conference).forEach(manager::persist);
            manager.flush();

            LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
            manager.persist(new Reservation(alice, roomA, tomorrow.withHour(9), tomorrow.withHour(11)));
            manager.persist(new Reservation(karim, roomB, tomorrow.withHour(13), tomorrow.withHour(15)));
            manager.persist(new Reservation(sara, conference, tomorrow.withHour(10), tomorrow.withHour(12)));
            transaction.commit();
            LOGGER.info("Sample users, rooms and reservations initialized");
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.severe("Sample data initialization failed: " + exception.getMessage());
            throw exception;
        } finally {
            manager.close();
        }
    }
}
