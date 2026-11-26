package com.nhom.challengealarm

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val MAX_CHALLENGES_PER_ALARM = 5
const val MAX_SNOOZE_LIMIT = 3
const val MAX_ROUNDS = 10
const val MAX_BREAK_MINUTES = 2

/**
 * Các loại thử thách.
 * Có sẵn trong app, không cần đăng ký: MATH, SHAKE, STEPS, TYPING, MEMORY, ORDER, RHYTHM, PATTERN, WORDS, CATCH,
 * STROOP (màu chữ), PAIRS (lật cặp), LIGHT (bật đèn, cảm biến ánh sáng), ARROWS (vuốt mũi tên),
 * ODD (tìm ô khác màu), DIGITS (nhớ dãy số), SLIDE (xếp hình trượt), TILT (giữ thăng bằng, gia tốc kế),
 * COMPARE (so sánh nhanh), CLOCK (đọc đồng hồ).
 * QR: quét đúng mã đã đăng ký. PHOTO: hướng camera vào đúng loại vật đã đăng ký (người dùng tự thêm).
 */
enum class ChallengeType {
    MATH, QR, PHOTO, SHAKE, STEPS, TYPING, MEMORY, ORDER, RHYTHM, PATTERN, WORDS, CATCH,
    STROOP, PAIRS, LIGHT, ARROWS,
    ODD, DIGITS, SLIDE, TILT, COMPARE, CLOCK,
}

/**
 * Thử thách "trí não": làm ngay trên màn hình, có thể vẫn nằm trên giường.
 * Sau những màn này chuông được nghỉ 1 đến 2 phút. Thử thách vận động (lắc, đi bộ, quét mã, chụp vật)
 * đã bắt người dùng ra khỏi giường nên không nghỉ, tránh nằm xuống ngủ tiếp.
 */
val ChallengeType.isMental: Boolean
    get() = when (this) {
        ChallengeType.SHAKE, ChallengeType.STEPS, ChallengeType.QR, ChallengeType.PHOTO, ChallengeType.LIGHT -> false
        else -> true
    }

/** Thử thách có sẵn. id 0 = toán, id âm = các thử thách khác; id dương dành cho thử thách người dùng tự thêm. */
data class BuiltInChallenge(val id: Int, val type: ChallengeType, val name: String, val description: String)

object BuiltIn {
    val all = listOf(
        BuiltInChallenge(0, ChallengeType.MATH, "Giải toán", "Giải vài bài toán, chỉnh được số bài"),
        BuiltInChallenge(-1, ChallengeType.SHAKE, "Lắc điện thoại", "Lắc mạnh đủ số lần"),
        BuiltInChallenge(-2, ChallengeType.STEPS, "Đi bộ", "Đứng dậy và đi đủ số bước"),
        BuiltInChallenge(-3, ChallengeType.TYPING, "Gõ lại câu", "Gõ đúng nguyên câu hiện trên màn hình"),
        BuiltInChallenge(-4, ChallengeType.MEMORY, "Nhớ dãy màu", "Nhìn dãy ô sáng rồi chạm lại đúng thứ tự"),
        BuiltInChallenge(-5, ChallengeType.ORDER, "Chạm số theo thứ tự", "Chạm các số từ nhỏ đến lớn"),
        BuiltInChallenge(-6, ChallengeType.RHYTHM, "Bắt nhịp", "Chạm đúng lúc chấm sáng vào vùng vàng"),
        BuiltInChallenge(-7, ChallengeType.PATTERN, "Vẽ lại hình", "Nhớ đường đi rồi vuốt lại qua các chấm"),
        BuiltInChallenge(-8, ChallengeType.WORDS, "Xếp chữ", "Ghép các chữ cái xáo trộn thành từ"),
        BuiltInChallenge(-9, ChallengeType.CATCH, "Bắt nắng", "Chạm mặt trời nhỏ trước khi nó biến mất"),
        BuiltInChallenge(-10, ChallengeType.STROOP, "Màu chữ", "Chọn đúng màu mực, đừng để chữ đánh lừa"),
        BuiltInChallenge(-11, ChallengeType.PAIRS, "Lật cặp", "Lật thẻ, tìm các cặp hình giống nhau"),
        BuiltInChallenge(-12, ChallengeType.LIGHT, "Bật đèn", "Bật đèn hoặc mở rèm, giữ máy ở chỗ sáng"),
        BuiltInChallenge(-13, ChallengeType.ARROWS, "Vuốt mũi tên", "Vuốt theo mũi tên, mũi tên đỏ thì vuốt ngược"),
        BuiltInChallenge(-14, ChallengeType.ODD, "Tìm ô khác màu", "Chạm ô có màu hơi khác các ô còn lại"),
        BuiltInChallenge(-15, ChallengeType.DIGITS, "Nhớ dãy số", "Nhìn dãy số vài giây rồi bấm lại cho đúng"),
        BuiltInChallenge(-16, ChallengeType.SLIDE, "Xếp hình trượt", "Trượt các ô số về đúng thứ tự 1 đến 8"),
        BuiltInChallenge(-17, ChallengeType.TILT, "Giữ thăng bằng", "Nghiêng máy đưa viên bi vào vòng và giữ yên"),
        BuiltInChallenge(-18, ChallengeType.COMPARE, "So sánh nhanh", "Chọn phép tính có kết quả lớn hơn"),
        BuiltInChallenge(-19, ChallengeType.CLOCK, "Đọc đồng hồ", "Nhìn đồng hồ kim, chọn đúng giờ"),
    )

