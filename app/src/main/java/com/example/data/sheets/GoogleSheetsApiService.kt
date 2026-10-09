package com.example.data.sheets

import retrofit2.Response
import retrofit2.http.*

interface GoogleSheetsApiService {

    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateSpreadsheetRequest
    ): Response<CreateSpreadsheetResponse>

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: ValueRange
    ): Response<AppendValuesResponse>

    @GET("v4/spreadsheets/{spreadsheetId}")
    suspend fun getSpreadsheet(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String
    ): Response<Map<String, Any>>
}
