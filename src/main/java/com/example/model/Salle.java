package com.example.model;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.OneToMany;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "salles")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Salle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nom;

    @Column(nullable = false)
    private int capacite;

    @Column(length = 255)
    private String description;

    @Version
    private int version;

    @OneToMany(mappedBy = "salle")
    private List<Reservation> reservations = new ArrayList<>();

    protected Salle() {
    }

    public Salle(String nom, int capacite) {
        setNom(nom);
        setCapacite(capacite);
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public int getCapacite() { return capacite; }
    public String getDescription() { return description; }
    public int getVersion() { return version; }
    public List<Reservation> getReservations() { return Collections.unmodifiableList(reservations); }

    public void setNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom de salle est obligatoire");
        }
        this.nom = nom.trim();
    }

    public void setCapacite(int capacite) {
        if (capacite < 1) {
            throw new IllegalArgumentException("La capacite doit etre positive");
        }
        this.capacite = capacite;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Salle that) || id == null) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : Objects.hash(id);
    }
}
