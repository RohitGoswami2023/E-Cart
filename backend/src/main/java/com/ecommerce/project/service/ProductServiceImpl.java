package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.CategoryRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    AuthUtil authUtil;

    @Autowired
    RedisStockGateService redisStockGateService;

    @Value("${project.image}")
    private String path;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    @Override
    public ProductDTO addProduct(Long categoryId, ProductDTO productDTO) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        boolean isProductNotPresent = true;
        List<Product> products = category.getProducts();
        for (Product value : products) {
            if (value.getProductName().equals(productDTO.getProductName())) {
                isProductNotPresent = false;
                break;
            }
        }

        if (!isProductNotPresent) {
            throw new APIException("Product already exist!!");
        }

        Product product = modelMapper.map(productDTO, Product.class);
        product.setImage("default.png");
        product.setCategory(category);
        product.setUser(authUtil.loggedInUser());
        double specialPrice = product.getPrice() -
                ((product.getDiscount() * 0.01) * product.getPrice());
        product.setSpecialPrice(specialPrice);
        Product savedProduct = productRepository.save(product);
        redisStockGateService.primeStock(savedProduct.getProductId(), savedProduct.getQuantity());
        return modelMapper.map(savedProduct, ProductDTO.class);
    }

    @Override
    public ProductDTO getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
        productDTO.setImage(constructImageUrl(product.getImage()));
        return productDTO;
    }

    @Override
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String keyword, String category) {
        Pageable pageDetails = page(pageNumber, pageSize, sortBy, sortOrder);
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isEmpty()) {
            String escaped = escapeLikePattern(keyword.toLowerCase());
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("productName")), "%" + escaped + "%", '\\'));
        }
        if (category != null && !category.isEmpty()) {
            String escapedCat = escapeLikePattern(category);
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(root.get("category").get("categoryName"), "%" + escapedCat + "%", '\\'));
        }
        Page<Product> pageProducts = productRepository.findAll(spec, pageDetails);
        return toResponse(pageProducts, true);
    }

    @Override
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Page<Product> pageProducts = productRepository.findAll(page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageProducts, true);
    }

    @Override
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user, page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageProducts, true);
    }

    private String constructImageUrl(String imageName) {
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }

    @Override
    public ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));
        Page<Product> pageProducts = productRepository.findByCategoryOrderByPriceAsc(category, page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageProducts, false);
    }

    @Override
    public ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        String escaped = escapeLikePattern(keyword);
        Page<Product> pageProducts = productRepository.findByProductNameLikeIgnoreCase('%' + escaped + '%', page(pageNumber, pageSize, sortBy, sortOrder));
        return toResponse(pageProducts, false);
    }

    @Override
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO, boolean ownerOnly) {
        Product productFromDb = loadMutable(productId, ownerOnly);
        Product product = modelMapper.map(productDTO, Product.class);
        productFromDb.setProductName(product.getProductName());
        productFromDb.setDescription(product.getDescription());
        productFromDb.setQuantity(product.getQuantity());
        productFromDb.setDiscount(product.getDiscount());
        productFromDb.setPrice(product.getPrice());
        productFromDb.setSpecialPrice(product.getSpecialPrice());
        Product savedProduct = productRepository.save(productFromDb);
        redisStockGateService.primeStock(savedProduct.getProductId(), savedProduct.getQuantity());

        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        List<CartDTO> cartDTOs = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            List<ProductDTO> products = cart.getCartItems().stream()
                    .map(p -> modelMapper.map(p.getProduct(), ProductDTO.class)).collect(Collectors.toList());
            cartDTO.setProducts(products);
            return cartDTO;
        }).collect(Collectors.toList());
        cartDTOs.forEach(cart -> cartService.updateProductInCarts(cart.getCartId(), productId));
        return modelMapper.map(savedProduct, ProductDTO.class);
    }

    @Override
    public ProductDTO deleteProduct(Long productId, boolean ownerOnly) {
        Product product = loadMutable(productId, ownerOnly);
        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        carts.forEach(cart -> cartService.clearProductFromCart(cart.getCartId(), productId));
        productRepository.delete(product);
        return modelMapper.map(product, ProductDTO.class);
    }

    @Override
    public ProductDTO updateProductImage(Long productId, MultipartFile image, boolean ownerOnly) throws IOException {
        Product productFromDb = loadMutable(productId, ownerOnly);
        String fileName = fileService.uploadImage(path, image);
        productFromDb.setImage(fileName);
        Product updatedProduct = productRepository.save(productFromDb);
        return modelMapper.map(updatedProduct, ProductDTO.class);
    }

    private Product loadMutable(Long productId, boolean ownerOnly) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        if (!ownerOnly) {
            return product;
        }
        User user = authUtil.loggedInUser();
        boolean admin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.ROLE_ADMIN);
        if (admin) {
            return product;
        }
        if (product.getUser() == null || !product.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }
        return product;
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "productId", "productName", "price", "discount", "specialPrice", "quantity"
    );

    private String escapeLikePattern(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }

    private Pageable page(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        int safePageNumber = (pageNumber == null || pageNumber < 0) ? 0 : pageNumber;
        int safePageSize = (pageSize == null || pageSize <= 0) ? 10 : Math.min(pageSize, 100);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "productId";
        Sort sortByAndOrder = (sortOrder != null && sortOrder.equalsIgnoreCase("desc"))
                ? Sort.by(safeSortBy).descending()
                : Sort.by(safeSortBy).ascending();
        return PageRequest.of(safePageNumber, safePageSize, sortByAndOrder);
    }

    private ProductResponse toResponse(Page<Product> pageProducts, boolean withImageUrl) {
        List<ProductDTO> productDTOS = pageProducts.getContent().stream()
                .map(product -> {
                    ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
                    if (withImageUrl) {
                        productDTO.setImage(constructImageUrl(product.getImage()));
                    }
                    return productDTO;
                })
                .toList();
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }
}
