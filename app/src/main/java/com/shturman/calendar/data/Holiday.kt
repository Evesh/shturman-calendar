package com.shturman.calendar.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey val id: Int,
    val name: String,
    val date: String,       // DD.MM.YYYY
    val type: Int,          // 1=state, 2=church, 3=professional
    val year: Int,
    val month: Int,
    val day: Int
)

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays WHERE year = :year AND month = :month")
    fun getHolidaysForMonth(year: Int, month: Int): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays WHERE year = :year AND month = :month")
    suspend fun getHolidaysForMonthList(year: Int, month: Int): List<Holiday>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<Holiday>)

    @Query("DELETE FROM holidays WHERE year = :year")
    suspend fun deleteYear(year: Int)

    @Query("SELECT MAX(year) FROM holidays")
    suspend fun getMaxYear(): Int?
}
