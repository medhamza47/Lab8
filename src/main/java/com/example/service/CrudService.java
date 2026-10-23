package com.example.service;

import com.example.dao.GenericDAO;
import java.util.List;
import java.util.Objects;

public class CrudService<T, ID> {
    private final GenericDAO<T, ID> dao;

    public CrudService(Class<T> entityClass) {
        this(new GenericDAO<>(entityClass));
    }

    public CrudService(GenericDAO<T, ID> dao) {
        this.dao = Objects.requireNonNull(dao);
    }

    public T getById(ID id) {
        return dao.find(Objects.requireNonNull(id, "id"));
    }

    public List<T> getAll() {
        return dao.findAll();
    }

    public T create(T entity) {
        return dao.create(requireEntity(entity));
    }

    public T update(T entity) {
        return dao.update(requireEntity(entity));
    }

    public void delete(T entity) {
        dao.delete(requireEntity(entity));
    }

    private T requireEntity(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("L'entite ne doit pas etre nulle");
        }
        return entity;
    }
}
