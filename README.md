# SS-Android-SDK

Native Android SDK for embedding SecondStreet promotions inside any Android app — no browser takeover.

---

## Installation

### Step 1 — Add JitPack to `settings.gradle`
```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2 — Add dependency to `app/build.gradle`
```gradle
dependencies {
    implementation 'com.github.secondstreet:ss-android-sdk:1.0.0'
}
```

---

## Usage

### Simple open
```kotlin
PromotionKit.show(
    activity = this,
    promoId  = "your-promo-id"
)
```

### With user context
```kotlin
PromotionKit.show(
    activity = this,
    promoId  = "your-promo-id",
    userId   = currentUser.id,
    token    = currentUser.authToken
)
```

### Floating button
```kotlin
PromotionKit.attach(
    activity      = this,
    promoId       = "your-promo-id",
    mode          = PromotionMode.FloatingButton(
        icon  = "🎁",
        label = "Win a Prize"
    ),
    heightPercent = 90
)
```

### With callbacks
```kotlin
PromotionKit.show(
    activity = this,
    promoId  = "your-promo-id",
    listener = object : PromotionListener {
        override fun onComplete(promoId: String, reward: Map<String, Any>?) {
            val coupon = reward?.get("couponCode") as? String
        }
        override fun onClose(promoId: String) { }
        override fun onError(promoId: String, error: PromotionError) { }
    }
)
```

---

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `promoId` | String | required | Your promotion ID |
| `userId` | String? | null | Optional user ID |
| `token` | String? | null | Optional auth token |
| `baseUrl` | String | promos.secondstreet.com | Your platform base URL |
| `heightPercent` | Int | 100 | Screen height % (100 = full, 90 = 90%) |

---

## Requirements
- Android API 21+
- Internet permission (auto-included)
