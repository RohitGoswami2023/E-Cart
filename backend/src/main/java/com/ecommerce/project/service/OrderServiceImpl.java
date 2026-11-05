package com.ecommerce.project.service;

// Connected to: OrderRepository, CartRepository, ProductRepository, PaymentRepository, RedisStockGateService, StripeService
// Purpose: Order placement with payment verification, stock reservation, saga compensation

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @Autowired
    StripeService stripeService;

    @Value("${stripe.currency:usd}")
    private String stripeCurrency;

    @Override
    @Transactional
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage) {
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }
        List<CartItem> cartItems = cart.getCartItems();
        if (cartItems == null || cartItems.isEmpty()) {
            throw new APIException("Cart is empty");
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));
        if (address.getUser() == null || address.getUser().getEmail() == null
                || !address.getUser().getEmail().equalsIgnoreCase(emailId)) {
            throw new ResourceNotFoundException("Address", "addressId", addressId);
        }

        long expectedAmount = Math.round(cart.getTotalPrice() * 100);
        String verifiedPgStatus = pgStatus;
        String verifiedMessage = pgResponseMessage;

        if (paymentMethod != null && paymentMethod.equalsIgnoreCase("STRIPE")) {
            try {
                PaymentIntent intent = stripeService.retrieve(pgPaymentId);
                if (intent == null || !"succeeded".equalsIgnoreCase(intent.getStatus())) {
                    throw new APIException("Payment not succeeded");
                }
                if (intent.getAmount() == null || !intent.getAmount().equals(expectedAmount)) {
                    throw new APIException("Payment amount mismatch");
                }
                if (intent.getCurrency() != null && !intent.getCurrency().equalsIgnoreCase(stripeCurrency)) {
                    throw new APIException("Payment currency mismatch");
                }
                verifiedPgStatus = intent.getStatus();
                verifiedMessage = "verified";
            } catch (StripeException e) {
                throw new APIException("Payment verification failed");
            }
        }

        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(cart.getTotalPrice());
        order.setOrderStatus("Order Confirmed");
        order.setAddress(address);

        Payment payment = new Payment(paymentMethod, pgPaymentId, verifiedPgStatus, verifiedMessage, pgName);
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
        Order savedOrder = orderRepository.save(order);

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

        List<CartItem> redisReservedItems = new ArrayList<>();
        try {
            for (CartItem item : cart.getCartItems()) {
                Long productId = item.getProduct().getProductId();
                int qty = item.getQuantity();
                boolean redisAllowed = redisStockGateService.tryReserveStock(productId, qty);
                if (!redisAllowed) {
                    throw new APIException("Product '" + item.getProduct().getProductName() + "' is sold out (Redis Gate)!");
                }
                redisReservedItems.add(item);
            }

            for (CartItem item : cart.getCartItems()) {
                int quantity = item.getQuantity();
                Long productId = item.getProduct().getProductId();
                int updated = productRepository.decrementStockSafely(productId, quantity);
                if (updated == 0) {
                    throw new APIException("Product '" + item.getProduct().getProductName() +
                            "' is out of stock or insufficient quantity!");
                }
            }

            List<Long> productIdsToRemove = cart.getCartItems().stream()
                    .map(item -> item.getProduct().getProductId())
                    .toList();
            for (Long pid : productIdsToRemove) {
                cartService.clearProductFromCart(cart.getCartId(), pid);
            }
        } catch (Exception e) {
            for (CartItem redisItem : redisReservedItems) {
                redisStockGateService.rollbackReservation(redisItem.getProduct().getProductId(), redisItem.getQuantity());
            }
            throw e;
        }

        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);
        if (orderDTO.getOrderItems() == null) {
            orderDTO.setOrderItems(new ArrayList<>());
        }
        orderItems.forEach(item -> orderDTO.getOrderItems().add(modelMapper.map(item, OrderItemDTO.class)));
        orderDTO.setAddressId(addressId);
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Page<Order> pageOrders = orderRepository.findAll(page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageOrders);
    }

    @Override
    public OrderDTO updateOrder(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        order.setOrderStatus(status);
        orderRepository.save(order);
        return modelMapper.map(order, OrderDTO.class);
    }

    @Override
    public OrderDTO updateSellerOrder(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        User seller = authUtil.loggedInUser();
        boolean owns = order.getOrderItems() != null && order.getOrderItems().stream().anyMatch(item -> {
            Product product = item.getProduct();
            return product != null && product.getUser() != null
                    && product.getUser().getUserId().equals(seller.getUserId());
        });
        if (!owns) {
            throw new ResourceNotFoundException("Order", "orderId", orderId);
        }
        order.setOrderStatus(status);
        orderRepository.save(order);
        return modelMapper.map(order, OrderDTO.class);
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User seller = authUtil.loggedInUser();
        Page<Order> pageOrders = orderRepository.findBySellerId(seller.getUserId(),
                page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageOrders);
    }

    @Override
    public OrderResponse getUserOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        String email = authUtil.loggedInEmail();
        Page<Order> pageOrders = orderRepository.findByEmail(email, page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageOrders);
    }

    @Override
    public OrderDTO getUserOrder(Long orderId) {
        String email = authUtil.loggedInEmail();
        Order order = orderRepository.findByOrderIdAndEmail(orderId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        return modelMapper.map(order, OrderDTO.class);
    }

    private Pageable page(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(pageNumber, pageSize, sortByAndOrder);
    }

    private OrderResponse toResponse(Page<Order> pageOrders) {
        List<OrderDTO> orderDTOs = pageOrders.getContent().stream()
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
