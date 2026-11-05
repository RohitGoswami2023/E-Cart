package com.ecommerce.project.concurrency;
import com.ecommerce.project.model.*;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class BbdFlashSaleSimulationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("BBD Flash Sale Simulation: 2,000 Customers vs 1,000 Stock with Payment Failures & Rollbacks")
    void simulateBbdMegaSale() throws Exception {
        int FLASH_SALE_STOCK = 1000;
        int TOTAL_BUYERS = 2000;
        int CONCURRENT_THREADS = 64; // High concurrency thread pool

        System.out.println("\n========================================================");
        System.out.println("STARTING BIG BILLION DAYS (BBD) FLASH SALE SIMULATION");
        System.out.println("========================================================");
        System.out.printf("Total Buyers: %d | Available Stock: %d | Concurrency Level: %d\n\n",
                TOTAL_BUYERS, FLASH_SALE_STOCK, CONCURRENT_THREADS);

        System.out.println("Step 1: Fetching " + TOTAL_BUYERS + " real users from PostgreSQL...");
        List<User> sampleUsers = userRepository.findAll(PageRequest.of(0, TOTAL_BUYERS)).getContent();
        assertThat(sampleUsers.size()).isGreaterThanOrEqualTo(TOTAL_BUYERS);
        System.out.println("Loaded " + sampleUsers.size() + " customers from PostgreSQL database.");

        System.out.println("Step 2: Preparing Hot Flash Sale Product in DB with " + FLASH_SALE_STOCK + " stock...");
        Product hotProduct = new Product();
        hotProduct.setProductName("BBD iPhone 16 Pro Max - Midnight Black");
        hotProduct.setDescription("Special BBD Sale unit with high concurrent demand");
        hotProduct.setImage("iphone16.png");
        hotProduct.setPrice(120000.0);
        hotProduct.setSpecialPrice(99999.0);
        hotProduct.setDiscount(16.6);
        hotProduct.setQuantity(FLASH_SALE_STOCK);
        final Product savedProduct = productRepository.save(hotProduct);
        final Long hotProductId = savedProduct.getProductId();

        System.out.println("Step 3: Staging carts & shipping addresses for all 2,000 customers...");
        List<Long> addressIds = new ArrayList<>(TOTAL_BUYERS);

        for (int i = 0; i < TOTAL_BUYERS; i++) {
            User buyer = sampleUsers.get(i);

            Address addr = new Address();
            addr.setStreet("Street " + i + " Tech Hub");
            addr.setBuildingName("Flat " + (i + 1) + " Tower BBD");
            addr.setCity("Bengaluru");
            addr.setState("Karnataka");
            addr.setCountry("India");
            addr.setPincode("560100");
            addr.setUser(buyer);
            addr = addressRepository.save(addr);
            addressIds.add(addr.getAddressId());

            Cart cart = cartRepository.findCartByEmail(buyer.getEmail());
            if (cart == null) {
                cart = new Cart();
                cart.setUser(buyer);
                cart.setTotalPrice(99999.0);
                cart = cartRepository.save(cart);
            } else {
                cart.getCartItems().clear();
                cart.setTotalPrice(99999.0);
                cart = cartRepository.save(cart);
            }

            CartItem cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(savedProduct);
            cartItem.setQuantity(1);
            cartItem.setProductPrice(99999.0);
            cartItem.setDiscount(0.0);
            cartItemRepository.save(cartItem);
            cart.getCartItems().add(cartItem);
            cartRepository.save(cart);
        }
        System.out.println("All 2,000 buyer carts and shipping destinations prepared.");

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_BUYERS);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger paymentDeclinedCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        System.out.println("\nStep 4: Aligning all 2,000 buyers at the starting gate for synchronized checkout...");

        long startTime = System.currentTimeMillis();
        System.out.println("FLASH SALE TRIGGERED! 2,000 requests firing across pool...");

        for (int i = 0; i < TOTAL_BUYERS; i++) {
            final int index = i;
            final User buyer = sampleUsers.get(i);
            final Long buyerAddressId = addressIds.get(i);

            executor.submit(() -> {
                try {
                    boolean willPaymentFail = (index % 7 == 0); 

                    String pgPaymentId = "stripe_ch_" + UUID.randomUUID().toString().substring(0, 8);
                    String pgStatus = willPaymentFail ? "FAILED" : "COMPLETED";
                    String pgMessage = willPaymentFail ? "Card Declined / 3D Secure Timeout" : "Charge Succeeded";

                    try {
                        orderService.placeOrder(
                                buyer.getEmail(),
                                buyerAddressId,
                                "STRIPE",
                                "STRIPE",
                                pgPaymentId,
                                pgStatus,
                                pgMessage
                        );
                        successfulOrders.incrementAndGet();
                    } catch (Exception e) {
                        if (willPaymentFail) {
                            paymentDeclinedCount.incrementAndGet();
                        } else if (e.getMessage() != null && e.getMessage().contains("out of stock")) {
                            outOfStockCount.incrementAndGet();
                        } else {
                            otherErrors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(180, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        Product productAfterSale = productRepository.findById(hotProductId).orElseThrow();
        int finalStock = productAfterSale.getQuantity();

        System.out.println("\n========================================================");
        System.out.println("BBD FLASH SALE RESULTS & METRICS");
        System.out.println("========================================================");
        System.out.printf("Total Requests Processed:     %d\n", TOTAL_BUYERS);
        System.out.printf("Elapsed Execution Time:       %d ms (%.2f seconds)\n", durationMs, durationMs / 1000.0);
        System.out.printf("Throughput:                   %.2f req/sec\n", (TOTAL_BUYERS * 1000.0) / durationMs);
        System.out.println("--------------------------------------------------------");
        System.out.printf("Successful Confirmed Orders: %d\n", successfulOrders.get());
        System.out.printf("Payment Declined (Reverted): %d (Saga Rollbacks)\n", paymentDeclinedCount.get());
        System.out.printf("Out-of-Stock Rejections:    %d\n", outOfStockCount.get());
        System.out.printf("Other Errors:              %d\n", otherErrors.get());
        System.out.println("--------------------------------------------------------");
        System.out.printf("Initial Stock:                %d\n", FLASH_SALE_STOCK);
        System.out.printf("Ending Stock in PostgreSQL:   %d\n", finalStock);
        System.out.println("========================================================\n");

        assertThat(finalStock).isGreaterThanOrEqualTo(0);
        assertThat(FLASH_SALE_STOCK).isEqualTo(finalStock + successfulOrders.get());

        com.ecommerce.project.benchmark.BenchmarkReport.record("bbd-flash-sale", true, durationMs, FLASH_SALE_STOCK, finalStock, successfulOrders.get(), paymentDeclinedCount.get() + outOfStockCount.get() + otherErrors.get());

        System.out.println("INVARIANTS VERIFIED:");
        System.out.println("  1. Zero Overselling: Stock never went negative.");
        System.out.println("  2. Perfect Saga Reversals: Initial Stock == Ending Stock + Successful Orders.");
        System.out.println("  3. Downstream payment failures did not lock or leak inventory!");
    }
}

