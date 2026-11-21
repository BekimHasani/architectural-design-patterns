package com.designpatterns.level03.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPayments extends JpaRepository<PaymentEntity, String> {
}
