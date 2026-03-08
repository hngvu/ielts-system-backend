package io.gsp26se16.moni.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.CreditTransaction;

@Repository
public interface CreditTransactionRepository
        extends JpaRepository<CreditTransaction, Integer>, JpaSpecificationExecutor<CreditTransaction> {}