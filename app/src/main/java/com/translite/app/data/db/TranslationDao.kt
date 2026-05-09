package com.translite.app.data.db

import androidx.room.*
import com.translite.app.data.db.entity.TranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(translation: TranslationEntity): Long

    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<TranslationEntity>>

    @Query("UPDATE translations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM translations")
    suspend fun deleteAll()

    @Query("SELECT * FROM translations WHERE originalText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<TranslationEntity>>
}
