package com.hcwebhook.app.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.hcwebhook.app.HealthConnectManager
import com.hcwebhook.app.PreferencesManager
import com.hcwebhook.app.R
import com.hcwebhook.app.SyncManager
import com.hcwebhook.app.SyncResult
import com.hcwebhook.app.WebhookConfig
import com.hcwebhook.app.ui.SectionTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The status card at the top of Home: what the sync does, the last sync, the counts of the last
 * 24 hours, and the Sync now button. Sync now opens one sheet with the time range and the target.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncHero(
    title: String,
    subtitle: String,
    sentLast24h: Int?,
    failedLast24h: Int?,
    lastSyncTime: Long?,
    canSync: Boolean,
    onSyncCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmSheet by remember { mutableStateOf(false) }

    val webhookConfigs = preferencesManager.getWebhookConfigs()
    val enabledWebhooks = remember(webhookConfigs) { webhookConfigs.filter { it.isEnabled } }
    var selectedWebhookUrl by remember { mutableStateOf<String?>(null) }

    val timeRangeOptions = listOf(
        context.getString(R.string.manual_sync_default_range) to null,
        context.getString(R.string.manual_sync_past_1_day) to 1,
        context.getString(R.string.manual_sync_past_7_days) to 7,
        context.getString(R.string.manual_sync_past_30_days) to 30,
        context.getString(R.string.manual_sync_custom) to -1
    )
    val localDateSaver = Saver<LocalDate?, String>(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else LocalDate.parse(it) }
    )
    var selectedOptionIndex by rememberSaveable { mutableStateOf(0) }
    var startDate by rememberSaveable(stateSaver = localDateSaver) { mutableStateOf(null) }
    var endDate by rememberSaveable(stateSaver = localDateSaver) { mutableStateOf(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val isCustomRange = timeRangeOptions[selectedOptionIndex].second == -1
    val today = LocalDate.now()
    val selectedStart = startDate
    val selectedEnd = endDate
    val customRangeValid = !isCustomRange || (
        selectedStart != null && selectedEnd != null &&
            !selectedEnd.isBefore(selectedStart) && !selectedEnd.isAfter(today)
        )

    // Relative time of the last sync, localized by Android and refreshed every 30 seconds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val lastSyncText = lastSyncTime?.let {
        DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
    } ?: stringResource(R.string.home_hero_never)

    // ── Sync now sheet ───────────────────────────────────────────────────────
    if (showConfirmSheet) {
        ModalBottomSheet(onDismissRequest = { showConfirmSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.manual_sync_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    stringResource(R.string.manual_sync_confirm_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                SectionTitle(stringResource(R.string.manual_sync_time_range), Modifier.padding(top = 8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    timeRangeOptions.forEachIndexed { index, option ->
                        FilterChip(
                            selected = index == selectedOptionIndex,
                            onClick = { selectedOptionIndex = index },
                            label = { Text(option.first) },
                        )
                    }
                }
                AnimatedVisibility(visible = isCustomRange, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 8.dp).padding(top = 4.dp),
                    ) {
                        OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(
                                startDate?.toString() ?: stringResource(R.string.manual_sync_start_date),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(
                                endDate?.toString() ?: stringResource(R.string.manual_sync_end_date),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (isCustomRange && selectedStart != null && selectedEnd != null && selectedEnd < selectedStart) {
                    Text(
                        stringResource(R.string.err_end_date_before_start),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (enabledWebhooks.size >= 2) {
                    SectionTitle(stringResource(R.string.manual_sync_send_to), Modifier.padding(top = 8.dp))
                    Column {
                        (listOf<WebhookConfig?>(null) + enabledWebhooks).forEach { config ->
                            val label = config?.let {
                                remember(it.url) {
                                    try { java.net.URI(it.url).host?.takeIf { host -> host.isNotEmpty() } ?: it.url }
                                    catch (_: Exception) { it.url }
                                }
                            } ?: stringResource(R.string.manual_sync_all_webhooks)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedWebhookUrl = config?.url },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedWebhookUrl == config?.url, onClick = { selectedWebhookUrl = config?.url })
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        showConfirmSheet = false
                        if (isSyncing) return@Button

                scope.launch {
                    isSyncing = true
                    syncMessage = null

                    try {
                        val availability = HealthConnectClient.getSdkStatus(context)
                        if (availability != HealthConnectClient.SDK_AVAILABLE) {
                            syncMessage = when (availability) {
                                HealthConnectClient.SDK_UNAVAILABLE -> context.getString(R.string.err_hc_not_installed)
                                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> context.getString(R.string.err_hc_needs_update)
                                else -> context.getString(R.string.err_hc_not_available)
                            }
                            isSyncing = false
                            return@launch
                        }

                        val healthConnectManager = HealthConnectManager(context)
                        val enabledTypes = preferencesManager.getEnabledDataTypes()
                        val requiredPermissions = HealthConnectManager.getPermissionsForTypes(
                            enabledTypes,
                            includeBackgroundPermission = false,
                            // The manual-sync gate should only require read access to the
                            // enabled data types. READ_HEALTH_DATA_HISTORY is only needed for
                            // ranges older than 30 days, and READ_STEPS_CADENCE is optional
                            // (its aggregate read is already try/catch-guarded). Requiring
                            // either here left users stuck on "Permissions required for sync"
                            // with no in-app way to grant them.
                            includeHistoryPermission = false,
                            includeStepsCadence = false
                        )
                        if (requiredPermissions.isNotEmpty() && !healthConnectManager.hasPermissions(requiredPermissions)) {
                            syncMessage = context.getString(R.string.err_permissions_required)
                            isSyncing = false
                            return@launch
                        }

                        val syncManager = SyncManager(context)
                        val timeRangeSelection = timeRangeOptions[selectedOptionIndex].second

                        val targetWebhooks: List<WebhookConfig>? = selectedWebhookUrl?.let { url ->
                            enabledWebhooks.filter { it.url == url }
                        }

                        val result = if (timeRangeSelection == -1) {
                            // Custom range: inclusive local calendar days
                            // [startDate 00:00, endDate+1 00:00) in the device zone.
                            val zone = ZoneId.systemDefault()
                            val startInstant = startDate?.atStartOfDay(zone)?.toInstant()
                            val endInstant = endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
                            if (startInstant == null || endInstant == null) {
                                syncMessage = context.getString(R.string.err_select_both_dates)
                                isSyncing = false
                                return@launch
                            }
                            if (!startInstant.isBefore(endInstant)) {
                                syncMessage = context.getString(R.string.err_end_date_before_start)
                                isSyncing = false
                                return@launch
                            }

                            syncMessage = context.getString(R.string.manual_sync_progress, startDate.toString(), endDate.toString())
                            syncManager.performSync(start = startInstant, end = endInstant, syncType = "manual", targetWebhooks = targetWebhooks)
                        } else {
                            // sync the last N days, or from the last sync
                            syncManager.performSync(timeRangeSelection, syncType = "manual", targetWebhooks = targetWebhooks)
                        }

                        when {
                            result.isSuccess -> {
                                val syncResult = result.getOrThrow()
                                syncMessage = when (syncResult) {
                                    is SyncResult.NoData -> context.getString(R.string.manual_sync_no_data)
                                    is SyncResult.NoMatchingData -> context.getString(R.string.manual_sync_no_matching_data)
                                    is SyncResult.Success -> {
                                        val parts = syncResult.syncCounts.map { (type, count) ->
                                            "$count ${context.getString(type.nameResId).lowercase()}"
                                        }
                                        if (parts.isEmpty()) context.getString(R.string.manual_sync_success_empty)
                                        else context.getString(R.string.manual_sync_success_items, parts.joinToString(", "))
                                    }
                                }
                                onSyncCompleted()
                            }
                            result.isFailure -> {
                                syncMessage = context.getString(R.string.manual_sync_failed, result.exceptionOrNull()?.message ?: "Unknown error")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        syncMessage = context.getString(R.string.manual_sync_failed, e.message)
                    } finally {
                        isSyncing = false
                    }
                }
                    },
                    enabled = customRangeValid,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(stringResource(R.string.manual_sync_btn))
                }
            }
        }
    }

    if (isCustomRange) {
        val todayMillis = remember(today) {
            today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
            if (showStartDatePicker) {
                val startPickerState = rememberDatePickerState(
                    initialSelectedDateMillis = startDate
                        ?.atStartOfDay(ZoneOffset.UTC)
                        ?.toInstant()
                        ?.toEpochMilli()
                        ?: todayMillis,
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            return utcTimeMillis <= todayMillis
                        }

                        override fun isSelectableYear(year: Int): Boolean {
                            return year <= today.year
                        }
                    }
                )
                DatePickerDialog(
                    onDismissRequest = { showStartDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val selectedMillis = startPickerState.selectedDateMillis
                            startDate = selectedMillis?.let {
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            }
                            showStartDatePicker = false
                        }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartDatePicker = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                ) {
                    DatePicker(state = startPickerState)
                }
            }

            if (showEndDatePicker) {
                val currentEndDate = endDate
                val minEndDateMillis = startDate
                    ?.atStartOfDay(ZoneOffset.UTC)
                    ?.toInstant()
                    ?.toEpochMilli()
                val minEndYear = startDate?.year
                val endPickerState = rememberDatePickerState(
                    initialSelectedDateMillis = when {
                        currentEndDate != null && minEndDateMillis != null -> {
                            val endDateMillis = currentEndDate
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli()
                            maxOf(endDateMillis, minEndDateMillis)
                        }
                        currentEndDate != null -> currentEndDate
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli()
                        minEndDateMillis != null -> minEndDateMillis
                        else -> todayMillis
                    },
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            return utcTimeMillis <= todayMillis &&
                                (minEndDateMillis == null || utcTimeMillis >= minEndDateMillis)
                        }

                        override fun isSelectableYear(year: Int): Boolean {
                            return year <= today.year && (minEndYear == null || year >= minEndYear)
                        }
                    }
                )
                DatePickerDialog(
                    onDismissRequest = { showEndDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val selectedMillis = endPickerState.selectedDateMillis
                            endDate = selectedMillis?.let {
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            }
                            showEndDatePicker = false
                        }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndDatePicker = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                ) {
                    DatePicker(state = endPickerState)
                }
            }
    }

    // ── Status card ──────────────────────────────────────────────────────────
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.85f))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat(lastSyncText, stringResource(R.string.home_hero_last_sync), Modifier.weight(1.4f))
                HeroStat(sentLast24h?.toString() ?: "–", stringResource(R.string.home_hero_sent), Modifier.weight(1f))
                HeroStat(failedLast24h?.toString() ?: "–", stringResource(R.string.home_hero_failed), Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showConfirmSheet = true },
                enabled = canSync && !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.onPrimaryContainer,
                    contentColor = colors.primaryContainer,
                    disabledContainerColor = colors.onPrimaryContainer.copy(alpha = 0.3f),
                    disabledContentColor = colors.onPrimaryContainer.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.manual_sync_syncing))
                } else {
                    Icon(Icons.Filled.Sync, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.manual_sync_btn))
                }
            }
            AnimatedVisibility(visible = syncMessage != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(
                    syncMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
    }
}
