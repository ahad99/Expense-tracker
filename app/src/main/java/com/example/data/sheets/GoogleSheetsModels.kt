package com.example.data.sheets

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateSpreadsheetRequest(
    @Json(name = "properties") val properties: SpreadsheetProperties
)

@JsonClass(generateAdapter = true)
data class SpreadsheetProperties(
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class CreateSpreadsheetResponse(
    @Json(name = "spreadsheetId") val spreadsheetId: String,
    @Json(name = "spreadsheetUrl") val spreadsheetUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ValueRange(
    @Json(name = "range") val range: String? = null,
    @Json(name = "majorDimension") val majorDimension: String = "ROWS",
    @Json(name = "values") val values: List<List<String>>
)

@JsonClass(generateAdapter = true)
data class AppendValuesResponse(
    @Json(name = "spreadsheetId") val spreadsheetId: String? = null,
    @Json(name = "updates") val updates: UpdateData? = null
)

@JsonClass(generateAdapter = true)
data class UpdateData(
    @Json(name = "updatedRows") val updatedRows: Int? = null,
    @Json(name = "updatedCells") val updatedCells: Int? = null
)
