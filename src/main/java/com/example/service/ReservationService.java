package com.example.service;

import com.example.dao.GenericDAO;
import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.util.JpaUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

public class ReservationService extends CrudService<Reservation, Long> {
    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());
    private final EntityManagerFactory entityManagerFactory;

    public ReservationService() {
        this(JpaUtil.getEntityManagerFactory());
    }

    public ReservationService(EntityManagerFactory entityManagerFactory) {
        super(new GenericDAO<>(Reservation.class, entityManagerFactory));
        this.entityManagerFactory = entityManagerFactory;
    }

    public Reservation createReservation(Long utilisateurId, Long salleId,
                                         LocalDateTime debut, LocalDateTime fin) {
        validatePeriod(debut, fin);
        EntityManager manager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = manager.getTransaction();
        try {
            transaction.begin();
            Salle salle = manager.find(Salle.class, Objects.requireNonNull(salleId),
                    LockModeType.PESSIMISTIC_WRITE);
            Utilisateur utilisateur = manager.find(Utilisateur.class, Objects.requireNonNull(utilisateurId));
            if (salle == null || utilisateur == null) {
                throw new IllegalArgumentException("La salle et l'utilisateur doivent exister");
            }
            ensureRoomAvailable(manager, salleId, debut, fin, null);
            Reservation reservation = new Reservation(utilisateur, salle, debut, fin);
            manager.persist(reservation);
            transaction.commit();
            LOGGER.info(() -> "Reservation " + reservation.getId() + " created for room " + salleId);
            return reservation;
        } catch (RuntimeException exception) {
            rollback(transaction);
            LOGGER.log(Level.WARNING, "Reservation creation failed for room " + salleId, exception);
            throw exception;
        } finally {
            manager.close();
        }
    }

    public Reservation updatePeriod(Long reservationId, int expectedVersion,
                                    LocalDateTime debut, LocalDateTime fin) {
        validatePeriod(debut, fin);
        EntityManager manager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = manager.getTransaction();
        try {
            transaction.begin();
            Reservation reservation = manager.find(Reservation.class,
                    Objects.requireNonNull(reservationId), LockModeType.PESSIMISTIC_WRITE);
            if (reservation == null) {
                throw new IllegalArgumentException("Reservation introuvable : " + reservationId);
            }
            if (reservation.getVersion() != expectedVersion) {
                throw new OptimisticLockException("La reservation a ete modifiee par un autre utilisateur");
            }
            Long roomId = reservation.getSalle().getId();
            manager.find(Salle.class, roomId, LockModeType.PESSIMISTIC_WRITE);
            ensureRoomAvailable(manager, roomId, debut, fin, reservationId);
            reservation.setPeriode(debut, fin);
            manager.flush();
            transaction.commit();
            LOGGER.info(() -> "Reservation " + reservationId + " updated");
            return reservation;
        } catch (RuntimeException exception) {
            rollback(transaction);
            LOGGER.log(Level.WARNING, "Reservation update failed for id " + reservationId, exception);
            throw exception;
        } finally {
            manager.close();
        }
    }

    public List<Reservation> findForRoomAfter(String roomName, LocalDateTime date) {
        if (roomName == null || roomName.isBlank() || date == null) {
            throw new IllegalArgumentException("Un nom de salle et une date sont requis");
        }
        EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            return manager.createQuery(
                    "select r from Reservation r join fetch r.salle join fetch r.utilisateur " +
                            "where r.salle.nom = :roomName and r.dateDebut >= :date order by r.dateDebut",
                    Reservation.class)
                    .setParameter("roomName", roomName.trim())
                    .setParameter("date", date)
                    .getResultList();
        } finally {
            manager.close();
        }
    }

    public List<Reservation> findForRoomBetween(String roomName, LocalDateTime debut, LocalDateTime fin) {
        validatePeriod(debut, fin);
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("Un nom de salle est requis");
        }
        EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            TypedQuery<Reservation> query = manager.createQuery(
                    "select r from Reservation r join fetch r.salle join fetch r.utilisateur " +
                            "where r.salle.nom = :roomName and r.dateDebut < :fin and r.dateFin > :debut " +
                            "order by r.dateDebut", Reservation.class);
            return query.setParameter("roomName", roomName.trim())
                    .setParameter("debut", debut).setParameter("fin", fin).getResultList();
        } finally {
            manager.close();
        }
    }

    private static void ensureRoomAvailable(EntityManager manager, Long roomId,
                                            LocalDateTime debut, LocalDateTime fin, Long excludedId) {
        String jpql = "select count(r) from Reservation r where r.salle.id = :roomId " +
                "and r.dateDebut < :fin and r.dateFin > :debut" +
                (excludedId == null ? "" : " and r.id <> :excludedId");
        var query = manager.createQuery(jpql, Long.class)
                .setParameter("roomId", roomId)
                .setParameter("debut", debut)
                .setParameter("fin", fin);
        if (excludedId != null) {
            query.setParameter("excludedId", excludedId);
        }
        if (query.getSingleResult() > 0) {
            throw new IllegalStateException("La salle est deja reservee sur ce creneau");
        }
    }

    private static void validatePeriod(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            throw new IllegalArgumentException("La fin doit etre apres le debut");
        }
    }

    private static void rollback(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
