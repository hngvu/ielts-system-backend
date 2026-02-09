package io.gsp26se16.moni.payment.dto.request;

public record PaymentInitRequest(
    Integer packageId,
    Integer amount
) {
}
