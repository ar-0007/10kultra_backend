package com.tenkultra.tv.data.api.models

import com.google.gson.annotations.SerializedName

/**
 * Response of action=get_main_info (type=account_info). Carries the subscriber's
 * account details — name, tariff/package, expiry date, phone, balance. Field names
 * vary between Ministra/Stalker panels, so several aliases are modelled and the
 * repository picks whichever is present.
 */
data class AccountInfoResponse(
    @SerializedName("js") val js: AccountInfoData?
)

data class AccountInfoData(
    @SerializedName("mac") val mac: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("fname") val fname: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("login") val login: String?,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("exp_billing_date") val expBillingDate: String?,
    @SerializedName("tariff_plan") val tariffPlan: String?,
    @SerializedName("tariff_plan_name") val tariffPlanName: String?,
    @SerializedName("account_balance") val balance: String?,
    @SerializedName("status") val status: Int?
)
