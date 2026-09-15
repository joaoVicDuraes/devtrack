package com.devtrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.devtrack.model.Task;

/**
 * Persistence gateway for {@link Task} records (Requirement 8.6).
 *
 * <p>This is the "Repository" in the Controller -&gt; Service -&gt; Repository layering. It is a
 * plain Java <em>interface</em>: we never write an implementation for it. Spring Data JPA generates
 * a proxy implementation at runtime, so all the standard CRUD operations
 * ({@code save}, {@code findById}, {@code findAll}, {@code deleteById}, ...) come for free.
 *
 * <p>By extending {@code JpaRepository<Task, Long>} we declare two type parameters:
 * <ul>
 *   <li>{@code Task} — the entity type this repository manages.</li>
 *   <li>{@code Long} — the type of that entity's primary key ({@code Task#getId()}).</li>
 * </ul>
 *
 * <p>{@code @Repository} is optional here (Spring already detects repositories that extend the
 * Spring Data interfaces), but stating it makes the layer explicit and documents intent.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Returns every stored task ordered by ascending id (Requirement 2.1).
     *
     * <p>This is a Spring Data <em>derived query method</em>: we do not write any SQL/JPQL. Spring
     * parses the method name and builds the query from it. The name breaks down as:
     * {@code findAll} (return all matching rows) + {@code ByOrderByIdAsc} (order the result by the
     * {@code id} property, ascending). Because there is no filtering criteria, it returns all rows
     * sorted by {@code id}.
     */
    List<Task> findAllByOrderByIdAsc();
}
