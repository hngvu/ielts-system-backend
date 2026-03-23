package io.gsp26se16.moni.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String txnCode;
    int amount;

    String gatewayTxnId;

    @Column(columnDefinition = "TEXT")
    String webhookResponse;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    PaymentStatus status;

    @ManyToOne
    @JoinColumn(name = "package_id")
    PackagePricing packagePricing;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;
}
