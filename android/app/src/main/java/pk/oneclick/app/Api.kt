package pk.oneclick.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// ---- DTOs (snake_case on the wire, see docs/GLOSSARY.md) ----
@Serializable data class DemoUserDto(val id: String, val name: String, val userRole: String, val categories: List<String>? = null)
@Serializable data class DemoUsersRes(val users: List<DemoUserDto>)
@Serializable data class TokenRes(val token: String)
@Serializable data class UserDto(val id: String, val name: String, val phone: String? = null, val userRole: String)
@Serializable data class WalletDto(val balance: Long, val escrowHeld: Long = 0, val totalEarned: Long = 0)
@Serializable data class ProviderInfoDto(
    val categories: List<String>, val isOnline: Boolean, val isOnJob: Boolean, val tier: String,
    val ratingAvg: Double, val totalJobs: Int, val isVerified: Boolean,
)
@Serializable data class MeRes(val user: UserDto, val wallet: WalletDto, val provider: ProviderInfoDto? = null)
@Serializable data class CategoryDto(val slug: String, val nameEn: String, val nameUr: String, val pinColor: String)
@Serializable data class CategoriesRes(val categories: List<CategoryDto>)
@Serializable data class ProviderDto(
    val id: String, val name: String, val categories: List<String>, val ratingAvg: Double, val totalJobs: Int,
    val tier: String, val isVerified: Boolean, val lat: Double, val lng: Double, val distanceKm: Double,
)
@Serializable data class NearbyRes(val providers: List<ProviderDto>, val fallback: Boolean)
@Serializable data class BookingDto(
    val id: String, val status: String, val category: String, val description: String = "",
    val agreedFare: Long, val mySide: String, val otherName: String, val otherRole: String,
    val otherRating: Double? = null, val customerLat: Double, val customerLng: Double,
    val providerLat: Double, val providerLng: Double, val etaMin: Int? = null,
    val awaitingConfirmation: Boolean = false, val needsRating: Boolean = false,
    val cancelReason: String? = null, val completionPhotoUrl: String? = null,
    val expiresInS: Int? = null, val createdAt: String = "",
)
@Serializable data class BookingRes(val booking: BookingDto? = null)
@Serializable data class BookingsRes(val bookings: List<BookingDto>)
@Serializable data class TxnDto(
    val id: String, val type: String, val amount: Long, val status: String,
    val externalRef: String? = null, val createdAt: String, val incoming: Boolean,
)
@Serializable data class WalletRes(val wallet: WalletDto, val transactions: List<TxnDto>)
@Serializable data class OkRes(val ok: Boolean = true, val message: String? = null)

@Serializable data class LoginReq(val userId: String)
@Serializable data class OnlineReq(val online: Boolean)
@Serializable data class TopupReq(val amount: Long, val tid: String)
@Serializable data class CreateBookingReq(
    val providerId: String, val category: String, val description: String, val offerFare: Long,
    val lat: Double, val lng: Double,
)
@Serializable data class StatusReq(val status: String)
@Serializable data class CompleteReq(val photoUrl: String)
@Serializable data class ConfirmReq(val ok: Boolean = true)
@Serializable data class RateReq(val score: Int)
@Serializable data class DisputeReq(val reason: String)
@Serializable data class SosReq(val bookingId: String?, val lat: Double, val lng: Double)

interface ApiService {
    @GET("demo/users") suspend fun demoUsers(): DemoUsersRes
    @POST("auth/demo") suspend fun login(@Body b: LoginReq): TokenRes
    @GET("me") suspend fun me(): MeRes
    @GET("categories") suspend fun categories(): CategoriesRes
    @GET("providers/nearby") suspend fun nearby(@Query("lat") lat: Double, @Query("lng") lng: Double, @Query("category") category: String?): NearbyRes
    @PUT("providers/online") suspend fun setOnline(@Body b: OnlineReq): OkRes
    @GET("wallet") suspend fun wallet(): WalletRes
    @POST("wallet/topup") suspend fun topup(@Body b: TopupReq): OkRes
    @GET("bookings/active") suspend fun activeBooking(): BookingRes
    @GET("bookings") suspend fun bookings(): BookingsRes
    @POST("bookings") suspend fun createBooking(@Body b: CreateBookingReq): BookingRes
    @POST("bookings/{id}/accept") suspend fun accept(@Path("id") id: String, @Body b: ConfirmReq = ConfirmReq()): BookingRes
    @POST("bookings/{id}/reject") suspend fun reject(@Path("id") id: String, @Body b: ConfirmReq = ConfirmReq()): BookingRes
    @POST("bookings/{id}/status") suspend fun status(@Path("id") id: String, @Body b: StatusReq): BookingRes
    @POST("bookings/{id}/complete") suspend fun complete(@Path("id") id: String, @Body b: CompleteReq): BookingRes
    @POST("bookings/{id}/confirm") suspend fun confirm(@Path("id") id: String, @Body b: ConfirmReq = ConfirmReq()): BookingRes
    @POST("bookings/{id}/cancel") suspend fun cancel(@Path("id") id: String, @Body b: ConfirmReq = ConfirmReq()): BookingRes
    @POST("bookings/{id}/dispute") suspend fun dispute(@Path("id") id: String, @Body b: DisputeReq): BookingRes
    @POST("bookings/{id}/rate") suspend fun rate(@Path("id") id: String, @Body b: RateReq): BookingRes
    @POST("sos") suspend fun sos(@Body b: SosReq): OkRes
}

enum class ActiveMode { CUSTOMER, PROVIDER }

/** Session (signed-in user id) kept in EncryptedSharedPreferences (Android Keystore-backed master key). */
class Session(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "session",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()
    @Volatile var mode: ActiveMode = ActiveMode.CUSTOMER
}

@OptIn(ExperimentalSerializationApi::class)
val json = Json { namingStrategy = JsonNamingStrategy.SnakeCase; ignoreUnknownKeys = true; explicitNulls = false }

fun Throwable.userMessage(): String = if (this is AppError) message ?: "Error" else "Something went wrong"

fun rs(amount: Long) = "Rs. " + "%,d".format(amount)
