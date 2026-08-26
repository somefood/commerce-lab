package com.commercelab.product.application.service

import com.commercelab.product.adapter.`in`.web.ProductCreateRequest
import com.commercelab.product.adapter.`in`.web.ProductEditRequest
import com.commercelab.product.application.port.`in`.DeleteProductUseCase
import com.commercelab.product.application.port.`in`.EditProductUseCase
import com.commercelab.product.application.port.`in`.GetProductQuery
import com.commercelab.product.application.port.`in`.RegisterProductUseCase
import com.commercelab.product.application.port.out.ProductRepository
import com.commercelab.product.domain.Product
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository
) : RegisterProductUseCase, GetProductQuery, EditProductUseCase, DeleteProductUseCase {

    override fun registerProduct(createRequest: ProductCreateRequest): Product {
        val product = Product.create(
            name = createRequest.name,
            price = createRequest.price,
            description = createRequest.description,
            stockQuantity = createRequest.stockQuantity
        )
        return productRepository.save(product)
    }

    override fun getProduct(id: Long): Product {
        return productRepository.findById(id) ?: throw NoSuchElementException("Product not found")
    }

    override fun getAllProducts(): List<Product> {
        return productRepository.findAll()
    }

    override fun editProduct(
        id: Long,
        editRequest: ProductEditRequest
    ) {
        val apply = productRepository.findById(id)?.apply {
            copy(name = name, description = description, price = price, stockQuantity = stockQuantity)
        }
        productRepository.save(apply!!)
    }

    override fun deleteProduct(id: Long) {
        val findById = productRepository.findById(id)
        findById?.deactivate()
        productRepository.save(findById!!)
    }
}

