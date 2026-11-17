package com.chewanninja.pricetrackermy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** date fields are LocalDate.toEpochDay() */
@Entity(tableName = "receipts", indices = [Index("fingerprint"), Index("date")])
data class ReceiptEntity(
    @PrimaryKey val receiptId: String,
    val storeName: String, val receiptNo: String, val date: Long,
    val subtotal: Double, val tax: Double, val discount: Double, val rounding: Double, val totalPaid: Double,
    val paymentMethod: String, val notes: String, val imagePath: String, val driveUrl: String,
    val fingerprint: String, val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false
)

@Entity(tableName = "receipt_items", indices = [Index("receiptId")])
data class ReceiptItemEntity(
    @PrimaryKey val itemId: String, val receiptId: String, val name: String, val productKey: String,
    val qty: Double, val unit: String, val unitPrice: Double, val discount: Double,
    val lineTotal: Double, val categoryId: String
)

@Entity(tableName = "expenses", indices = [Index("date"), Index("receiptId")])
data class ExpenseEntity(
    @PrimaryKey val expenseId: String, val receiptId: String, val date: Long, val storeName: String,
    val amount: Double, val paymentMethod: String, val categoryId: String, val notes: String,
    val type: String, val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val budgetId: String, val periodType: String, val periodStart: Long, val periodEnd: Long,
    val categoryId: String, val amount: Double, val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val categoryId: String, val name: String, val monthlyBudget: Double,
    val color: String, val isActive: Boolean = true
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(@PrimaryKey val listId: String, val listName: String, val createdAt: Long, val status: String)

@Entity(tableName = "shopping_items", indices = [Index("listId")])
data class ShoppingItemEntity(
    @PrimaryKey val itemId: String, val listId: String, val productName: String, val quantity: Double,
    val estimatedPrice: Double, val actualPrice: Double, val isPurchased: Boolean, val linkedReceiptId: String = ""
)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val name: String, val value: String)

@Entity(tableName = "sync_jobs")
data class SyncJobEntity(
    @PrimaryKey(autoGenerate = true) val jobId: Long = 0, val type: String, val refId: String,
    val attempts: Int = 0, val lastError: String = "", val createdAt: Long = System.currentTimeMillis()
)
