package com.secondstreet.sdk

interface PromotionListener {
    fun onLoad(promoId: String) {}
    fun onComplete(promoId: String, reward: Map<String, Any>?) {}
    fun onClose(promoId: String) {}
    fun onError(promoId: String, error: PromotionError) {}
    fun onEvent(promoId: String, eventName: String, data: Map<String, Any>?) {}
}

sealed class PromotionError(val message: String) {
    class NetworkError(message: String) : PromotionError(message)
    class InvalidPromotion(message: String) : PromotionError(message)
    class Unknown(message: String) : PromotionError(message)
}
