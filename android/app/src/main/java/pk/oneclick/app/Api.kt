package pk.oneclick.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable data class SignedIn(val user: DbUser, val token: String)

/** Everything the screens need. [Backend] runs on the phone (tests); [RemoteApi] talks to the live server. */
interface Api {
    suspend fun lookup(phone: String): DbUser?
    suspend fun signIn(phone: String, name: String): SignedIn
    suspend fun me(): Me
    suspend fun mapProviders(): List<MapProvider>
    suspend fun activeJob(): JobView?
    suspend fun createRequest(category: String, description: String, fare: Long)
    suspend fun raiseFare(id: String, newFare: Long)
    suspend fun acceptOffer(id: String, offerId: String)
    suspend fun declineOffer(id: String, offerId: String)
    suspend fun cancel(id: String)
    suspend fun setOnline(online: Boolean)
    suspend fun requests(): List<RequestView>
    suspend fun makeOffer(id: String, amount: Long)
    suspend fun advance(id: String, next: String)
    suspend fun complete(id: String, photoPath: String)
    suspend fun confirm(id: String)
    suspend fun dispute(id: String, reason: String)
    suspend fun rate(id: String, score: Int, tags: List<String>)
    suspend fun skipRating(id: String)
    suspend fun wallet(): Pair<DbWallet, List<TxnView>>
    suspend fun topup(amount: Long, tid: String)
    suspend fun withdraw(amount: Long)
    suspend fun history(): List<JobView>
    suspend fun sos()
    suspend fun registerPush(token: String) {}
}

@Serializable private data class WalletRes(val wallet: DbWallet, val txns: List<TxnView>)
@Serializable private data class JobRes(val job: JobView? = null)
@Serializable private data class LookupRes(val user: DbUser? = null)

/** HTTPS client for the Supabase Edge Function `api`. Server enforces every rule; the app only displays. */
class RemoteApi(
    private val baseUrl: String,
    private val token: () -> String?,
    private val mode: () -> ActiveMode,
) : Api {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("X-Active-Mode", mode().name)
            .apply { token()?.let { header("Authorization", "Bearer $it") } }
            .method(method, body?.let { it.toString().toRequestBody(jsonType) } ?: if (method == "GET") null else "{}".toRequestBody(jsonType))
            .build()
        val res = try { http.newCall(req).execute() } catch (e: IOException) { throw AppError("No connection. Check your internet and try again") }
        res.use { r ->
            val text = r.body?.string().orEmpty()
            val el = runCatching { json.parseToJsonElement(text) }.getOrNull()
            if (!r.isSuccessful) throw AppError(runCatching { el!!.jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }
                .getOrElse { "Server error (${r.code}). Try again" })
            el ?: throw AppError("Unexpected reply from server")
        }
    }
    private suspend fun <T> get(path: String, s: KSerializer<T>): T = json.decodeFromJsonElement(s, call("GET", path))
    private suspend fun post(path: String, body: JsonObject = buildJsonObject {}) { call("POST", path, body) }

    override suspend fun lookup(phone: String) = get("/lookup?phone=" + java.net.URLEncoder.encode(phone, "UTF-8"), LookupRes.serializer()).user
    override suspend fun signIn(phone: String, name: String) =
        json.decodeFromJsonElement(SignedIn.serializer(), call("POST", "/auth/signin", buildJsonObject { put("phone", phone); put("name", name) }))
    override suspend fun me() = get("/me", Me.serializer())
    override suspend fun mapProviders() = get("/map-providers", ListSerializer(MapProvider.serializer()))
    override suspend fun activeJob() = get("/job", JobRes.serializer()).job
    override suspend fun createRequest(category: String, description: String, fare: Long) =
        post("/requests", buildJsonObject { put("category", category); put("description", description); put("fare", fare) })
    override suspend fun raiseFare(id: String, newFare: Long) = post("/bookings/$id/raise", buildJsonObject { put("fare", newFare) })
    override suspend fun acceptOffer(id: String, offerId: String) = post("/bookings/$id/accept-offer", buildJsonObject { put("offerId", offerId) })
    override suspend fun declineOffer(id: String, offerId: String) = post("/bookings/$id/decline-offer", buildJsonObject { put("offerId", offerId) })
    override suspend fun cancel(id: String) = post("/bookings/$id/cancel")
    override suspend fun setOnline(online: Boolean) { call("PUT", "/online", buildJsonObject { put("online", online) }) }
    override suspend fun requests() = get("/requests", ListSerializer(RequestView.serializer()))
    override suspend fun makeOffer(id: String, amount: Long) = post("/bookings/$id/offer", buildJsonObject { put("amount", amount) })
    override suspend fun advance(id: String, next: String) = post("/bookings/$id/advance", buildJsonObject { put("status", next) })
    override suspend fun complete(id: String, photoPath: String) {
        val b64 = withContext(Dispatchers.IO) { Base64.getEncoder().encodeToString(File(photoPath).readBytes()) }
        post("/bookings/$id/complete", buildJsonObject { put("photoBase64", b64) })
    }
    override suspend fun confirm(id: String) = post("/bookings/$id/confirm")
    override suspend fun dispute(id: String, reason: String) = post("/bookings/$id/dispute", buildJsonObject { put("reason", reason) })
    override suspend fun rate(id: String, score: Int, tags: List<String>) =
        post("/bookings/$id/rate", buildJsonObject { put("score", score); putJsonArray("tags") { tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } } })
    override suspend fun skipRating(id: String) = rate(id, 5, emptyList())
    override suspend fun wallet() = get("/wallet", WalletRes.serializer()).let { it.wallet to it.txns }
    override suspend fun topup(amount: Long, tid: String) = post("/wallet/topup", buildJsonObject { put("amount", amount); put("tid", tid) })
    override suspend fun withdraw(amount: Long) = post("/wallet/withdraw", buildJsonObject { put("amount", amount) })
    override suspend fun history() = get("/history", ListSerializer(JobView.serializer()))
    override suspend fun sos() = post("/sos")
    override suspend fun registerPush(token: String) = post("/push-token", buildJsonObject { put("token", token) })
}
