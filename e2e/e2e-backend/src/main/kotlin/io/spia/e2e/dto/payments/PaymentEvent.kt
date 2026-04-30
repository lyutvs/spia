package io.spia.e2e.dto.payments

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = CardPayload::class, name = "card"),
    JsonSubTypes.Type(value = BankTransferPayload::class, name = "bank"),
)
sealed class PaymentPayload

@JsonTypeName("card")
data class CardPayload(val last4: String, val brand: String) : PaymentPayload()

@JsonTypeName("bank")
data class BankTransferPayload(val account: String) : PaymentPayload()

data class PaymentEvent(
    val kind: String,
    val payload: PaymentPayload,
    val amountCents: Long,
)
