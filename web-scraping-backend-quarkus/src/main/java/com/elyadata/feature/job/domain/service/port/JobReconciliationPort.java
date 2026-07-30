package com.elyadata.feature.job.domain.service.port;

import com.elyadata.feature.job.domain.model.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture minimale de l'etat des jobs pour la reconciliation Postgres -> Redis.
 *
 * <p>Volontairement separe de {@code JobPort} : la reconciliation n'a pas besoin
 * d'un agregat {@code Job} hydrate (schema, proxy config, etc.), seulement de
 * l'identifiant, du statut et de la date de derniere modification. Charger
 * l'agregat complet pour quelques centaines de candidats a chaque scan serait
 * du gaspillage pur.
 */
public interface JobReconciliationPort {

    /**
     * Jobs non supprimes dont le statut est dans {@code statuses} et dont
     * {@code updatedAt} est anterieur a {@code updatedBefore}, tries du plus
     * ancien au plus recent.
     *
     * @param limit borne dure sur le nombre de resultats (protege le scan)
     */
    List<StaleJob> findStale(Set<JobStatus> statuses, Instant updatedBefore, int limit);

    record StaleJob(UUID id, JobStatus status, Instant updatedAt) {
    }
}
