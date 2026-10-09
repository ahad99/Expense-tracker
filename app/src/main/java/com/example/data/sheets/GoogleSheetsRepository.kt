package com.example.data.sheets

import com.example.data.ExpenseEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleSheetsRepository {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val apiService: GoogleSheetsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://sheets.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleSheetsApiService::class.java)
    }

    suspend fun syncWithAppsScript(
        scriptUrl: String,
        expenses: List<ExpenseEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (expenses.isEmpty()) return@withContext Result.success(0)
        val cleanUrl = scriptUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(Exception("Please enter a valid Google Apps Script Web App URL."))
        }

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val expensesArray = org.json.JSONArray()
            expenses.forEach { exp ->
                val timeMillis = if (exp.date > 0) exp.date else System.currentTimeMillis()
                val obj = JSONObject().apply {
                    put("id", exp.id)
                    put("date", dateFormat.format(Date(timeMillis)))
                    put("title", exp.title)
                    put("category", exp.category)
                    put("amount", exp.amount)
                    put("note", exp.note)
                }
                expensesArray.put(obj)
            }

            val payload = JSONObject().apply {
                put("action", "appendExpenses")
                put("expenses", expensesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(cleanUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.code == 401 || response.code == 403 || responseBody.trim().startsWith("<!DOCTYPE", ignoreCase = true) || responseBody.trim().startsWith("<html", ignoreCase = true)) {
                return@withContext Result.failure(
                    Exception("Access Denied (HTTP ${response.code}): In Google Apps Script, click 'Deploy' -> 'New deployment' (or 'Manage deployments' -> ✏️ Edit) and ensure 'Who has access' is set to 'Anyone'.")
                )
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonResponse = try { JSONObject(responseBody) } catch (_: Exception) { null }
            if (jsonResponse != null && jsonResponse.optString("status") == "error") {
                val errorMsg = jsonResponse.optString("message", "Apps Script error")
                return@withContext Result.failure(Exception(errorMsg))
            }

            Result.success(expenses.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyAppsScriptUrl(scriptUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUrl = scriptUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(Exception("Apps Script Web App URL cannot be empty."))
        }

        try {
            val payload = JSONObject().apply {
                put("action", "ping")
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(cleanUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Connection failed (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAccessTokenFromRefreshToken(
        refreshToken: String,
        clientId: String = "",
        clientSecret: String = ""
    ): String = withContext(Dispatchers.IO) {
        val cid = clientId.trim()
        val csec = clientSecret.trim()

        if (cid.isBlank()) {
            throw Exception("Missing Client ID: Refreshing a token (1//...) requires an OAuth Client ID. Please expand 'Advanced Settings' and enter your Client ID & Secret (or paste a fresh Access Token starting with ya29...).")
        }

        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken.trim())
            .add("client_id", cid)

        if (csec.isNotBlank()) {
            formBuilder.add("client_secret", csec)
        }

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBuilder.build())
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorDetail = try {
                val json = JSONObject(responseBody)
                val desc = json.optString("error_description", "")
                if (desc.isNotBlank()) {
                    desc
                } else {
                    val errObj = json.optJSONObject("error")
                    errObj?.optString("message", responseBody) ?: responseBody
                }
            } catch (_: Exception) {
                responseBody
            }
            throw Exception("Token Refresh Error: $errorDetail")
        }

        val json = JSONObject(responseBody)
        json.getString("access_token")
    }

    suspend fun resolveValidToken(
        rawToken: String,
        refreshToken: String = "",
        clientId: String = "",
        clientSecret: String = ""
    ): String {
        val cleanToken = rawToken.trim()
        val cleanRefresh = refreshToken.trim()

        // 1. If main token input is a Refresh Token (1//...)
        if (cleanToken.startsWith("1//")) {
            return fetchAccessTokenFromRefreshToken(cleanToken, clientId, clientSecret)
        }

        // 2. If main token input has a standard Access Token (ya29...)
        if (cleanToken.isNotBlank()) {
            return cleanToken
        }

        // 3. If main token is empty, but a stored Refresh Token exists
        if (cleanRefresh.isNotBlank()) {
            return fetchAccessTokenFromRefreshToken(cleanRefresh, clientId, clientSecret)
        }

        throw Exception("No Google OAuth Token provided. Please paste an Access Token (ya29...) or Refresh Token (1//...) in Settings.")
    }

    private fun formatBearer(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) trimmed else "Bearer $trimmed"
    }

    suspend fun createNewSpreadsheet(
        token: String,
        title: String,
        refreshToken: String = "",
        clientId: String = "",
        clientSecret: String = ""
    ): Result<String> {
        return try {
            val accessToken = resolveValidToken(token, refreshToken, clientId, clientSecret)
            val bearer = formatBearer(accessToken)
            val request = CreateSpreadsheetRequest(
                properties = SpreadsheetProperties(title = title)
            )
            val response = apiService.createSpreadsheet(bearer, request)
            if (response.isSuccessful && response.body() != null) {
                val sheetId = response.body()!!.spreadsheetId
                initHeaderRow(bearer, sheetId)
                Result.success(sheetId)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to create Google Sheet: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun initHeaderRow(bearerToken: String, spreadsheetId: String) {
        try {
            val headers = listOf("Date", "Title", "Category", "Amount (৳)", "Note", "Sync ID")
            val valueRange = ValueRange(
                range = "Sheet1!A1:F1",
                values = listOf(headers)
            )
            apiService.appendValues(
                bearerToken = bearerToken,
                spreadsheetId = spreadsheetId,
                range = "Sheet1!A1",
                body = valueRange
            )
        } catch (_: Exception) {
            // Non-blocking header attempt
        }
    }

    suspend fun appendExpenses(
        spreadsheetId: String,
        token: String,
        expenses: List<ExpenseEntity>,
        refreshToken: String = "",
        clientId: String = "",
        clientSecret: String = ""
    ): Result<Int> {
        if (expenses.isEmpty()) return Result.success(0)
        return try {
            val accessToken = resolveValidToken(token, refreshToken, clientId, clientSecret)
            val bearer = formatBearer(accessToken)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            val rows = expenses.map { expense ->
                listOf(
                    dateFormat.format(Date(expense.date)),
                    expense.title,
                    expense.category,
                    String.format(Locale.US, "%.2f", expense.amount),
                    expense.note,
                    expense.id.toString()
                )
            }

            val valueRange = ValueRange(
                range = "Sheet1!A:F",
                values = rows
            )

            val response = apiService.appendValues(
                bearerToken = bearer,
                spreadsheetId = spreadsheetId.trim(),
                range = "Sheet1!A:F",
                body = valueRange
            )

            if (response.isSuccessful) {
                val updatedCount = response.body()?.updates?.updatedRows ?: expenses.size
                Result.success(updatedCount)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to sync expenses: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifySpreadsheet(
        spreadsheetId: String,
        token: String,
        refreshToken: String = "",
        clientId: String = "",
        clientSecret: String = ""
    ): Result<Boolean> {
        return try {
            val accessToken = resolveValidToken(token, refreshToken, clientId, clientSecret)
            val bearer = formatBearer(accessToken)
            val response = apiService.getSpreadsheet(bearer, spreadsheetId.trim())
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Unable to connect to sheet: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

