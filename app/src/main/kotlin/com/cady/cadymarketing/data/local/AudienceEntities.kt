package com.cady.cadymarketing.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A copy of one Kady customer. The whole table is replaced on every refresh from Kady (the marketing app never writes to `customers`). */
@Entity(tableName = "customer_copy")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val rawPhone: String,
    val area: String,
    val repName: String,
    val active: Boolean,
    val fetchedAt: Long,
)

/** What one customer agreed to (`mkt_consent`). A deleted row only gets [deletedAt]: it means "no consent recorded". */
@Entity(tableName = "consent", indices = [Index(value = ["updatedAt"])])
data class ConsentEntity(
    @PrimaryKey val customerId: String,
    val status: String,
    val note: String,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** A group of customers to send to. [memberIds] are the picked customers (one per line) of a manual segment. */
@Entity(tableName = "segment", indices = [Index(value = ["updatedAt"])])
data class SegmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val area: String,
    val repName: String,
    val memberIds: String,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** One line of a send list: what was done for one customer with one post. */
@Entity(
    tableName = "send_mark",
    primaryKeys = ["contentId", "customerId"],
    indices = [Index(value = ["updatedAt"])],
)
data class SendMarkEntity(
    val contentId: String,
    val customerId: String,
    val status: String,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** A result written by hand for a campaign (orders, sales, reach or replies on a day). */
@Entity(tableName = "result", indices = [Index(value = ["updatedAt"]), Index(value = ["campaignId"])])
data class ResultEntity(
    @PrimaryKey val id: String,
    val campaignId: String,
    val channel: String?,
    val metric: String,
    val value: Long,
    val date: String,
    val note: String,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** An order that carries a campaign code, copied from the website or from Kady. Replaced per origin on every refresh. */
@Entity(tableName = "report_order", indices = [Index(value = ["origin"])])
data class OrderEntity(
    @PrimaryKey val id: String,
    val origin: String,
    val campaignCode: String,
    val channel: String?,
    val amount: Long?,
    val date: String?,
    val cancelled: Boolean,
    val fetchedAt: Long,
)
