package com.example.banqueservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.banqueservice.entities.Compte;

@Repository
public interface CompteRepository extends JpaRepository<Compte, Long> {
}