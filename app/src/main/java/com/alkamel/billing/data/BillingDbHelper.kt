package com.alkamel.billing.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BillingDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {

        // إعدادات التطبيق
        db.execSQL(
            """
            CREATE TABLE settings (
                id INTEGER PRIMARY KEY,
                station_name TEXT NOT NULL DEFAULT 'محطة العسل للطاقة النظيفة',
                unit_price REAL NOT NULL DEFAULT 170,
                next_invoice INTEGER NOT NULL DEFAULT 1,
                next_receipt INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO settings
            (id, station_name, unit_price, next_invoice, next_receipt)
            VALUES (1, 'محطة العسل للطاقة النظيفة', 170, 1, 1)
            """.trimIndent()
        )

        // المشتركين
        db.execSQL(
            """
            CREATE TABLE subscribers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                meter TEXT NOT NULL,
                phone TEXT DEFAULT '',
                price REAL NOT NULL DEFAULT 170,
                previous_reading REAL NOT NULL DEFAULT 0,
                arrears REAL NOT NULL DEFAULT 0,
                credit REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )

        // الفواتير
        db.execSQL(
            """
            CREATE TABLE invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL UNIQUE,
                subscriber_id INTEGER NOT NULL,
                subscriber_name TEXT NOT NULL,
                meter TEXT NOT NULL,
                phone TEXT DEFAULT '',
                date TEXT NOT NULL,
                previous_reading REAL NOT NULL DEFAULT 0,
                current_reading REAL NOT NULL DEFAULT 0,
                consumption REAL NOT NULL DEFAULT 0,
                unit_price REAL NOT NULL DEFAULT 0,
                discount REAL NOT NULL DEFAULT 0,
                old_arrears REAL NOT NULL DEFAULT 0,
                credit_used REAL NOT NULL DEFAULT 0,
                total REAL NOT NULL DEFAULT 0,
                paid REAL NOT NULL DEFAULT 0,
                remaining REAL NOT NULL DEFAULT 0,
                FOREIGN KEY(subscriber_id) REFERENCES subscribers(id)
            )
            """.trimIndent()
        )

        // الإيصالات
        db.execSQL(
            """
            CREATE TABLE receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL UNIQUE,
                subscriber_id INTEGER NOT NULL,
                subscriber_name TEXT NOT NULL,
                phone TEXT DEFAULT '',
                date TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                note TEXT DEFAULT '',
                FOREIGN KEY(subscriber_id) REFERENCES subscribers(id)
            )
            """.trimIndent()
        )

        // فهارس لتسريع البحث
        db.execSQL(
            "CREATE INDEX idx_subscribers_meter ON subscribers(meter)"
        )

        db.execSQL(
            "CREATE INDEX idx_subscribers_phone ON subscribers(phone)"
        )

        db.execSQL(
            "CREATE INDEX idx_invoices_subscriber ON invoices(subscriber_id)"
        )

        db.execSQL(
            "CREATE INDEX idx_receipts_subscriber ON receipts(subscriber_id)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            // reserved for future database upgrades
        }
    }

    // إضافة مشترك
    fun addSubscriber(
        name: String,
        meter: String,
        phone: String,
        price: Double,
        previousReading: Double = 0.0
    ): Long {

        val values = ContentValues().apply {
            put("name", name)
            put("meter", meter)
            put("phone", phone)
            put("price", price)
            put("previous_reading", previousReading)
            put("arrears", 0.0)
            put("credit", 0.0)
            put("created_at", System.currentTimeMillis().toString())
        }

        return writableDatabase.insert(
            "subscribers",
            null,
            values
        )
    }

    // تعديل مشترك
    fun updateSubscriber(
        id: Long,
        name: String,
        meter: String,
        phone: String,
        price: Double
    ): Int {

        val values = ContentValues().apply {
            put("name", name)
            put("meter", meter)
            put("phone", phone)
            put("price", price)
        }

        return writableDatabase.update(
            "subscribers",
            values,
            "id = ?",
            arrayOf(id.toString())
        )
    }

    // حذف مشترك
    fun deleteSubscriber(id: Long): Int {
        return writableDatabase.delete(
            "subscribers",
            "id = ?",
            arrayOf(id.toString())
        )
    }

    // الحصول على رقم الفاتورة التالي
    fun nextInvoiceNumber(): Int {
        val db = writableDatabase

        val cursor = db.rawQuery(
            "SELECT next_invoice FROM settings WHERE id = 1",
            null
        )

        var number = 1

        if (cursor.moveToFirst()) {
            number = cursor.getInt(0)
        }

        cursor.close()

        val values = ContentValues().apply {
            put("next_invoice", number + 1)
        }

        db.update(
            "settings",
            values,
            "id = 1",
            null
        )

        return number
    }

    // الحصول على رقم الإيصال التالي
    fun nextReceiptNumber(): Int {
        val db = writableDatabase

        val cursor = db.rawQuery(
            "SELECT next_receipt FROM settings WHERE id = 1",
            null
        )

        var number = 1

        if (cursor.moveToFirst()) {
            number = cursor.getInt(0)
        }

        cursor.close()

        val values = ContentValues().apply {
            put("next_receipt", number + 1)
        }

        db.update(
            "settings",
            values,
            "id = 1",
            null
        )

        return number
    }

    // حفظ فاتورة
    fun addInvoice(values: ContentValues): Long {
        return writableDatabase.insert(
            "invoices",
            null,
            values
        )
    }

    // حفظ إيصال
    fun addReceipt(values: ContentValues): Long {
        return writableDatabase.insert(
            "receipts",
            null,
            values
        )
    }

    // تحديث مديونية المشترك
    fun updateSubscriberBalance(
        subscriberId: Long,
        arrears: Double,
        credit: Double
    ): Int {

        val values = ContentValues().apply {
            put("arrears", arrears)
            put("credit", credit)
        }

        return writableDatabase.update(
            "subscribers",
            values,
            "id = ?",
            arrayOf(subscriberId.toString())
        )
    }

    // تغيير سعر الوحدة الافتراضي
    fun updateUnitPrice(price: Double): Int {

        val values = ContentValues().apply {
            put("unit_price", price)
        }

        return writableDatabase.update(
            "settings",
            values,
            "id = 1",
            null
        )
    }

    // تغيير اسم المحطة
    fun updateStationName(name: String): Int {

        val values = ContentValues().apply {
            put("station_name", name)
        }

        return writableDatabase.update(
            "settings",
            values,
            "id = 1",
            null
        )
    }

    // إنشاء نسخة احتياطية من قاعدة البيانات
    fun exportDatabase(): SQLiteDatabase {
        return readableDatabase
    }

    companion object {
        private const val DATABASE_NAME = "honey_billing.db"
        private const val DATABASE_VERSION = 1
    }
}