    const val STEPS_ID = -2

    fun byId(id: Int): BuiltInChallenge? = all.firstOrNull { it.id == id }
}

/** Thử thách được chọn khi chuông reo (đã quy về một dạng chung). */
data class ActiveChallenge(val type: ChallengeType, val name: String, val param: String)

/** Cách người dùng tắt chuông, lưu trong nhật ký thức dậy. */
object WakeMethod {
    const val MATH = "MATH"
    const val QR = "QR"
    const val PHOTO = "PHOTO"
    const val SHAKE = "SHAKE"
    const val STEPS = "STEPS"
    const val TYPING = "TYPING"
    const val MEMORY = "MEMORY"
    const val ORDER = "ORDER"
    const val RHYTHM = "RHYTHM"
    const val PATTERN = "PATTERN"
    const val WORDS = "WORDS"
    const val CATCH = "CATCH"
    const val STROOP = "STROOP"
    const val PAIRS = "PAIRS"
    const val LIGHT = "LIGHT"
    const val ARROWS = "ARROWS"
    const val ODD = "ODD"
    const val DIGITS = "DIGITS"
    const val SLIDE = "SLIDE"
    const val TILT = "TILT"
    const val COMPARE = "COMPARE"
    const val CLOCK = "CLOCK"
    /** Đã qua ít nhất 1 màn rồi giữ nút "Bạn đã tỉnh" để tắt luôn. */
    const val AWAKE = "AWAKE"
    const val EMERGENCY = "EMERGENCY"
}

/**
 * Một thử thách do người dùng tự thêm vào thư viện.
 * QR: param = nội dung mã đã quét. PHOTO: param = nhãn ML Kit (ví dụ "Sink", "Bottle").
 */
@Entity(tableName = "challenge_items")
data class ChallengeItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val name: String,
    val param: String,
) {
    val kind: ChallengeType get() = runCatching { ChallengeType.valueOf(type) }.getOrDefault(ChallengeType.MATH)
}

