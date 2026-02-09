package io.gsp26se16.moni.payment.service;

import io.gsp26se16.moni.payment.dto.request.PaymentInitRequest;
import io.gsp26se16.moni.payment.dto.request.SePayWebhookRequest;
import io.gsp26se16.moni.payment.dto.response.PaymentInitResponse;
import io.gsp26se16.moni.payment.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentInitResponse initPayment(PaymentInitRequest paymentInitRequest);
    PaymentResponse handleSePayCallback(SePayWebhookRequest sePayWebhookRequest);
}
