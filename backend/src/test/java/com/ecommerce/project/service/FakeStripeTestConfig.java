package com.ecommerce.project.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakeStripeTestConfig {

    @Bean
    @Primary
    public FakeStripeService fakeStripeService() {
        return new FakeStripeService();
    }
}