/**
 * daysMask: bit 0 = Thứ 2 ... bit 6 = Chủ nhật. 0 = báo một lần.
 * mathDifficulty / mathCount: cấu hình cho thử thách toán.
 * challengeIds: danh sách id thử thách (tối đa 5) cách nhau bằng dấu phẩy; id 0 = giải toán.
 * Khi chuông reo, app chọn ngẫu nhiên 1 thử thách trong danh sách. Rỗng = giải toán.
 * snoozeLimit: số lần được hoãn chuông (0 = không cho hoãn). ringtoneUri: rỗng = nhạc báo thức mặc định.
 * rounds: số màn phải qua (1 đến 10), mỗi màn random 1 thử thách.
 * breakMinutes: sau mỗi màn trí não, chuông im bao nhiêu phút rồi reo lại (0, 1 hoặc 2).
 * skipUntil: "Bỏ qua lần reo tới". Mọi lần reo đến hết thời điểm này bị bỏ qua (0 = không bỏ). Chỉ bỏ một lần,
 *   sau đó chuông tự reo lại như cũ, nên không phải tắt chuông lặp rồi quên bật lại.
 * wakeCheckMin: tắt chuông xong bao nhiêu phút thì hỏi "Bạn còn thức không?" (0 = không hỏi). Xem WakeCheck.
 * nap: chuông "Chợp mắt" (ngủ trưa). Reo một lần rồi tự xóa, không tính vào thống kê dậy sáng và giấc ngủ đêm.
 * skipHolidays: chuông lặp không reo vào ngày lễ Việt Nam (cả Tết âm lịch) và kỳ nghỉ người dùng thêm (Holidays.kt).
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val mathDifficulty: Int = 1,
    val mathCount: Int = 3,
    @ColumnInfo(defaultValue = "''") val challengeIds: String = "",
    @ColumnInfo(defaultValue = "0") val snoozeLimit: Int = 0,
    @ColumnInfo(defaultValue = "''") val ringtoneUri: String = "",
    @ColumnInfo(defaultValue = "1") val rounds: Int = 3,
    @ColumnInfo(defaultValue = "1") val breakMinutes: Int = 1,
    @ColumnInfo(defaultValue = "0") val skipUntil: Long = 0L,
    @ColumnInfo(defaultValue = "0") val wakeCheckMin: Int = 5,
    @ColumnInfo(defaultValue = "0") val nap: Boolean = false,
    @ColumnInfo(defaultValue = "0") val skipHolidays: Boolean = false,
) {
    fun challengeIdList(): List<Int> =
        challengeIds.split(',').mapNotNull { it.trim().toIntOrNull() }
}

/**
 * Mỗi lần tắt chuông thành công (hoặc thoát khẩn cấp) ghi một dòng. firedAt = lúc chuông bắt đầu reo.
 * wokeAt = lúc qua xong màn đầu tiên (lúc đó người dùng đã tỉnh); 0 ở dữ liệu cũ.
 * rounds = số màn đã qua.
 * relapsed = tắt chuông xong lại ngủ tiếp: không trả lời "Bạn còn thức không?" nên chuông phải reo lại.
 */
@Entity(tableName = "wake_logs")
data class WakeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alarmId: Int,
    val label: String,
    val firedAt: Long,
    val dismissedAt: Long,
    val method: String,
    @ColumnInfo(defaultValue = "0") val wokeAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val rounds: Int = 1,
    @ColumnInfo(defaultValue = "0") val relapsed: Boolean = false,
) {
    /** Thời điểm tính là đã tỉnh: xong màn đầu, hoặc lúc tắt chuông với dữ liệu cũ. */
    val awakeAt: Long get() = if (wokeAt > 0) wokeAt else dismissedAt
}

/**
 * Một đêm ngủ, tính từ lúc màn hình tắt đủ lâu đến lúc chuông reo.
 * nightOf = 0h của buổi sáng thức dậy (mỗi buổi sáng chỉ một dòng).
 */
