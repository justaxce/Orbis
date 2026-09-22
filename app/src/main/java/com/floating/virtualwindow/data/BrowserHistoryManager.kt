package com.floating.virtualwindow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val domain: String,
    val timestamp: Long,
    val visitCount: Int
)

/**
 * Invisible, device-local SQLite database manager for recording browsing history.
 * Runs completely asynchronously with zero UI exposed to ensure privacy and peak performance.
 */
class BrowserHistoryManager private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "BrowserHistoryManager"
        private const val DB_NAME = "orbis_browser_history.db"
        private const val DB_VERSION = 1

        private const val TABLE_HISTORY = "browser_history"
        private const val COL_ID = "id"
        private const val COL_URL = "url"
        private const val COL_TITLE = "title"
        private const val COL_DOMAIN = "domain"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_VISIT_COUNT = "visit_count"

        @Volatile
        private var INSTANCE: BrowserHistoryManager? = null

        fun getInstance(context: Context): BrowserHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BrowserHistoryManager(context).also { INSTANCE = it }
            }
        }

        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        /**
         * Silently records a page visit in the local SQLite database.
         */
        fun recordVisit(context: Context, url: String?, title: String?) {
            if (url.isNullOrBlank()) return
            // Only record authentic web URLs (skip internal schemes like about:blank, data:, javascript:)
            if (!url.startsWith("http://") && !url.startsWith("https://")) return

            coroutineScope.launch {
                try {
                    val manager = getInstance(context)
                    manager.insertOrUpdateVisit(url, title ?: "")
                } catch (e: Exception) {
                    Log.w(TAG, "Error recording history: ${e.message}")
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URL TEXT NOT NULL,
                $COL_TITLE TEXT,
                $COL_DOMAIN TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_VISIT_COUNT INTEGER DEFAULT 1
            )
        """.trimIndent()
        db.execSQL(createTableSql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_url ON $TABLE_HISTORY ($COL_URL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON $TABLE_HISTORY ($COL_TIMESTAMP)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    @Synchronized
    private fun insertOrUpdateVisit(url: String, rawTitle: String) {
        val db = writableDatabase
        val domain = try {
            Uri.parse(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
        val cleanTitle = rawTitle.ifBlank { domain.ifBlank { url } }
        val now = System.currentTimeMillis()

        // Debounce: check if the same URL was visited in the last 30 seconds
        val cursor = db.query(
            TABLE_HISTORY,
            arrayOf(COL_ID, COL_VISIT_COUNT, COL_TIMESTAMP),
            "$COL_URL = ?",
            arrayOf(url),
            null,
            null,
            "$COL_TIMESTAMP DESC",
            "1"
        )

        cursor.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COL_ID))
                val count = it.getInt(it.getColumnIndexOrThrow(COL_VISIT_COUNT))
                val lastTime = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))

                // If visited within 30s, update timestamp and title without inflating count
                val cv = ContentValues().apply {
                    put(COL_TIMESTAMP, now)
                    if (cleanTitle.isNotBlank()) put(COL_TITLE, cleanTitle)
                    if (now - lastTime > 30_000) {
                        put(COL_VISIT_COUNT, count + 1)
                    }
                }
                db.update(TABLE_HISTORY, cv, "$COL_ID = ?", arrayOf(id.toString()))
                return
            }
        }

        // Insert new visit record
        val cv = ContentValues().apply {
            put(COL_URL, url)
            put(COL_TITLE, cleanTitle)
            put(COL_DOMAIN, domain)
            put(COL_TIMESTAMP, now)
            put(COL_VISIT_COUNT, 1)
        }
        db.insert(TABLE_HISTORY, null, cv)
    }

    @Synchronized
    fun getRecentHistory(limit: Int = 100): List<HistoryEntry> {
        val list = mutableListOf<HistoryEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    HistoryEntry(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        url = it.getString(it.getColumnIndexOrThrow(COL_URL)),
                        title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                        domain = it.getString(it.getColumnIndexOrThrow(COL_DOMAIN)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        visitCount = it.getInt(it.getColumnIndexOrThrow(COL_VISIT_COUNT))
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }
}
