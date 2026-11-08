package com.ecommerce.project.service;

// Connected to: Stripe API
// Purpose: PaymentIntent creation with idempotency, customer search/create

import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.repositories.UserRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeServiceImpl implements StripeService {

    @Value("${stripe.secret.key:}")
    private String stripeApiKey;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        if (stripeApiKey != null && !stripeApiKey.isBlank()) {
            Stripe.apiKey = stripeApiKey;
        }
    }

    @Override
    public PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException {
        String email = stripePaymentDto.getEmail();
        var userOpt = email != null ? userRepository.findByEmail(email)
                : java.util.Optional.<com.ecommerce.project.model.User>empty();
        String customerId = (userOpt != null && userOpt.isPresent()) ? userOpt.get().getStripeCustomerId() : null;

        if (customerId == null || customerId.isBlank()) {
            CustomerSearchParams searchParams = CustomerSearchParams.builder()
                    .setQuery("email:'" + email + "'")
                    .build();
            CustomerSearchResult customers = Customer.search(searchParams);
            if (customers.getData().isEmpty()) {
                CustomerCreateParams.Builder customerParamsBuilder = CustomerCreateParams.builder()
                        .setEmail(email)
                        .setName(stripePaymentDto.getName());

                if (stripePaymentDto.getAddress() != null) {
                    customerParamsBuilder.setAddress(
                            CustomerCreateParams.Address.builder()
                                    .setLine1(stripePaymentDto.getAddress().getStreet())
                                    .setCity(stripePaymentDto.getAddress().getCity())
                                    .setState(stripePaymentDto.getAddress().getState())
                                    .setPostalCode(stripePaymentDto.getAddress().getPincode())
                                    .setCountry(stripePaymentDto.getAddress().getCountry())
                                    .build());
                }

                RequestOptions customerOpts = RequestOptions.builder()
                        .setIdempotencyKey(
                                "cus_idem_" + (email != null ? email.replace("@", "_").replace(".", "_") : "anon"))
                        .build();
                Customer customer = Customer.create(customerParamsBuilder.build(), customerOpts);
                customerId = customer.getId();
            } else {
                customerId = customers.getData().get(0).getId();
            }

            if (userOpt != null && userOpt.isPresent()) {
                com.ecommerce.project.model.User user = userOpt.get();
                user.setStripeCustomerId(customerId);
                userRepository.save(user);
            }
        }

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(stripePaymentDto.getAmount())
                .setCurrency(stripePaymentDto.getCurrency())
                .setCustomer(customerId)
                .setDescription(stripePaymentDto.getDescription())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build());

        if (stripePaymentDto.getMetadata() != null && !stripePaymentDto.getMetadata().isEmpty()) {
            paramsBuilder.putAllMetadata(stripePaymentDto.getMetadata());
        }

        String idempotencyKey = (stripePaymentDto.getMetadata() != null
                && stripePaymentDto.getMetadata().containsKey("idempotencyKey"))
                        ? stripePaymentDto.getMetadata().get("idempotencyKey")
                        : "stripe_idem_" + customerId + "_" + stripePaymentDto.getAmount() + "_"
                                + System.currentTimeMillis() / 60000;

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        return PaymentIntent.create(paramsBuilder.build(), requestOptions);
    }

    @Override
    public PaymentIntent retrieve(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    @Override
    public void refund(String paymentIntentId, String reason) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return;
        }
        com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .putMetadata("reason", reason != null ? reason : "Order compensation")
                .build();
        com.stripe.model.Refund.create(params);
    }
}
