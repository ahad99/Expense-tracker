package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BudgetEntity
import com.example.data.ExpenseEntity
import com.example.data.sheets.GoogleSheetsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class ExpenseUiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val budget: BudgetEntity = BudgetEntity(),
    val totalSpentThisMonth: Double = 0.0,
    val totalSpentAllTime: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val dailyTrend: List<Pair<String, Double>> = emptyList(),
    val unsyncedCount: Int = 0,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val filterCategory: String? = null,
    val searchQuery: String = ""
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.expenseDao()
    private val sheetsRepo = GoogleSheetsRepository()

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    private val _filterCategory = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    init {
        // Observe expenses and budget
        viewModelScope.launch {
            combine(
                dao.getAllExpenses(),
                dao.getBudgetFlow(),
                _filterCategory,
                _searchQuery
            ) { allExpenses, budget, category, query ->
                val currentBudget = budget ?: BudgetEntity()
                
                // Calculate monthly spending
                val nowCal = Calendar.getInstance()
                val currentMonth = nowCal.get(Calendar.MONTH)
                val currentYear = nowCal.get(Calendar.YEAR)

                val monthlyExpenses = allExpenses.filter { expense ->
                    val expCal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    expCal.get(Calendar.MONTH) == currentMonth && expCal.get(Calendar.YEAR) == currentYear
                }

                val totalSpentMonth = monthlyExpenses.sumOf { it.amount }
                val totalSpentAll = allExpenses.sumOf { it.amount }

                // Category breakdown for current month
                val catMap = monthlyExpenses.groupBy { it.category }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }

                // Daily trend for the last 7 days
                val dailyList = calculateDailyTrend(allExpenses)

                // Filtered expenses list for UI
                var filtered = allExpenses
                if (!category.isNullOrBlank()) {
                    filtered = filtered.filter { it.category.equals(category, ignoreCase = true) }
                }
                if (query.isNotBlank()) {
                    filtered = filtered.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.note.contains(query, ignoreCase = true) ||
                        it.category.contains(query, ignoreCase = true)
                    }
                }

                val unsynced = allExpenses.count { !it.syncedToSheet }

                ExpenseUiState(
                    expenses = filtered,
                    budget = currentBudget,
                    totalSpentThisMonth = totalSpentMonth,
                    totalSpentAllTime = totalSpentAll,
                    categoryBreakdown = catMap,
                    dailyTrend = dailyList,
                    unsyncedCount = unsynced,
                    filterCategory = category,
                    searchQuery = query,
                    isSyncing = _uiState.value.isSyncing,
                    syncMessage = _uiState.value.syncMessage
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun calculateDailyTrend(allExpenses: List<ExpenseEntity>): List<Pair<String, Double>> {
        val days = mutableListOf<Pair<String, Double>>()
        val cal = Calendar.getInstance()
        val dayFormat = java.text.SimpleDateFormat("EEE", Locale.getDefault())

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val startOfDay = dayCal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endOfDay = dayCal.apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val dayTotal = allExpenses.filter { it.date in startOfDay..endOfDay }.sumOf { it.amount }
            val label = if (i == 0) "Today" else dayFormat.format(dayCal.time)
            days.add(label to dayTotal)
        }
        return days
    }

    fun addExpense(title: String, amount: Double, category: String, date: Long, note: String) {
        viewModelScope.launch {
            val entity = ExpenseEntity(
                title = title.ifBlank { category },
                amount = amount,
                category = category,
                date = date,
                note = note,
                syncedToSheet = false
            )
            dao.insertExpense(entity)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            dao.updateExpense(expense.copy(syncedToSheet = false))
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            dao.deleteExpense(expense)
        }
    }

    fun setFilterCategory(category: String?) {
        _filterCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addCustomCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = dao.getBudget() ?: BudgetEntity()
            val existing = current.customCategories.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (existing.none { it.equals(trimmed, ignoreCase = true) }) {
                val updatedList = existing + trimmed
                val updatedString = updatedList.joinToString(",")
                dao.saveBudget(current.copy(customCategories = updatedString))
            }
        }
    }

    fun removeCustomCategory(categoryName: String) {
        viewModelScope.launch {
            val current = dao.getBudget() ?: BudgetEntity()
            val existing = current.customCategories.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val updatedList = existing.filterNot { it.equals(categoryName.trim(), ignoreCase = true) }
            val updatedString = updatedList.joinToString(",")
            dao.saveBudget(current.copy(customCategories = updatedString))
        }
    }

    fun updateBudgetLimit(limit: Double) {
        viewModelScope.launch {
            val current = dao.getBudget() ?: BudgetEntity()
            dao.saveBudget(current.copy(monthlyLimit = limit))
        }
    }

    fun saveAppsScriptUrl(scriptUrl: String) {
        viewModelScope.launch {
            val current = dao.getBudget() ?: BudgetEntity()
            dao.saveBudget(current.copy(scriptUrl = scriptUrl.trim()))
            _uiState.update { it.copy(syncMessage = "Google Apps Script Web App URL saved successfully!") }
        }
    }

    fun saveSheetsConfig(
        spreadsheetId: String,
        googleToken: String,
        refreshToken: String = "",
        clientId: String = "",
        clientSecret: String = ""
    ) {
        viewModelScope.launch {
            val current = dao.getBudget() ?: BudgetEntity()
            dao.saveBudget(current.copy(
                spreadsheetId = spreadsheetId.trim(),
                googleToken = googleToken.trim(),
                refreshToken = refreshToken.trim(),
                clientId = clientId.trim(),
                clientSecret = clientSecret.trim()
            ))
            _uiState.update { it.copy(syncMessage = "Spreadsheet settings saved successfully!") }
        }
    }

    fun createGoogleSheet(sheetTitle: String, token: String, refreshToken: String = "") {
        if (token.isBlank() && refreshToken.isBlank()) {
            _uiState.update { it.copy(syncMessage = "Please enter a valid Google OAuth Access Token or Refresh Token first.") }
            return
        }

        viewModelScope.launch {
            val current = dao.getBudget() ?: BudgetEntity()
            _uiState.update { it.copy(isSyncing = true, syncMessage = "Creating Google Sheet...") }
            val result = sheetsRepo.createNewSpreadsheet(
                token = token,
                title = sheetTitle.ifBlank { "Daily Expense Log" },
                refreshToken = refreshToken,
                clientId = current.clientId,
                clientSecret = current.clientSecret
            )
            result.onSuccess { sheetId ->
                dao.saveBudget(current.copy(spreadsheetId = sheetId, googleToken = token, refreshToken = refreshToken))
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Google Sheet created! ID: $sheetId"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Failed to create sheet: ${formatSyncError(err.message)}"
                    )
                }
            }
        }
    }

    private fun formatSyncError(rawMsg: String?): String {
        if (rawMsg.isNullOrBlank()) return "Unknown error occurred"
        if (rawMsg.contains("401") || rawMsg.contains("UNAUTHENTICATED") || rawMsg.contains("invalid_grant") || rawMsg.contains("invalid authentication credentials", ignoreCase = true)) {
            return "Token Expired / Invalid (401): Please paste a fresh Access Token or a Refresh Token (1//...) in Settings."
        }
        if (rawMsg.contains("404") || rawMsg.contains("NOT_FOUND")) {
            return "Spreadsheet Not Found (404): Check your Spreadsheet ID or Google account permissions."
        }
        if (rawMsg.contains("403") || rawMsg.contains("PERMISSION_DENIED")) {
            return "Permission Denied (403): Ensure scope includes 'https://www.googleapis.com/auth/spreadsheets'."
        }
        return rawMsg
    }

    fun syncWithGoogleSheets() {
        viewModelScope.launch {
            val budget = dao.getBudget() ?: BudgetEntity()

            val unsynced = dao.getUnsyncedExpenses()
            if (unsynced.isEmpty()) {
                _uiState.update { it.copy(syncMessage = "All expenses are already synced!") }
                return@launch
            }

            // 1. Prefer Google Apps Script Web App sync if configured
            if (budget.scriptUrl.isNotBlank()) {
                _uiState.update { it.copy(isSyncing = true, syncMessage = "Syncing ${unsynced.size} items via Google Apps Script...") }
                val result = sheetsRepo.syncWithAppsScript(budget.scriptUrl, unsynced)
                result.onSuccess { count ->
                    val syncedIds = unsynced.map { it.id }
                    dao.markAsSynced(syncedIds)
                    val now = System.currentTimeMillis()
                    dao.saveBudget(budget.copy(lastSyncTime = now))
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncMessage = "Successfully synced $count items to Google Sheet via Apps Script!"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncMessage = "Apps Script Sync Failed: ${err.message}"
                        )
                    }
                }
                return@launch
            }

            // 2. Fallback to Direct Google Sheets OAuth API if configured
            if (budget.spreadsheetId.isBlank()) {
                _uiState.update { it.copy(syncMessage = "Please paste your Google Apps Script Web App URL (or Spreadsheet ID) first.") }
                return@launch
            }
            if (budget.googleToken.isBlank() && budget.refreshToken.isBlank()) {
                _uiState.update { it.copy(syncMessage = "Please paste your Google Apps Script Web App URL in Settings.") }
                return@launch
            }

            _uiState.update { it.copy(isSyncing = true, syncMessage = "Syncing ${unsynced.size} items to Google Sheets...") }

            val result = sheetsRepo.appendExpenses(
                spreadsheetId = budget.spreadsheetId,
                token = budget.googleToken,
                expenses = unsynced,
                refreshToken = budget.refreshToken,
                clientId = budget.clientId,
                clientSecret = budget.clientSecret
            )
            result.onSuccess { count ->
                val syncedIds = unsynced.map { it.id }
                dao.markAsSynced(syncedIds)
                val now = System.currentTimeMillis()
                dao.saveBudget(budget.copy(lastSyncTime = now))
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Successfully synced $count items to Google Sheets!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Sync failed: ${formatSyncError(err.message)}"
                    )
                }
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }
}
