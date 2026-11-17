package com.chewanninja.pricetrackermy.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // receipts + items
    @Query("SELECT * FROM receipts WHERE deleted=0 ORDER BY date DESC, createdAt DESC") fun receipts(): Flow<List<ReceiptEntity>>
    @Query("SELECT * FROM receipts WHERE receiptId=:id") suspend fun receipt(id: String): ReceiptEntity?
    @Query("SELECT * FROM receipts WHERE fingerprint=:fp AND deleted=0 AND receiptId!=:except LIMIT 1") suspend fun duplicate(fp: String, except: String): ReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReceipt(r: ReceiptEntity)
    @Query("SELECT * FROM receipt_items") fun allItems(): Flow<List<ReceiptItemEntity>>
    @Query("SELECT * FROM receipt_items WHERE receiptId=:id") suspend fun itemsOf(id: String): List<ReceiptItemEntity>
    @Query("DELETE FROM receipt_items WHERE receiptId=:id") suspend fun deleteItemsOf(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putItems(l: List<ReceiptItemEntity>)

    // expenses
    @Query("SELECT * FROM expenses WHERE deleted=0 ORDER BY date DESC, createdAt DESC") fun expenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses WHERE deleted=0") suspend fun expensesOnce(): List<ExpenseEntity>
    @Query("SELECT * FROM expenses WHERE expenseId=:id") suspend fun expense(id: String): ExpenseEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExpense(e: ExpenseEntity)

    // budgets
    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun budgetsOnce(): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE budgetId=:id") suspend fun budget(id: String): BudgetEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudget(b: BudgetEntity)

    // categories
    @Query("SELECT * FROM categories ORDER BY isActive DESC, name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun categoriesOnce(): List<CategoryEntity>
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategory(c: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(l: List<CategoryEntity>)

    // shopping
    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC") fun lists(): Flow<List<ShoppingListEntity>>
    @Query("SELECT * FROM shopping_lists WHERE listId=:id") suspend fun list(id: String): ShoppingListEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putList(l: ShoppingListEntity)
    @Query("DELETE FROM shopping_lists WHERE listId=:id") suspend fun deleteList(id: String)
    @Query("SELECT * FROM shopping_items") fun shopItems(): Flow<List<ShoppingItemEntity>>
    @Query("SELECT * FROM shopping_items WHERE listId=:id") suspend fun shopItemsOf(id: String): List<ShoppingItemEntity>
    @Query("SELECT * FROM shopping_items WHERE itemId=:id") suspend fun shopItem(id: String): ShoppingItemEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putShopItems(l: List<ShoppingItemEntity>)
    @Query("DELETE FROM shopping_items WHERE itemId=:id") suspend fun deleteShopItem(id: String)
    @Query("DELETE FROM shopping_items WHERE listId=:id") suspend fun deleteShopItemsOf(id: String)

    // settings
    @Query("SELECT * FROM settings") fun settings(): Flow<List<SettingEntity>>
    @Query("SELECT * FROM settings") suspend fun settingsOnce(): List<SettingEntity>
    @Query("SELECT value FROM settings WHERE name=:n") suspend fun setting(n: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(s: SettingEntity)

    // sync queue
    @Insert suspend fun addJob(j: SyncJobEntity)
    @Query("DELETE FROM sync_jobs WHERE type=:t AND refId=:r") suspend fun dropJobs(t: String, r: String)
    @Query("SELECT * FROM sync_jobs ORDER BY jobId") suspend fun pendingJobs(): List<SyncJobEntity>
    @Query("DELETE FROM sync_jobs WHERE jobId=:id") suspend fun deleteJob(id: Long)
    @Update suspend fun updateJob(j: SyncJobEntity)
    @Query("SELECT COUNT(*) FROM sync_jobs") fun jobCount(): Flow<Int>
    @Query("SELECT lastError FROM sync_jobs WHERE lastError!='' ORDER BY jobId DESC LIMIT 1") fun lastError(): Flow<String?>
}

@Database(
    entities = [ReceiptEntity::class, ReceiptItemEntity::class, ExpenseEntity::class, BudgetEntity::class,
        CategoryEntity::class, ShoppingListEntity::class, ShoppingItemEntity::class, SettingEntity::class, SyncJobEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "pricetracker.db").build().also { inst = it }
        }
    }
}
