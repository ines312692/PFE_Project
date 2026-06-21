package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.infrastructure.entity.JobEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobRepositoryTest {

    @Inject JobRepository repository;
    @Inject EntityManager  em;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();


    private JobEntity persistNew(String name, UUID createdBy, boolean deleted) {
        JobEntity e = new JobEntity();
        e.setName(name);
        e.setTargetUrl("https://example.com");
        e.setStatus(JobStatus.CREATED);
        e.setPriority(JobPriority.NORMAL);
        e.setTriggerType(JobTriggerType.MANUAL);
        e.setJobType(JobType.SCRAPER);
        e.setExtractionMode(ExtractionMode.CSS_SELECTOR);
        e.setCreatedBy(createdBy);
        e.setDeleted(deleted);
        if (deleted) e.setDeletedAt(Instant.now());
        em.persist(e);
        em.flush();
        return e;
    }

    private JobEntity persistNew(String name, UUID createdBy) {
        return persistNew(name, createdBy, false);
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test @TestTransaction @Order(1)
    @DisplayName("persist then findById should return the saved entity")
    void persistAndFindById_newEntity_returnsPersistedEntity() {
        JobEntity saved = persistNew("My Job", USER_A);

        Optional<JobEntity> found = repository.findByIdOptional(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("My Job");
        assertThat(found.get().getCreatedBy()).isEqualTo(USER_A);
        assertThat(found.get().isDeleted()).isFalse();
    }

    @Test @TestTransaction @Order(2)
    @DisplayName("findById should return empty for unknown UUID")
    void findById_unknownId_returnsEmpty() {
        assertThat(repository.findByIdOptional(UUID.randomUUID())).isEmpty();
    }

    @Test @TestTransaction @Order(3)
    @DisplayName("listAll should not return soft-deleted jobs")
    void listAll_withDeletedJobs_excludesDeleted() {
        persistNew("Active Job",  USER_A, false);
        persistNew("Deleted Job", USER_A, true);

        assertThat(repository.listAll())
                .extracting(JobEntity::getName)
                .doesNotContain("Deleted Job");
    }

    @Test @TestTransaction @Order(4)
    @DisplayName("findAllByCreatedBy should return only jobs of the given user")
    void findAllByCreatedBy_twoUsers_returnsOnlyRequestedUserJobs() {
        persistNew("User A Job 1", USER_A);
        persistNew("User A Job 2", USER_A);
        persistNew("User B Job",   USER_B);

        List<JobEntity> resultA = repository.findAllByCreatedBy(USER_A);
        List<JobEntity> resultB = repository.findAllByCreatedBy(USER_B);

        assertThat(resultA).allMatch(e -> USER_A.equals(e.getCreatedBy()));
        assertThat(resultB).allMatch(e -> USER_B.equals(e.getCreatedBy()));
        assertThat(resultA).hasSizeGreaterThanOrEqualTo(2);
        assertThat(resultB).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test @TestTransaction @Order(5)
    @DisplayName("findAllByCreatedBy should not return soft-deleted jobs")
    void findAllByCreatedBy_withDeletedJob_excludesDeleted() {
        persistNew("Active",  USER_A, false);
        persistNew("Deleted", USER_A, true);

        assertThat(repository.findAllByCreatedBy(USER_A))
                .extracting(JobEntity::getName)
                .doesNotContain("Deleted");
    }

    @Test @TestTransaction @Order(6)
    @DisplayName("findByIdAndCreatedBy should return empty when job belongs to another user")
    void findByIdAndCreatedBy_wrongUser_returnsEmpty() {
        JobEntity saved = persistNew("User A Only", USER_A);

        assertThat(repository.findByIdAndCreatedBy(saved.getId(), USER_B)).isEmpty();
    }

    @Test @TestTransaction @Order(7)
    @DisplayName("findByIdAndCreatedBy should return job when owner matches")
    void findByIdAndCreatedBy_correctOwner_returnsEntity() {
        JobEntity saved = persistNew("My Private Job", USER_A);

        Optional<JobEntity> result = repository.findByIdAndCreatedBy(saved.getId(), USER_A);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    @Test @TestTransaction @Order(8)
    @DisplayName("deleteById should soft-delete: findByIdOptional returns empty after delete")
    void deleteById_existingEntity_softDeletesWithTimestamp() {
        JobEntity saved = persistNew("To Delete", USER_A);

        repository.deleteById(saved.getId());
        em.flush();

        assertThat(repository.findByIdOptional(saved.getId())).isEmpty();
    }

    @Test @TestTransaction @Order(9)
    @DisplayName("deleteById should be safe when entity does not exist")
    void deleteById_nonExistentId_doesNotThrow() {
        assertThatNoException()
                .isThrownBy(() -> repository.deleteById(UUID.randomUUID()));
    }
}