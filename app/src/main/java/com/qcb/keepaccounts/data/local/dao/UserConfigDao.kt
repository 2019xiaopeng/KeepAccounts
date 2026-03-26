package com.qcb.keepaccounts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qcb.keepaccounts.data.local.entity.UserConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: UserConfigEntity)

    @Query("SELECT * FROM user_config WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<UserConfigEntity?>
}
