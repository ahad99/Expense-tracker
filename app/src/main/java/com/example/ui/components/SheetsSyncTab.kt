package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BudgetEntity
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SheetsSyncTab(
    budget: BudgetEntity,
    unsyncedCount: Int,
    isSyncing: Boolean,
    syncMessage: String?,
    onSaveScriptUrl: (scriptUrl: String) -> Unit,
    onSaveOAuthConfig: (spreadsheetId: String, googleToken: String, clientId: String, clientSecret: String) -> Unit,
    onCreateSheet: (title: String, token: String, clientId: String, clientSecret: String) -> Unit,
    onSyncNow: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scriptUrlInput by remember(budget.scriptUrl) { mutableStateOf(budget.scriptUrl) }
    var spreadsheetIdInput by remember(budget.spreadsheetId) { mutableStateOf(budget.spreadsheetId) }
    var tokenInput by remember(budget.googleToken, budget.refreshToken) {
        mutableStateOf(if (budget.refreshToken.isNotBlank()) budget.refreshToken else budget.googleToken)
    }
    var clientIdInput by remember(budget.clientId) { mutableStateOf(budget.clientId) }
    var clientSecretInput by remember(budget.clientSecret) { mutableStateOf(budget.clientSecret) }
    var showLegacyOAuth by remember { mutableStateOf(budget.spreadsheetId.isNotBlank()) }
    var showScriptCode by remember { mutableStateOf(true) }

    val clipboardManager = LocalClipboardManager.current
    var copiedScriptCode by remember { mutableStateOf(false) }

    val appsScriptCode = """
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    
    // Create header row if sheet is empty
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["ID", "Date", "Title", "Category", "Amount (৳)", "Note"]);
      sheet.getRange(1, 1, 1, 6).setFontWeight("bold").setBackground("#4CAF50").setFontColor("#FFFFFF");
    }
    
    if (data.action === "ping") {
      return ContentService.createTextOutput(JSON.stringify({ status: "success", message: "Connected!" }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    if (data.expenses && Array.isArray(data.expenses)) {
      data.expenses.forEach(function(item) {
        var nextRow = sheet.getLastRow() + 1;
        sheet.appendRow([
          item.id || "",
          item.date || "",
          item.title || "",
          item.category || "",
          item.amount || 0,
          item.note || ""
        ]);
        // Force Date column (Column B) to Plain Text so Google Sheets shows the exact date string without 1900-01-01 reformatting
        sheet.getRange(nextRow, 2).setNumberFormat("@").setValue(String(item.date || ""));
      });
      return ContentService.createTextOutput(JSON.stringify({ status: "success", count: data.expenses.length }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "No expenses provided" }))
      .setMimeType(ContentService.MimeType.JSON);
      
  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
function doGet(e) {
  return ContentService.createTextOutput("Expense Tracker Web App is Active!");
}
    """.trimIndent()

    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Cloud Sync",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Google Sheets Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    budget.scriptUrl.isNotBlank() -> "Connected via Apps Script Web App"
                                    budget.spreadsheetId.isNotBlank() -> "Connected via Direct OAuth API"
                                    else -> "Not Connected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (unsyncedCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Text("$unsyncedCount Pending", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else {
                        Badge(
                            containerColor = EmeraldPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("All Synced", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Last Synced:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (budget.lastSyncTime > 0) dateFormat.format(Date(budget.lastSyncTime)) else "Never",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Sync Now Button
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_now_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync $unsyncedCount Items Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!syncMessage.isNullOrBlank()) {
            Snackbar(
                action = {
                    TextButton(onClick = onClearMessage) {
                        Text("OK")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(syncMessage)
            }
        }

        // Card 1: Google Apps Script Web App (RECOMMENDED)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1. Apps Script Web App (Recommended)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "No complex OAuth or expiring tokens required! Simply paste your Google Apps Script Web App URL below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = scriptUrlInput,
                    onValueChange = { scriptUrlInput = it },
                    label = { Text("Google Apps Script Web App URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apps_script_url_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { onSaveScriptUrl(scriptUrlInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_script_url_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Apps Script URL")
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Instruction & Apps Script Code Box
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📖 3-Step Apps Script Setup Guide:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. Open your Google Sheet ➔ Click Extensions ➔ Apps Script.\n" +
                                   "2. Copy the script below, paste it into `Code.gs`, and click Save (💾).\n" +
                                   "3. Click Deploy ➔ New deployment ➔ Select type 'Web app'.\n" +
                                   "4. ⚠️ CRITICAL: Set 'Who has access' to 'Anyone' (so the app can send data without OAuth errors).\n" +
                                   "5. Click Deploy, copy the Web app URL, and paste it in the box above!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Google Apps Script Code:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(appsScriptCode))
                                copiedScriptCode = true
                            }) {
                                Icon(
                                    imageVector = if (copiedScriptCode) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy Script Code",
                                    tint = if (copiedScriptCode) EmeraldPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Code Container Box
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = appsScriptCode,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Optional Legacy OAuth API Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = { showLegacyOAuth = !showLegacyOAuth },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Legacy Direct OAuth API (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            if (showLegacyOAuth) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }

                if (showLegacyOAuth) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = spreadsheetIdInput,
                            onValueChange = { spreadsheetIdInput = it },
                            label = { Text("Google Spreadsheet ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Access Token or Refresh Token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedButton(
                            onClick = { onSaveOAuthConfig(spreadsheetIdInput, tokenInput, clientIdInput, clientSecretInput) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Direct OAuth Config")
                        }
                    }
                }
            }
        }
    }
}

