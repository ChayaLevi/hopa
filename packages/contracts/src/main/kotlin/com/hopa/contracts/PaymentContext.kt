package com.hopa.contracts

data class PaymentContext(
    val amount: String = "",
    val currency: String = "",
    val merchant: String = "",
    val consequence: String = "",
) {
    fun hasDetails(): Boolean = amount.isNotEmpty() || merchant.isNotEmpty()
}
