package com.tulip.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: Long,
    val lastName: String,
    val lastPath: String,
    val size: Long,
    val isVideo: Boolean,
    val addedAt: Long,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "collection_items", primaryKeys = ["collectionId", "mediaId"])
data class CollectionItemEntity(val collectionId: Long, val mediaId: Long, val lastName: String)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val key: String,   // volumen:ruta/relativa/
    val label: String,
    val enabled: Boolean,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(f: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(f: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE mediaId = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM favorites WHERE mediaId IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("UPDATE favorites SET mediaId = :newId, lastPath = :path WHERE mediaId = :oldId")
    suspend fun relink(oldId: Long, newId: Long, path: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_items")
    fun observeItems(): Flow<List<CollectionItemEntity>>

    @Insert
    suspend fun insertCollection(c: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM collection_items WHERE collectionId = :id")
    suspend fun deleteItemsOf(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItems(items: List<CollectionItemEntity>)

    @Query("DELETE FROM collection_items WHERE collectionId = :cid AND mediaId = :mid")
    suspend fun removeItem(cid: Long, mid: Long)
}

@Dao
interface ExcludedDao {
    @Query("SELECT * FROM excluded_folders ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<ExcludedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("UPDATE excluded_folders SET enabled = :enabled WHERE `key` = :key")
    suspend fun setEnabled(key: String, enabled: Boolean)
}

@Database(
    entities = [FavoriteEntity::class, CollectionEntity::class, CollectionItemEntity::class, ExcludedFolderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TulipDb : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun collections(): CollectionDao
    abstract fun excluded(): ExcludedDao
}
