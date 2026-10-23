package com.example.util;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public final class JpaUtil {
    private static final Logger LOGGER = Logger.getLogger(JpaUtil.class.getName());
    private static volatile EntityManagerFactory entityManagerFactory;

    private JpaUtil() {
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        EntityManagerFactory factory = entityManagerFactory;
        if (factory == null || !factory.isOpen()) {
            synchronized (JpaUtil.class) {
                factory = entityManagerFactory;
                if (factory == null || !factory.isOpen()) {
                    entityManagerFactory = factory = buildFactory();
                }
            }
        }
        return factory;
    }

    public static synchronized void shutdown() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
            LOGGER.info("EntityManagerFactory closed");
        }
        entityManagerFactory = null;
    }

    private static EntityManagerFactory buildFactory() {
        Properties overrides = new Properties();
        setIfPresent(overrides, "hibernate.connection.url", System.getenv("DB_URL"));
        setIfPresent(overrides, "hibernate.connection.username", System.getenv("DB_USER"));
        setIfPresent(overrides, "hibernate.connection.password", System.getenv("DB_PASSWORD"));
        setIfPresent(overrides, "hibernate.connection.url", System.getProperty("hibernate.connection.url"));
        setIfPresent(overrides, "hibernate.connection.username", System.getProperty("hibernate.connection.username"));
        setIfPresent(overrides, "hibernate.connection.password", System.getProperty("hibernate.connection.password"));
        setIfPresent(overrides, "hibernate.hbm2ddl.auto", System.getProperty("hibernate.hbm2ddl.auto"));
        setIfPresent(overrides, "hibernate.show_sql", System.getProperty("hibernate.show_sql"));
        setIfPresent(overrides, "hibernate.connection.driver_class",
                System.getProperty("hibernate.connection.driver_class"));
        setIfPresent(overrides, "hibernate.dialect", System.getProperty("hibernate.dialect"));
        setIfPresent(overrides, "hibernate.cache.use_second_level_cache",
                System.getProperty("hibernate.cache.use_second_level_cache"));

        try {
            return Persistence.createEntityManagerFactory("ReservationPU", overrides);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Unable to create the ReservationPU EntityManagerFactory", exception);
            throw exception;
        }
    }

    private static void setIfPresent(Properties properties, String name, String value) {
        if (value != null && !value.isBlank()) {
            properties.setProperty(name, value);
        }
    }
}
