package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.util.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.SignatureException;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;

    private final HMACValidator hmacValidator;

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
    }

    // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received: {}", json);
        var notificationRequest = NotificationRequest.fromJson(json);
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        if (notificationRequestItem.isPresent()) {

            NotificationRequestItem item = notificationRequestItem.get();


            try {
                if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                    log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                    return ResponseEntity.ok().build();
                }

                log.info("Received webhook success:{} eventCode:{}", item.isSuccess(), item.getEventCode());

                if(item.isSuccess()) {
                    // read about eventcode "RECURRING_CONTRACT" here: https://docs.adyen.com/online-payments/tokenization/create-and-use-tokens?tab=subscriptions_2#pending-and-refusal-result-codes-1
                    if (item.getEventCode().equals("RECURRING_CONTRACT") && item.getAdditionalData() != null && item.getAdditionalData().get("recurring.shopperReference") != null) {
                        // webhook with recurring token
                        log.info("Recurring authorized - recurringDetailReference {}", item.getAdditionalData().get("recurring.recurringDetailReference"));

                        // save token
                        Storage.add(item.getAdditionalData().get("recurring.recurringDetailReference"), item.getPaymentMethod(), item.getAdditionalData().get("recurring.shopperReference"));
                    } else if (item.getEventCode().equals("AUTHORISATION")) {
                        // webhook with payment authorisation
                        log.info("Payment authorized - PspReference {}", item.getPspReference());
                    } else {
                        // unexpected eventCode
                        log.warn("Unexpected eventCode: {}", item.getEventCode());
                    }
                } else {
                    // Operation has failed: check the reason field for failure information.
                    log.info("Operation has failed: {}", item.getReason());
                }

            } catch (SignatureException e) {
                // Unexpected error during HMAC validation
                log.error("Error while validating HMAC Key", e);
                throw new RuntimeException(e.getMessage());
            }

        }

        // Acknowledge event has been consumed
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}