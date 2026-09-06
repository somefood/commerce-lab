package com.commercelab.product.application.service

import com.commercelab.common.Money
import com.commercelab.product.application.port.`in`.AdjustStockUseCase
import com.commercelab.product.application.port.`in`.DeleteProductUseCase
import com.commercelab.product.application.port.`in`.EditProductCommand
import com.commercelab.product.application.port.`in`.EditProductUseCase
import com.commercelab.product.application.port.`in`.GetProductQuery
import com.commercelab.product.application.port.`in`.RegisterProductCommand
import com.commercelab.product.application.port.`in`.RegisterProductUseCase
import com.commercelab.product.application.port.out.ProductRepository
import com.commercelab.product.domain.Product
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository
) : RegisterProductUseCase, GetProductQuery, EditProductUseCase, AdjustStockUseCase, DeleteProductUseCase {

    override fun registerProduct(registerProductCommand: RegisterProductCommand): Product {
        val product = Product.create(
            name = registerProductCommand.name,
            price = Money.of(registerProductCommand.price),
            description = registerProductCommand.description,
            stockQuantity = registerProductCommand.stockQuantity
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
        editRequest: EditProductCommand
    ) {
        val product = productRepository.findById(id) ?: throw NoSuchElementException("Product not found")

        val editedProduct = product.copy(
            name = editRequest.name,
            description = editRequest.description,
            price = Money.of(editRequest.price),
        )
        productRepository.save(editedProduct)
    }

    override fun adjustStock(productId: Long, amount: Int) {
        val product = productRepository.findById(productId) ?: throw NoSuchElementException("Product not found")
        productRepository.save(product.adjustStockQuantity(amount))
    }

    override fun deleteProduct(id: Long) {
        val product = productRepository.findById(id) ?: throw NoSuchElementException("Product not found")
        val deactivatedProduct = product.deactivate()
        productRepository.save(deactivatedProduct)
    }
}

