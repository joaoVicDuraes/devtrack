package com.devtrack.service;

import com.devtrack.model.Task;
import com.devtrack.repository.TaskRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A tiny in-memory stand-in for {@link TaskRepository}, used only by the service property tests.
 *
 * <p><strong>Why a fake instead of the real JPA repository or a mock?</strong> The property tests
 * verify DevTrack's own service logic (title trimming, defaulting, entity&harr;DTO mapping), not the
 * behaviour of MySQL or Hibernate. A hand-written fake keeps those tests fast and dependency-free:
 * no database, no Spring context. It is not a mock either &mdash; it stores real objects in a
 * {@link Map}, so a write followed by a read behaves like a genuine repository, which is exactly what
 * the round-trip property needs to exercise.
 *
 * <p><strong>Why extend {@link TaskRepository} directly?</strong> {@code TaskRepository} extends
 * Spring Data's {@code JpaRepository}, which declares many methods. This fake implements the handful
 * the {@link TaskServiceImpl} actually calls &mdash; {@code save}, {@code findById},
 * {@code existsById}, {@code deleteById}, and the derived {@code findAllByOrderByIdAsc()} &mdash; and
 * leaves the rest as {@code default} methods (inherited from the Spring Data interfaces) or throws
 * {@link UnsupportedOperationException} for the abstract ones we do not need. If a future test uses a
 * method that throws, that is a clear signal to implement it rather than a silent wrong answer.
 *
 * <p><strong>Id assignment mirrors MySQL.</strong> Real ids come from a MySQL {@code AUTO_INCREMENT}
 * column. Here an {@link AtomicLong} sequence assigns the next id on first save (when the entity's id
 * is still {@code null}), reproducing the "database owns the id" behaviour the service relies on.
 */
class InMemoryTaskRepository implements TaskRepository {

    /** Stored tasks keyed by id. LinkedHashMap keeps insertion order for readable debugging. */
    private final Map<Long, Task> store = new LinkedHashMap<>();

    /** Emulates MySQL AUTO_INCREMENT: the next id to hand out, starting at 1. */
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public <S extends Task> S save(S entity) {
        if (entity.getId() == null) {
            // New task: assign the next sequence id, just as the database would on insert.
            entity.setId(sequence.getAndIncrement());
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<Task> findAllByOrderByIdAsc() {
        List<Task> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(Task::getId));
        return all;
    }

    // ---------------------------------------------------------------------
    // Remaining JpaRepository methods: not used by the service under test.
    // They throw so any accidental use is caught loudly rather than passing silently.
    // ---------------------------------------------------------------------

    @Override
    public <S extends Task> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public List<Task> findAll() {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public List<Task> findAll(org.springframework.data.domain.Sort sort) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public org.springframework.data.domain.Page<Task> findAll(
            org.springframework.data.domain.Pageable pageable) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public List<Task> findAllById(Iterable<Long> ids) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void delete(Task entity) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public void deleteAll(Iterable<? extends Task> entities) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    @Override
    public void flush() {
        // No-op: this in-memory store writes synchronously, so there is nothing to flush.
    }

    @Override
    public <S extends Task> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends Task> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public void deleteAllInBatch(Iterable<Task> entities) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public void deleteAllInBatch() {
        store.clear();
    }

    @Override
    public Task getOne(Long id) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public Task getById(Long id) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public Task getReferenceById(Long id) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task> Optional<S> findOne(
            org.springframework.data.domain.Example<S> example) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task> List<S> findAll(
            org.springframework.data.domain.Example<S> example) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task> List<S> findAll(
            org.springframework.data.domain.Example<S> example,
            org.springframework.data.domain.Sort sort) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task> org.springframework.data.domain.Page<S> findAll(
            org.springframework.data.domain.Example<S> example,
            org.springframework.data.domain.Pageable pageable) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task> long count(org.springframework.data.domain.Example<S> example) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task> boolean exists(org.springframework.data.domain.Example<S> example) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }

    @Override
    public <S extends Task, R> R findBy(
            org.springframework.data.domain.Example<S> example,
            java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw new UnsupportedOperationException("not needed for these property tests");
    }
}
