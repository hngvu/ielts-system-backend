package io.gsp26se16.moni.payment.controller;

import io.gsp26se16.moni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    // webhook for sepay callback

    // init payment record

    // get payment list by user, status, date range

    // get payment detail
}
