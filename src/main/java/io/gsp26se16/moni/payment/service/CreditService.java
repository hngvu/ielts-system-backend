package io.gsp26se16.moni.payment.service;

public interface CreditService {
    void checkAndDeduct(String credentialId, String serviceCode);

    void refund(String credentialId, String serviceCode);

    int getBalance(String credentialId);
}
