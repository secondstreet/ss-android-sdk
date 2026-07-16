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

### With test server
```kotlin
PromotionKit.show(
    activity = this,
    promoId  = "your-promo-id",
    testServer = "staging"  // Uses https://consumer-qa-staging.secondstreetapp.com
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
| `testServer` | String? | null | Optional test server name (uses consumer-qa-{testServer}.secondstreetapp.com) |
| `heightPercent` | Int | 100 | Screen height % (100 = full, 90 = 90%) |
| `listener` | PromotionListener? | null | Optional callback listener for events |

## Base URLs

- **Production**: `https://consumer.secondstreetapp.com`
- **Staging**: `https://consumer-qa-{testServer}.secondstreetapp.com` (when testServer is provided)

---

## Requirements
- Android API 21+
- Internet permission (auto-included)
