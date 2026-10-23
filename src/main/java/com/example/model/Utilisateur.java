package com.example.model;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "utilisateurs")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Utilisateur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false, length = 120)
    private String nom;

    @Version
    private int version;

    @OneToMany(mappedBy = "utilisateur")
    private List<Reservation> reservations = new ArrayList<>();

    protected Utilisateur() {
    }

    public Utilisateur(String nom, String email) {
        setNom(nom);
        setEmail(email);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNom() { return nom; }
    public int getVersion() { return version; }
    public List<Reservation> getReservations() { return Collections.unmodifiableList(reservations); }

    public void setNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom est obligatoire");
        }
        this.nom = nom.trim();
    }

    public void setEmail(String email) {
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Une adresse e-mail valide est obligatoire");
        }
        this.email = email.trim().toLowerCase();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Utilisateur that) || id == null) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : Objects.hash(id);
    }
}
