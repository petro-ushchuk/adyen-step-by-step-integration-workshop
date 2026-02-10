package com.adyen.workshop.controllers;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.service.RecurringApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.util.Storage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
public class SubscriptionController {
    private final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;

    public SubscriptionController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, RecurringApi recurringApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
    }


    @PostMapping("/api/subscription-create")
    public ResponseEntity<CreateCheckoutSessionResponse> create(@RequestHeader String host, HttpServletRequest request) throws IOException, ApiException {
        var orderRef = UUID.randomUUID().toString();
        var amount = new Amount()
                .currency("EUR")
                .value(0L); // zero-auth transaction

        var checkoutSession = new CreateCheckoutSessionRequest();
        checkoutSession.setAmount(amount);
        checkoutSession.countryCode("NL");
        checkoutSession.merchantAccount(this.applicationConfiguration.getAdyenMerchantAccount());
        checkoutSession.setReference(orderRef);
        checkoutSession.setShopperReference(Storage.SHOPPER_REFERENCE);
        checkoutSession.setChannel(CreateCheckoutSessionRequest.ChannelEnum.WEB);
        checkoutSession.setReturnUrl(request.getScheme() + "://" + host + "/redirect?orderRef=" + orderRef);
        // recurring payment settings
        checkoutSession.setShopperInteraction(CreateCheckoutSessionRequest.ShopperInteractionEnum.ECOMMERCE);
        checkoutSession.setRecurringProcessingModel(CreateCheckoutSessionRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        checkoutSession.setEnableRecurring(true);

        log.info("/tokenization/sessions {}", checkoutSession);
        var response = paymentsApi.sessions(checkoutSession);
        return ResponseEntity.ok().body(response);
    }

}