package com.bank.accounts.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

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
}
