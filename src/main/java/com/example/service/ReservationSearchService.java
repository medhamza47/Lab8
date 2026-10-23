package com.example.service;

import com.example.model.Reservation;
import com.example.util.JpaUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public class ReservationSearchService {
    private final EntityManagerFactory entityManagerFactory;

    public ReservationSearchService() {
        this(JpaUtil.getEntityManagerFactory());
    }

    public ReservationSearchService(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public List<Reservation> search(String roomName, LocalDateTime after) {
        EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            CriteriaBuilder builder = manager.getCriteriaBuilder();
            CriteriaQuery<Reservation> criteria = builder.createQuery(Reservation.class);
            Root<Reservation> reservation = criteria.from(Reservation.class);
            reservation.fetch("salle");
            reservation.fetch("utilisateur");
            Join<Reservation, ?> room = reservation.join("salle");
            List<Predicate> predicates = new ArrayList<>();
            if (roomName != null && !roomName.isBlank()) {
                predicates.add(builder.equal(room.get("nom"), roomName.trim()));
            }
            if (after != null) {
                predicates.add(builder.greaterThanOrEqualTo(reservation.get("dateDebut"), after));
            }
            criteria.select(reservation).where(predicates.toArray(Predicate[]::new))
                    .orderBy(builder.asc(reservation.get("dateDebut")));
            return manager.createQuery(criteria).getResultList();
        } finally {
            manager.close();
        }
    }
}
