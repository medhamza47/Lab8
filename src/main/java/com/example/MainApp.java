package com.example;

import com.example.service.ReservationSearchService;
import com.example.util.DataInitializer;
import com.example.util.JpaUtil;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import org.hibernate.SessionFactory;

public final class MainApp {
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    private MainApp() {
    }

    public static void main(String[] args) {
        try {
            EntityManager manager = JpaUtil.getEntityManagerFactory().createEntityManager();
            manager.close();
            MigrationExecutor.fromEnvironment()
                    .executeMigration("db/migration/v2_0_add_description.sql");
            new DataInitializer().initializeIfEmpty();

            var upcoming = new ReservationSearchService().search(null, LocalDateTime.now());
            upcoming.forEach(reservation -> LOGGER.info(
                    reservation.getSalle().getNom() + " reserved by " + reservation.getUtilisateur().getEmail()));

            SessionFactory sessionFactory = JpaUtil.getEntityManagerFactory().unwrap(SessionFactory.class);
            new PerformanceReport(sessionFactory).printReport();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Reservation application failed", exception);
            throw exception;
        } finally {
            JpaUtil.shutdown();
        }
    }
}
