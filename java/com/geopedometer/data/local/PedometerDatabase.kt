package com.geopedometer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.geopedometer.data.local.dao.StepDao
import com.geopedometer.data.local.entity.DailyStepSummaryEntity
import com.geopedometer.data.local.entity.RawStepEventEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [DailyStepSummaryEntity::class, RawStepEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PedometerDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile private var INSTANCE: PedometerDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): PedometerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): PedometerDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                PedometerDatabase::class.java,
                "pedometer_encrypted.db"
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
