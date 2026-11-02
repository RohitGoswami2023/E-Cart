package com.ecommerce.project.service;

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
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class StripeServiceImpl implements StripeService {

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init(){
        Stripe.apiKey = stripeApiKey;
    }

    @Override
    public PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException {
        Customer customer;
        // Search customer by email
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

            // Customer creation with idempotency on email to prevent duplicate customer IDs
            RequestOptions customerOpts = RequestOptions.builder()
                    .setIdempotencyKey("cus_idem_" + stripePaymentDto.getEmail().replace("@", "_").replace(".", "_"))
                    .build();

            customer = Customer.create(customerParamsBuilder.build(), customerOpts);
        } else {
            // Fetch the existing customer
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

        // Attach metadata if available
        if (stripePaymentDto.getMetadata() != null && !stripePaymentDto.getMetadata().isEmpty()) {
            paramsBuilder.putAllMetadata(stripePaymentDto.getMetadata());
        }

        PaymentIntentCreateParams params = paramsBuilder.build();

        // Idempotency: Use client-provided key or unique fallback based on customer & amount to prevent duplicate charges
        String idempotencyKey = (stripePaymentDto.getMetadata() != null && stripePaymentDto.getMetadata().containsKey("idempotencyKey"))
                ? stripePaymentDto.getMetadata().get("idempotencyKey")
                : "stripe_idem_" + customer.getId() + "_" + stripePaymentDto.getAmount() + "_" + System.currentTimeMillis() / 60000;

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        return PaymentIntent.create(params, requestOptions);
    }
}
