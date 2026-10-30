package com.bank.accounts.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every read fetches the account in the same query, to avoid N+1 selects. */
interface MovementJpaRepository extends JpaRepository<MovementEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "account")
    Optional<MovementEntity> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "account")
    Page<MovementEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "account")
    Page<MovementEntity> findByAccountAccountNumber(String accountNumber, Pageable pageable);

    @EntityGraph(attributePaths = "account")
    Optional<MovementEntity> findFirstByAccountAccountNumberOrderByMovementDateDescIdDesc(String accountNumber);

    @Query("""
            select m from MovementEntity m join fetch m.account a
            where a.clientId = :clientId and m.movementDate >= :from and m.movementDate < :to
            order by m.movementDate, m.id
            """)
    List<MovementEntity> findByClientIdBetween(@Param("clientId") UUID clientId, @Param("from") Instant from,
                                               @Param("to") Instant to);
}
