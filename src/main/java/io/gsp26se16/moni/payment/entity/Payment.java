package io.gsp26se16.moni.payment.entity;

import io.gsp26se16.moni.authentication.entity.Users;
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
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String txnCode;
    int amount;

    String gatewayTxnId;
    String webhookResponse;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "package_id")
    PackagePricing packagePricing;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;
}
