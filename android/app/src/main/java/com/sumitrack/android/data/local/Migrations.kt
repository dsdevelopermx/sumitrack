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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
