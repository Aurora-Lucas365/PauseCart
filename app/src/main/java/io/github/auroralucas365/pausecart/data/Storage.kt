package io.github.auroralucas365.pausecart.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.*
import io.github.auroralucas365.pausecart.core.Mode
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "items")
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val cents: Long? = null, val link: String = "", val source: String = "手动记录",
    val createdAt: Long = System.currentTimeMillis(), val dueAt: Long,
    val status: String = "WAITING", val feeling: String = ""
)
@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC") fun observe(): Flow<List<SavedItem>>
    @Query("SELECT * FROM items WHERE id = :id") suspend fun get(id: Long): SavedItem?
    @Insert suspend fun insert(item: SavedItem): Long
    @Update suspend fun update(item: SavedItem)
    @Query("DELETE FROM items WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM items") suspend fun deleteAll()
}
@Database(entities = [SavedItem::class], version = 1, exportSchema = false)
abstract class CartDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    companion object {
        @Volatile private var instance: CartDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CartDatabase::class.java, "pausecart.db")
                .build().also { instance = it }
        }
    }
}

class Preferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var consent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(value) { prefs.edit().putBoolean("consent", value).apply() }
    var reminders: Boolean
        get() = prefs.getBoolean("reminders", false)
        set(value) { prefs.edit().putBoolean("reminders", value).apply() }
    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) { prefs.edit().putBoolean("paused", value).apply() }
    var lastDismissed: Long
        get() = prefs.getLong("dismissed", 0)
        set(value) { prefs.edit().putLong("dismissed", value).apply() }
    fun mode(pkg: String): Mode = runCatching { Mode.valueOf(prefs.getString("mode_$pkg", "OFF")!!) }.getOrDefault(Mode.OFF)
    fun setMode(pkg: String, mode: Mode) { prefs.edit().putString("mode_$pkg", mode.name).apply() }
    fun passUntil(pkg: String) = prefs.getLong("pass_$pkg", 0)
    fun pass(pkg: String) { prefs.edit().putLong("pass_$pkg", System.currentTimeMillis() + 15 * 60_000).apply() }
    fun clearPasses() { prefs.edit().also { editor -> prefs.all.keys.filter { it.startsWith("pass_") }.forEach(editor::remove) }.apply() }
    fun reset() { prefs.edit().clear().apply() }
    fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}
