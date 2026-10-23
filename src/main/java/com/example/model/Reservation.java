package com.example.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    @Version
    private int version;

    protected Reservation() {
    }

    public Reservation(Utilisateur utilisateur, Salle salle, LocalDateTime dateDebut, LocalDateTime dateFin) {
        this.utilisateur = requireUtilisateur(utilisateur);
        this.salle = requireSalle(salle);
        setPeriode(dateDebut, dateFin);
    }

    public Long getId() { return id; }
    public Salle getSalle() { return salle; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public int getVersion() { return version; }

    public void setPeriode(LocalDateTime dateDebut, LocalDateTime dateFin) {
        if (dateDebut == null || dateFin == null || !dateFin.isAfter(dateDebut)) {
            throw new IllegalArgumentException("La fin de reservation doit etre apres le debut");
        }
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public void setSalle(Salle salle) {
        this.salle = requireSalle(salle);
    }

    private static Salle requireSalle(Salle salle) {
        if (salle == null) {
            throw new IllegalArgumentException("Une salle est obligatoire");
        }
        return salle;
    }

    private static Utilisateur requireUtilisateur(Utilisateur utilisateur) {
        if (utilisateur == null) {
            throw new IllegalArgumentException("Un utilisateur est obligatoire");
        }
        return utilisateur;
    }
}
