package com.ecommerce.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.project.model.Payment;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgPaymentId(String pgPaymentId);
    boolean existsByPgPaymentId(String pgPaymentId);
}