@Entity(tableName = "sleep_logs", indices = [Index(value = ["nightOf"], unique = true)])
data class SleepLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nightOf: Long,
    val sleepStart: Long,
    val wakeAt: Long,
    val asleepMin: Int,
    val awakeMin: Int,
    val interruptions: Int,
) {
    fun toNight() = SleepNight(sleepStart, wakeAt, asleepMin * 60_000L, awakeMin * 60_000L, interruptions)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE alarms SET skipUntil = :until WHERE id = :id")
    suspend fun setSkipUntil(id: Int, until: Long)

    @Query("SELECT * FROM alarms WHERE nap = 1")
    suspend fun getNaps(): List<AlarmEntity>
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge_items ORDER BY id")
    fun observeAll(): Flow<List<ChallengeItem>>

    @Query("SELECT * FROM challenge_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<ChallengeItem>

    @Insert
    suspend fun insert(item: ChallengeItem): Long

    @Query("DELETE FROM challenge_items WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface WakeLogDao {
    @Insert
    suspend fun insert(log: WakeLog): Long

    @Query("SELECT * FROM wake_logs WHERE firedAt >= :since ORDER BY firedAt DESC")
    fun observeSince(since: Long): Flow<List<WakeLog>>

    @Query("SELECT * FROM wake_logs WHERE firedAt >= :since ORDER BY firedAt")
    suspend fun getSince(since: Long): List<WakeLog>

    /** Đánh dấu lần dậy này là "ngủ lại" (không trả lời kiểm tra đã tỉnh). */
    @Query("UPDATE wake_logs SET relapsed = 1 WHERE id = :id")
    suspend fun markRelapsed(id: Long)

    /** Xóa nhật ký của một chuông (dọn dữ liệu thử của bản cũ, alarmId = DemoData.ALARM_ID). */
    @Query("DELETE FROM wake_logs WHERE alarmId = :alarmId")
    suspend fun deleteByAlarm(alarmId: Int)
}

@Dao
interface SleepLogDao {
    /** Mỗi buổi sáng một dòng; ghi trùng thì bỏ qua. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: SleepLog): Long

    @Query("SELECT * FROM sleep_logs WHERE nightOf >= :since ORDER BY nightOf DESC")
    fun observeSince(since: Long): Flow<List<SleepLog>>

    @Query("SELECT nightOf FROM sleep_logs WHERE nightOf >= :since")
    suspend fun nightsSince(since: Long): List<Long>

    @Query("SELECT * FROM sleep_logs WHERE nightOf >= :since ORDER BY nightOf DESC")
    suspend fun getSince(since: Long): List<SleepLog>

    @Query("DELETE FROM sleep_logs WHERE nightOf IN (:nights)")
    suspend fun deleteNights(nights: List<Long>)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN challengeIds TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS challenge_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, name TEXT NOT NULL, param TEXT NOT NULL)"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN snoozeLimit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE alarms ADD COLUMN ringtoneUri TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS wake_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "alarmId INTEGER NOT NULL, label TEXT NOT NULL, " +
                "firedAt INTEGER NOT NULL, dismissedAt INTEGER NOT NULL, method TEXT NOT NULL)"
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN rounds INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE alarms ADD COLUMN breakMinutes INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE wake_logs ADD COLUMN wokeAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wake_logs ADD COLUMN rounds INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sleep_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "nightOf INTEGER NOT NULL, sleepStart INTEGER NOT NULL, wakeAt INTEGER NOT NULL, " +
                "asleepMin INTEGER NOT NULL, awakeMin INTEGER NOT NULL, interruptions INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sleep_logs_nightOf ON sleep_logs (nightOf)")
    }
}

/** v5: bỏ qua lần reo tới, kiểm tra đã tỉnh, chuông chợp mắt, đánh dấu "ngủ lại". */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN skipUntil INTEGER NOT NULL DEFAULT 0")
        // Chuông cũ không tự bật kiểm tra đã tỉnh (người dùng bật trong trình sửa chuông); chuông mới mặc định 5 phút.
        db.execSQL("ALTER TABLE alarms ADD COLUMN wakeCheckMin INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE alarms ADD COLUMN nap INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wake_logs ADD COLUMN relapsed INTEGER NOT NULL DEFAULT 0")
    }
}

/** v6: chuông không reo ngày lễ, ngày nghỉ (chuông cũ giữ nguyên như trước: vẫn reo). */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN skipHolidays INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [AlarmEntity::class, ChallengeItem::class, WakeLog::class, SleepLog::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun wakeLogDao(): WakeLogDao
    abstract fun sleepLogDao(): SleepLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alarms.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
