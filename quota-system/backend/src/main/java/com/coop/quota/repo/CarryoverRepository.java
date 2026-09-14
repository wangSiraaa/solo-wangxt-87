package com.coop.quota.repo;

import com.coop.quota.domain.Carryover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarryoverRepository extends JpaRepository<Carryover, Long> {
    List<Carryover> findAllByOrderByCreatedAtDesc();
}
