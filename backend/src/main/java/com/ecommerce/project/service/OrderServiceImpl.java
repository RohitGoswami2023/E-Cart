package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    CartRepository cartRepository;

    @Autowired
    AddressRepository addressRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    CartService cartService;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    RedisStockGateService redisStockGateService;

    @Autowired
    AuthUtil authUtil;

    @Override
    @Transactional
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage) {
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(cart.getTotalPrice());
        order.setOrderStatus("Accepted");
        order.setAddress(address);

        Payment payment = new Payment(paymentMethod, pgPaymentId, pgStatus, pgResponseMessage, pgName);
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);

        Order savedOrder = orderRepository.save(order);

        List<CartItem> cartItems = cart.getCartItems();
        if (cartItems.isEmpty()) {
            throw new APIException("Cart is empty");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItem.setOrder(savedOrder);
            orderItems.add(orderItem);
        }

        orderItems = orderItemRepository.saveAll(orderItems);

        // Track successfully decremented items for SAGA compensating rollback if downstream payment fails
        List<CartItem> decrementedItems = new ArrayList<>();
        List<CartItem> redisReservedItems = new ArrayList<>();
        try {
            // STEP 1: REDIS STOCK GATE (Shield PostgreSQL from excess traffic)
            for (CartItem item : cart.getCartItems()) {
                Long productId = item.getProduct().getProductId();
                int qty = item.getQuantity();

                boolean redisAllowed = redisStockGateService.tryReserveStock(productId, qty);
                if (!redisAllowed) {
                    throw new APIException("Product '" + item.getProduct().getProductName() + "' is sold out (Redis Gate)!");
                }
                redisReservedItems.add(item);
            }

            // STEP 2: POSTGRESQL ATOMIC DURABLE DECREMENT (ACID Commit)
            for (CartItem item : cart.getCartItems()) {
                int quantity = item.getQuantity();
                Long productId = item.getProduct().getProductId();

                int updated = productRepository.decrementStockSafely(productId, quantity);
                if (updated == 0) {
                    throw new APIException("Product '" + item.getProduct().getProductName() + 
                            "' is out of stock or insufficient quantity!");
                }
                decrementedItems.add(item);
            }

            // Verify payment status (Saga check for external Stripe / Payment Gateway)
            if (pgStatus != null && (pgStatus.equalsIgnoreCase("FAILED") || pgStatus.equalsIgnoreCase("requires_payment_method"))) {
                throw new APIException("Payment failed via gateway: " + pgResponseMessage);
            }

            // Remove items from cart once reservation and payment validation succeed
            List<Long> productIdsToRemove = cart.getCartItems().stream()
                    .map(item -> item.getProduct().getProductId())
                    .toList();
            for (Long pid : productIdsToRemove) {
                cartService.deleteProductFromCart(cart.getCartId(), pid);
            }

        } catch (Exception e) {
            // SAGA COMPENSATING ACTION:
            // 1. Roll back inventory in PostgreSQL
            for (CartItem rolledBackItem : decrementedItems) {
                productRepository.incrementStock(rolledBackItem.getProduct().getProductId(), rolledBackItem.getQuantity());
            }

            // 2. Roll back reservation in Redis
            for (CartItem redisItem : redisReservedItems) {
                redisStockGateService.rollbackReservation(redisItem.getProduct().getProductId(), redisItem.getQuantity());
            }

            // Update order and payment records to FAILED so history is preserved
            savedOrder.setOrderStatus("Payment Failed");
            orderRepository.save(savedOrder);
            payment.setPgStatus("FAILED");
            payment.setPgResponseMessage(e.getMessage());
            paymentRepository.save(payment);

            throw e;
        }

        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);
        orderItems.forEach(item -> orderDTO.getOrderItems().add(modelMapper.map(item, OrderItemDTO.class)));

        orderDTO.setAddressId(addressId);

        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Order> pageOrders = orderRepository.findAll(pageDetails);
        List<Order> orders = pageOrders.getContent();
        List<OrderDTO> orderDTOs = orders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }

    @Override
    public OrderDTO updateOrder(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order","orderId",orderId));
        order.setOrderStatus(status);
        orderRepository.save(order);
        return modelMapper.map(order, OrderDTO.class);
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        User seller = authUtil.loggedInUser();

        Page<Order> pageOrders = orderRepository.findAll(pageDetails);

        List<Order> sellerOrders = pageOrders.getContent().stream()
                .filter(order -> order.getOrderItems().stream()
                        .anyMatch(orderItem -> {
                            var product = orderItem.getProduct();
                            if (product == null || product.getUser() == null) {
                                return false;
                            }
                            return product.getUser().getUserId().equals(
                                    seller.getUserId());
                        }))
                .toList();

        List<OrderDTO> orderDTOs = sellerOrders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }


}
