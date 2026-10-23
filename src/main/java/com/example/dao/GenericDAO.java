package com.example.dao;

import com.example.util.JpaUtil;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

public class GenericDAO<T, ID> {
    private final Class<T> entityClass;
    private final EntityManagerFactory entityManagerFactory;

    public GenericDAO(Class<T> entityClass) {
        this(entityClass, JpaUtil.getEntityManagerFactory());
    }

    public GenericDAO(Class<T> entityClass, EntityManagerFactory entityManagerFactory) {
        this.entityClass = Objects.requireNonNull(entityClass);
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory);
    }

    public T find(ID id) {
        Objects.requireNonNull(id);
        return read(manager -> manager.find(entityClass, id));
    }

    public List<T> findAll() {
        return read(manager -> manager.createQuery(
                "select entity from " + entityClass.getSimpleName() + " entity", entityClass)
                .getResultList());
    }

    public T create(T entity) {
        Objects.requireNonNull(entity);
        return write(manager -> {
            manager.persist(entity);
            return entity;
        });
    }

    public T update(T entity) {
        Objects.requireNonNull(entity);
        return write(manager -> manager.merge(entity));
    }

    public void delete(T entity) {
        Objects.requireNonNull(entity);
        write(manager -> {
            manager.remove(manager.contains(entity) ? entity : manager.merge(entity));
            return null;
        });
    }

    protected final <R> R read(Function<EntityManager, R> operation) {
        EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            return operation.apply(manager);
        } finally {
            manager.close();
        }
    }

    protected final <R> R write(Function<EntityManager, R> operation) {
        EntityManager manager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = manager.getTransaction();
        try {
            transaction.begin();
            R result = operation.apply(manager);
            transaction.commit();
            return result;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw exception;
        } finally {
            manager.close();
        }
    }
}
