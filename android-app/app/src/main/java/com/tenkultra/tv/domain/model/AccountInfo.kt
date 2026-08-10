package com.tenkultra.tv.domain.model

/** Subscriber account / subscription details shown in Settings → Subscription. */
data class AccountInfo(
    val name: String,          // who it's registered to
    val packageName: String,   // tariff / package
    val expiry: String,        // expiry date as reported by the portal ("—" if unknown)
    val daysLeft: Int?,        // days until expiry (null if not parseable)
    val phone: String,         // phone / account note (resellers often store the plan here)
    val status: String,        // Active / Blocked
    val isTrial: Boolean       // package looks like a trial/test plan
)
