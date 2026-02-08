package io.gsp26se16.moni.payment.entity;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreditTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    int delta;
    int balanceBefore;
    int balanceAfter;
    @Enumerated(EnumType.STRING)
    PaymentType paymentType;
    LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;

    @ManyToOne
    @JoinColumn(name = "service_id")
    ServicePricing servicePricing;

    @OneToOne
    @JoinColumn(name = "payment_id")
    Payment payment;
}
