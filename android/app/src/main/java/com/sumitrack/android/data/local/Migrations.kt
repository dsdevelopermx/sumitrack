package com.sumitrack.android.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS clients (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    rfc TEXT,
                    address TEXT,
                    notes TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sales (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_client TEXT NOT NULL,
                    folio TEXT NOT NULL,
                    total TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS products (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    name TEXT NOT NULL,
                    price TEXT NOT NULL,
                    tax_rate TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS product_variants (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_product TEXT NOT NULL,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sales ADD COLUMN subtotal TEXT NOT NULL DEFAULT '0'")
            db.execSQL("ALTER TABLE sales ADD COLUMN tax TEXT NOT NULL DEFAULT '0'")
            // Backfill: sin esto, toda venta creada antes de esta migración queda con
            // subtotal='0'/tax='0' de forma permanente pese a tener un `total` real distinto de
            // cero. No hay forma de reconstruir el desglose histórico exacto (no existía
            // `sale_items` antes de esta historia), así que se aproxima subtotal=total/tax=0 —
            // consistente con AR-17 (precisión monetaria) y evita el gap de integridad de datos
            // más visible (subtotal + tax != total) para historias futuras que lean estos campos.
            db.execSQL("UPDATE sales SET subtotal = total, tax = '0'")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sale_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_sale TEXT NOT NULL,
                    fk_product TEXT NOT NULL,
                    fk_variant TEXT,
                    product_name TEXT NOT NULL,
                    variant_name TEXT,
                    quantity INTEGER NOT NULL,
                    unit_price TEXT NOT NULL,
                    tax_rate TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS installments (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_sale TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    due_date INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS payments (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_sale TEXT NOT NULL,
                    fk_installment TEXT,
                    method TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    paid_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS credit_balances (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_client TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    fk_origin_sale TEXT,
                    applied_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
