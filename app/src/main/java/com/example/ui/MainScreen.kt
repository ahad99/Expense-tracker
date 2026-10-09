package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ExpenseEntity
import com.example.ui.components.*
import java.util.Locale

enum class ExpenseTab(val title: String) {
    DASHBOARD("Dashboard"),
    EXPENSES("Expenses"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ExpenseViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(ExpenseTab.DASHBOARD) }
    var showAddDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Expense Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    if (uiState.unsyncedCount > 0) {
                        IconButton(
                            onClick = { viewModel.syncWithGoogleSheets() },
                            modifier = Modifier.testTag("top_bar_sync_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge { Text(uiState.unsyncedCount.toString()) }
                                }
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Sync to Google Sheets")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == ExpenseTab.DASHBOARD,
                    onClick = { selectedTab = ExpenseTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("tab_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == ExpenseTab.EXPENSES,
                    onClick = { selectedTab = ExpenseTab.EXPENSES },
                    icon = { Icon(Icons.Default.List, contentDescription = "Expenses") },
                    label = { Text("Expenses") },
                    modifier = Modifier.testTag("tab_expenses")
                )
                NavigationBarItem(
                    selected = selectedTab == ExpenseTab.SETTINGS,
                    onClick = { selectedTab = ExpenseTab.SETTINGS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.unsyncedCount > 0) {
                                    Badge { Text(uiState.unsyncedCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    expenseToEdit = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_expense_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                ExpenseTab.DASHBOARD -> DashboardContent(
                    uiState = uiState,
                    onUpdateBudget = { viewModel.updateBudgetLimit(it) },
                    onViewAllExpenses = { selectedTab = ExpenseTab.EXPENSES },
                    onEditExpense = {
                        expenseToEdit = it
                        showAddDialog = true
                    },
                    onDeleteExpense = { viewModel.deleteExpense(it) }
                )
                ExpenseTab.EXPENSES -> ExpenseListSection(
                    expenses = uiState.expenses,
                    selectedCategory = uiState.filterCategory,
                    searchQuery = uiState.searchQuery,
                    customCategories = uiState.budget.customCategories,
                    onCategoryFilterSelect = { viewModel.setFilterCategory(it) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onEditExpense = {
                        expenseToEdit = it
                        showAddDialog = true
                    },
                    onDeleteExpense = { viewModel.deleteExpense(it) }
                )
                ExpenseTab.SETTINGS -> SettingsContent(
                    budget = uiState.budget,
                    totalSpentAllTime = uiState.totalSpentAllTime,
                    totalSpentThisMonth = uiState.totalSpentThisMonth,
                    unsyncedCount = uiState.unsyncedCount,
                    isSyncing = uiState.isSyncing,
                    syncMessage = uiState.syncMessage,
                    onUpdateBudget = { viewModel.updateBudgetLimit(it) },
                    onAddCustomCategory = { viewModel.addCustomCategory(it) },
                    onRemoveCustomCategory = { viewModel.removeCustomCategory(it) },
                    onSaveScriptUrl = { viewModel.saveAppsScriptUrl(it) },
                    onSaveConfig = { id, token, cid, csecret ->
                        val isRefresh = token.startsWith("1//")
                        viewModel.saveSheetsConfig(
                            spreadsheetId = id,
                            googleToken = if (isRefresh) "" else token,
                            refreshToken = if (isRefresh) token else "",
                            clientId = cid,
                            clientSecret = csecret
                        )
                    },
                    onCreateSheet = { title, token, cid, csecret ->
                        val isRefresh = token.startsWith("1//")
                        viewModel.createGoogleSheet(
                            sheetTitle = title,
                            token = if (isRefresh) "" else token,
                            refreshToken = if (isRefresh) token else ""
                        )
                    },
                    onSyncNow = { viewModel.syncWithGoogleSheets() },
                    onClearMessage = { viewModel.clearSyncMessage() }
                )
            }
        }
    }

    if (showAddDialog) {
        AddEditExpenseDialog(
            expenseToEdit = expenseToEdit,
            customCategories = uiState.budget.customCategories,
            onAddCustomCategory = { viewModel.addCustomCategory(it) },
            onDismiss = { showAddDialog = false },
            onSave = { title, amount, category, date, note ->
                if (expenseToEdit == null) {
                    viewModel.addExpense(title, amount, category, date, note)
                } else {
                    viewModel.updateExpense(
                        expenseToEdit!!.copy(
                            title = title,
                            amount = amount,
                            category = category,
                            date = date,
                            note = note
                        )
                    )
                }
            }
        )
    }
}

@Composable
fun DashboardContent(
    uiState: ExpenseUiState,
    onUpdateBudget: (Double) -> Unit,
    onViewAllExpenses: () -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Budget Limit Card
        BudgetLimitCard(
            monthlyLimit = uiState.budget.monthlyLimit,
            totalSpent = uiState.totalSpentThisMonth,
            onUpdateBudget = onUpdateBudget
        )

        // Category Donut Chart
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Category Breakdown (This Month)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                CategoryDonutChart(
                    categoryBreakdown = uiState.categoryBreakdown,
                    customCategories = uiState.budget.customCategories
                )
            }
        }

        // Daily Bar Chart
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Daily Spending Trend (Past 7 Days)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                DailyBarChart(dailyData = uiState.dailyTrend)
            }
        }

        // Recent Expenses Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onViewAllExpenses) {
                Text("View All")
            }
        }

        if (uiState.expenses.isEmpty()) {
            Text(
                text = "No expenses recorded yet. Tap + to add one!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            uiState.expenses.take(4).forEach { expense ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = expense.title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = expense.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "৳%.2f", expense.amount),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    budget: com.example.data.BudgetEntity,
    totalSpentAllTime: Double,
    totalSpentThisMonth: Double,
    unsyncedCount: Int,
    isSyncing: Boolean,
    syncMessage: String?,
    onUpdateBudget: (Double) -> Unit,
    onAddCustomCategory: (String) -> Unit,
    onRemoveCustomCategory: (String) -> Unit,
    onSaveScriptUrl: (String) -> Unit,
    onSaveConfig: (spreadsheetId: String, googleToken: String, clientId: String, clientSecret: String) -> Unit,
    onCreateSheet: (title: String, token: String, clientId: String, clientSecret: String) -> Unit,
    onSyncNow: () -> Unit,
    onClearMessage: () -> Unit
) {
    var editText by remember(budget.monthlyLimit) { mutableStateOf(budget.monthlyLimit.toString()) }
    var newCategoryInput by remember { mutableStateOf("") }
    val customCategoryList = remember(budget.customCategories) {
        budget.customCategories.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Monthly Budget Goal Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Monthly Budget Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Monthly Limit (৳)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val parsed = editText.toDoubleOrNull()
                        if (parsed != null && parsed >= 0) {
                            onUpdateBudget(parsed)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Budget Limit")
                }
            }
        }

        // 2. Custom Categories Management Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Custom Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (customCategoryList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        customCategoryList.forEach { catName ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(catName) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onRemoveCustomCategory(catName) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $catName",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No custom categories added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        placeholder = { Text("New Category Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (newCategoryInput.isNotBlank()) {
                                onAddCustomCategory(newCategoryInput)
                                newCategoryInput = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        // 3. Google Sheets Configuration Section
        SheetsSyncTab(
            budget = budget,
            unsyncedCount = unsyncedCount,
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            onSaveScriptUrl = onSaveScriptUrl,
            onSaveOAuthConfig = onSaveConfig,
            onCreateSheet = onCreateSheet,
            onSyncNow = onSyncNow,
            onClearMessage = onClearMessage
        )

        // 4. Overview Statistics
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Overview Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Spent This Month:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "৳%.2f", totalSpentThisMonth),
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Spent (All-Time):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "৳%.2f", totalSpentAllTime),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
