package com.ecommerce.project.service;

// Connected to: Stripe API
// Purpose: PaymentIntent creation with idempotency, customer search/create

import com.ecommerce.project.payload.StripePaymentDto;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeServiceImpl implements StripeService {

    @Value("${stripe.secret.key:}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        if (stripeApiKey != null && !stripeApiKey.isBlank()) {
            Stripe.apiKey = stripeApiKey;
        }
    }

    @Override
    public PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException {
        Customer customer;
        CustomerSearchParams searchParams =
                CustomerSearchParams.builder()
                        .setQuery("email:'" + stripePaymentDto.getEmail() + "'")
                        .build();
        CustomerSearchResult customers = Customer.search(searchParams);
        if (customers.getData().isEmpty()) {
            CustomerCreateParams.Builder customerParamsBuilder = CustomerCreateParams.builder()
                    .setEmail(stripePaymentDto.getEmail())
                    .setName(stripePaymentDto.getName());

            if (stripePaymentDto.getAddress() != null) {
                customerParamsBuilder.setAddress(
                        CustomerCreateParams.Address.builder()
                                .setLine1(stripePaymentDto.getAddress().getStreet())
                                .setCity(stripePaymentDto.getAddress().getCity())
                                .setState(stripePaymentDto.getAddress().getState())
                                .setPostalCode(stripePaymentDto.getAddress().getPincode())
                                .setCountry(stripePaymentDto.getAddress().getCountry())
                                .build()
                );
            }

            RequestOptions customerOpts = RequestOptions.builder()
                    .setIdempotencyKey("cus_idem_" + stripePaymentDto.getEmail().replace("@", "_").replace(".", "_"))
                    .build();
            customer = Customer.create(customerParamsBuilder.build(), customerOpts);
        } else {
            customer = customers.getData().get(0);
        }

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(stripePaymentDto.getAmount())
                .setCurrency(stripePaymentDto.getCurrency())
                .setCustomer(customer.getId())
                .setDescription(stripePaymentDto.getDescription())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                );

        if (stripePaymentDto.getMetadata() != null && !stripePaymentDto.getMetadata().isEmpty()) {
            paramsBuilder.putAllMetadata(stripePaymentDto.getMetadata());
        }

        String idempotencyKey = (stripePaymentDto.getMetadata() != null && stripePaymentDto.getMetadata().containsKey("idempotencyKey"))
                ? stripePaymentDto.getMetadata().get("idempotencyKey")
                : "stripe_idem_" + customer.getId() + "_" + stripePaymentDto.getAmount() + "_" + System.currentTimeMillis() / 60000;

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        return PaymentIntent.create(paramsBuilder.build(), requestOptions);
    }

    @Override
    public PaymentIntent retrieve(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
