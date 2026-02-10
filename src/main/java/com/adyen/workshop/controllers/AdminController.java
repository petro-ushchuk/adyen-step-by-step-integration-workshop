package com.adyen.workshop.controllers;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.StoredPaymentMethodDetails;
import com.adyen.model.recurring.DisableRequest;
import com.adyen.service.RecurringApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.util.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
public class AdminController {

    private final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final RecurringApi recurringApi;

    @Autowired
    public AdminController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, RecurringApi recurringApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.recurringApi = recurringApi;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("tokens", Storage.getAllTokens());
        return "admin/index";
    }

    @GetMapping(
            value = {
                    "/admin/makepayment/{recurringDetailReference}",
                    "/makepaymentwithtoken/{recurringDetailReference}",
                    "/api/subscription-payment/{recurringDetailReference}"
            }
    )
    public String payment(@PathVariable(required = false) String recurringDetailReference, Model model) {
        log.info("/admin/makepayment/{}", recurringDetailReference);

        String result;

        try {
            var orderRef = UUID.randomUUID().toString();

            var paymentRequest = new PaymentRequest();
            paymentRequest.setMerchantAccount(this.applicationConfiguration.getAdyenMerchantAccount());
            paymentRequest.setAmount(new Amount().currency("EUR").value(500L));
            paymentRequest.setReference(orderRef);
            paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
            paymentRequest.setShopperReference(Storage.SHOPPER_REFERENCE);
            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
            paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(new StoredPaymentMethodDetails().storedPaymentMethodId(recurringDetailReference)));


            var response = this.paymentsApi.payments(paymentRequest);
            log.info("payment response {}", response);

            if (response.getResultCode().equals(PaymentResponse.ResultCodeEnum.AUTHORISED)) {
                result = "success";
            } else {
                result = "error";
            }

        } catch (ApiException e) {
            log.error("ApiException", e);
            result = "error";
        } catch (Exception e) {
            log.error("Unexpected error while performing the payment", e);
            result = "error";
        }

        model.addAttribute("result", result);
        model.addAttribute("recurringDetailReference", recurringDetailReference);

        return "admin/makepayment";
    }

    @GetMapping(
            value = {
                    "/admin/disable/{recurringDetailReference}",
                    "/api/subscription-cancel/{recurringDetailReference}"
            }
    )
    public String disable(@PathVariable String recurringDetailReference, Model model) {
        log.info("/admin/disable/{}", recurringDetailReference);

        String result;

        try {
            var disableRequest = new DisableRequest();
            disableRequest.setMerchantAccount(this.applicationConfiguration.getAdyenMerchantAccount());
            disableRequest.setShopperReference(Storage.SHOPPER_REFERENCE);
            disableRequest.setRecurringDetailReference(recurringDetailReference);

            var response = this.recurringApi.disable(disableRequest);
            log.info("disable response {}", response);

            Storage.remove(recurringDetailReference, Storage.SHOPPER_REFERENCE);

            log.info("remove token {}", recurringDetailReference);
            result = "success";

        } catch (Exception e) {
            log.error("Unexpected error while disabling the token", e);
            result = "error";
        }

        model.addAttribute("result", result);
        model.addAttribute("recurringDetailReference", recurringDetailReference);

        return "admin/disable";
    }

}
