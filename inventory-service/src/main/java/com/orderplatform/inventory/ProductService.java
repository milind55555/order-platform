package com.orderplatform.inventory;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    public static final String CACHE = "products";

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Read-through Redis cache; evicted whenever stock changes. */
    @Cacheable(value = CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductDto getProduct(Long id) {
        return productRepository.findById(id)
                .map(ProductDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional(readOnly = true)
    public List<ProductDto> listProducts() {
        return productRepository.findAll().stream().map(ProductDto::from).toList();
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public ProductDto create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        Product saved = productRepository.save(
                new Product(request.sku(), request.name(), request.price(), request.availableQuantity()));
        return ProductDto.from(saved);
    }
}
