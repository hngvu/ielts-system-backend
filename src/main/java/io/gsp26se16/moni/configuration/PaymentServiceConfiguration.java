package io.gsp26se16.moni.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.service.CreditTransactionService;
import io.gsp26se16.moni.payment.service.impl.CreditTransactionServiceImpl;

@Configuration
public class PaymentServiceConfiguration {

    @Bean
    public CreditTransactionService creditTransactionService(
            CreditTransactionRepository creditTransactionRepository, UsersRepository usersRepository) {
        return new CreditTransactionServiceImpl(creditTransactionRepository, usersRepository);
    }
}
