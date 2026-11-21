package com.spaceslimmer.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.billing.BillingManager
import com.spaceslimmer.app.billing.SpaceSlimmerApiClient
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.ui.audioPresetLabel
import com.spaceslimmer.app.ui.auth.AuthDialog
import com.spaceslimmer.app.ui.components.SsAlertDialog
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsDialogTextButton
import com.spaceslimmer.app.ui.components.SsLargeTitle
import com.spaceslimmer.app.ui.components.SsListRow
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSection
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.imagePresetLabel
import com.spaceslimmer.app.ui.shared.AboutSection
import com.spaceslimmer.app.ui.theme.InputShape
import com.spaceslimmer.app.ui.theme.LocalThemeController
import com.spaceslimmer.app.ui.theme.ThemeMode
import com.spaceslimmer.app.ui.videoPresetLabel
import com.spaceslimmer.core.LocaleHelper
import kotlinx.coroutines.launch
import uniffi.ss_core.Compatibility
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.ParallelProfile
import uniffi.ss_core.QualityPreset
import uniffi.ss_core.Settings

@Composable
fun SettingsScreen(
    engine: CoreEngine?,
    engineError: String? = null,
    modifier: Modifier = Modifier,
    onOpenLogs: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember {
        val initial = engine?.settings() ?: defaultSettings()
        mutableStateOf(initial.copy(language = LocaleHelper.selectorCode(context)))
    }
    val themeController = LocalThemeController.current
    var settingsError by remember { mutableStateOf<String?>(null) }

    // User Profile state
    var isLoggedIn by remember { mutableStateOf(SpaceSlimmerApiClient.isLoggedIn(context)) }
    var userEmail by remember { mutableStateOf(SpaceSlimmerApiClient.getUserEmail(context) ?: "") }
    var isPro by remember { mutableStateOf(BillingManager.isProUnlocked(context)) }
    var planType by remember { mutableStateOf(SpaceSlimmerApiClient.getPlanType(context)) }
    var remainingQuota by remember { mutableIntStateOf(SpaceSlimmerApiClient.getRemainingQuota(context)) }
    var totalQuota by remember { mutableIntStateOf(SpaceSlimmerApiClient.getTotalQuota(context)) }
    var expiresAt by remember { mutableStateOf(SpaceSlimmerApiClient.getExpiresAt(context)) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var licenseInput by remember { mutableStateOf("") }
    var licenseLoading by remember { mutableStateOf(false) }
    var licenseError by remember { mutableStateOf<String?>(null) }
    var licenseSuccess by remember { mutableStateOf<String?>(null) }

    fun refreshUserState() {
        isLoggedIn = SpaceSlimmerApiClient.isLoggedIn(context)
        userEmail = SpaceSlimmerApiClient.getUserEmail(context) ?: ""
        isPro = BillingManager.isProUnlocked(context)
        planType = SpaceSlimmerApiClient.getPlanType(context)
        remainingQuota = SpaceSlimmerApiClient.getRemainingQuota(context)
        totalQuota = SpaceSlimmerApiClient.getTotalQuota(context)
        expiresAt = SpaceSlimmerApiClient.getExpiresAt(context)
    }

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            val statusRes = SpaceSlimmerApiClient.getStatus(context)
            if (statusRes.isSuccess) {
                refreshUserState()
            }
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(settings).copy(language = LocaleHelper.selectorCode(context))
        settings = next
        if (engine == null) {
            settingsError = engineError ?: context.getString(R.string.settings_save_failed)
            return
        }
        runCatching { engine.setSettings(next) }
            .onSuccess { settingsError = null }
            .onFailure {
                settingsError = it.message ?: context.getString(R.string.settings_save_failed)
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        SsLargeTitle(title = stringResource(R.string.settings_title))

        if (settingsError != null) {
            Text(
                text = settingsError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!BillingManager.isBillingEnabled()) {
                // ---- 离线本地隐私版卡片 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_offline_edition_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_offline_unlimited),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.profile_offline_edition_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
            // ---- 1. 用户账户与信息卡片 ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 顶部：头像 + 用户状态 + 右侧登出操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPro) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isPro) Icons.Default.WorkspacePremium else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isLoggedIn) {
                                        userEmail.ifBlank { stringResource(R.string.profile_title) }
                                    } else {
                                        stringResource(R.string.profile_not_logged_in)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isPro) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                ) {
                                    Text(
                                        text = when (planType) {
                                            "pro-lifetime" -> stringResource(R.string.profile_plan_pro_lifetime)
                                            "pro-yearly" -> stringResource(R.string.profile_plan_pro_yearly)
                                            "pro-monthly" -> stringResource(R.string.profile_plan_pro_monthly)
                                            else -> if (isPro) stringResource(R.string.vip_status_active) else stringResource(R.string.profile_plan_free)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Text(
                                text = if (isLoggedIn) {
                                    if (expiresAt != null) stringResource(R.string.profile_expires_at, expiresAt!!.take(10))
                                    else stringResource(R.string.profile_cloud_online)
                                } else {
                                    stringResource(R.string.profile_guest_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (isLoggedIn) {
                            IconButton(onClick = { showLogoutConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = stringResource(R.string.profile_logout),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    // 未登录状态展示独立完整的“登录 / 注册”操作按钮
                    if (!isLoggedIn) {
                        Button(
                            onClick = { showAuthDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.profile_login_register),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.8.dp,
                    )

                    // 额度展示
                    if (!isPro) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_quota_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.profile_quota_status, remainingQuota, totalQuota),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingQuota <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            val progress = if (totalQuota > 0) (remainingQuota.toFloat() / totalQuota.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (remainingQuota <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.profile_quota_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.profile_quota_unlimited),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.profile_device_id, SpaceSlimmerApiClient.getOrCreateDeviceId(context)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // ---- 2. 兑换 PRO 激活码与特权 ----
            if (!isPro) {
                SsSection(title = stringResource(R.string.profile_activate_license)) {
                    SsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.profile_pro_feature_1),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.profile_pro_feature_2),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.profile_pro_feature_3),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.profile_pro_feature_4),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            OutlinedTextField(
                                value = licenseInput,
                                onValueChange = {
                                    licenseInput = it
                                    licenseError = null
                                },
                                label = { Text(stringResource(R.string.profile_activate_license)) },
                                placeholder = { Text(stringResource(R.string.profile_license_placeholder)) },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                singleLine = true,
                                shape = InputShape,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (licenseError != null) {
                                Text(
                                    text = licenseError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            if (licenseSuccess != null) {
                                Text(
                                    text = licenseSuccess!!,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            SsPrimaryButton(
                                text = if (licenseLoading) stringResource(R.string.redeeming) else stringResource(R.string.profile_license_btn),
                                onClick = {
                                    if (licenseInput.isBlank()) {
                                        licenseError = context.getString(R.string.profile_license_empty)
                                        return@SsPrimaryButton
                                    }
                                    licenseLoading = true
                                    licenseError = null
                                    scope.launch {
                                        val res = SpaceSlimmerApiClient.activateLicense(context, licenseInput)
                                        licenseLoading = false
                                        if (res.isSuccess) {
                                            licenseSuccess = context.getString(R.string.profile_license_success)
                                            licenseInput = ""
                                            refreshUserState()
                                        } else {
                                            licenseError = res.errorMessage ?: context.getString(R.string.license_err_invalid_or_used)
                                        }
                                    }
                                },
                                enabled = !licenseLoading,
                            )
                        }
                    }
                }
            }

            // ---- 3. 云端服务状态 ----
            SsSection(title = stringResource(R.string.profile_cloud_status)) {
                SsCard {
                    SsListRow(
                        title = stringResource(R.string.profile_cloud_status),
                        subtitle = stringResource(R.string.profile_cloud_online),
                        trailing = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (isLoggedIn) {
                                            SpaceSlimmerApiClient.getStatus(context)
                                            refreshUserState()
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.profile_refresh_status),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        showDivider = false,
                    )
                }
            }
            }

            // ---- 4. 通用偏好设置 (保留原有能力) ----
            SsSection(title = stringResource(R.string.settings_appearance)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption(ThemeMode.Light, stringResource(R.string.theme_light)),
                            SsSegmentOption(ThemeMode.Dark, stringResource(R.string.theme_dark)),
                            SsSegmentOption(ThemeMode.System, stringResource(R.string.theme_system)),
                        ),
                        selected = themeController.mode,
                        onSelected = { themeController.updateThemeMode(it) },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_language)) {
                val currentCode = LocaleHelper.currentLanguageCode(context)
                val currentLang = LocaleHelper.findLanguage(currentCode)
                val displaySubtitle = if (currentCode == LocaleHelper.CODE_SYSTEM) {
                    stringResource(R.string.language_follow_system)
                } else {
                    "${currentLang.nativeName} (${currentLang.englishName})"
                }

                SsListRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = displaySubtitle,
                    trailing = {
                        Text(
                            text = if (currentCode == LocaleHelper.CODE_SYSTEM) stringResource(R.string.language_follow_system) else currentLang.nativeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = { showLanguageDialog = true },
                )
            }

            SsSection(title = stringResource(R.string.tab_vault)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = settings.vaultDays.toString(),
                        onValueChange = { v ->
                            v.toUIntOrNull()?.let { days -> updateSettings { it.copy(vaultDays = days) } }
                        },
                        label = { Text(stringResource(R.string.settings_vault_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = InputShape,
                    )
                }
                SsListRow(
                    title = stringResource(R.string.settings_auto_delete_vault),
                    showDivider = true,
                    trailing = {
                        Switch(
                            checked = settings.autoDeleteVault,
                            onCheckedChange = { checked -> updateSettings { it.copy(autoDeleteVault = checked) } },
                        )
                    },
                )
                SsListRow(
                    title = stringResource(R.string.settings_idle_charge_only),
                    showDivider = false,
                    trailing = {
                        Switch(
                            checked = settings.idleChargeOnly,
                            onCheckedChange = { checked -> updateSettings { it.copy(idleChargeOnly = checked) } },
                        )
                    },
                )
            }

            var batchRename by remember { mutableStateOf(AppPrefs.batchRenameEnabled(context)) }
            var archiveByDate by remember { mutableStateOf(AppPrefs.archiveByDateEnabled(context)) }
            SsSection(title = stringResource(R.string.settings_export)) {
                SsListRow(
                    title = stringResource(R.string.settings_batch_rename),
                    showDivider = true,
                    trailing = {
                        Switch(
                            checked = batchRename,
                            onCheckedChange = {
                                batchRename = it
                                AppPrefs.setBatchRenameEnabled(context, it)
                            },
                        )
                    },
                )
                SsListRow(
                    title = stringResource(R.string.settings_archive_by_date),
                    showDivider = false,
                    trailing = {
                        Switch(
                            checked = archiveByDate,
                            onCheckedChange = {
                                archiveByDate = it
                                AppPrefs.setArchiveByDateEnabled(context, it)
                            },
                        )
                    },
                )
            }

            SsSection(title = stringResource(R.string.settings_parallel)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption(ParallelProfile.ECO, parallelProfileLabel(ParallelProfile.ECO)),
                            SsSegmentOption(ParallelProfile.AUTO, parallelProfileLabel(ParallelProfile.AUTO)),
                            SsSegmentOption(ParallelProfile.FAST, parallelProfileLabel(ParallelProfile.FAST)),
                        ),
                        selected = settings.parallelProfile,
                        onSelected = { selected -> updateSettings { it.copy(parallelProfile = selected) } },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_default_video)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption(QualityPreset.VIDEO_HIGH, videoPresetLabel(QualityPreset.VIDEO_HIGH)),
                            SsSegmentOption(QualityPreset.VIDEO_BALANCED, videoPresetLabel(QualityPreset.VIDEO_BALANCED)),
                            SsSegmentOption(QualityPreset.VIDEO_SMALL, videoPresetLabel(QualityPreset.VIDEO_SMALL)),
                        ),
                        selected = settings.defaultPresetVideo,
                        onSelected = { selected -> updateSettings { it.copy(defaultPresetVideo = selected) } },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_default_image)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption(QualityPreset.IMAGE_MAX, imagePresetLabel(QualityPreset.IMAGE_MAX)),
                            SsSegmentOption(QualityPreset.IMAGE_HIGH, imagePresetLabel(QualityPreset.IMAGE_HIGH)),
                            SsSegmentOption(QualityPreset.IMAGE_SMALL, imagePresetLabel(QualityPreset.IMAGE_SMALL)),
                        ),
                        selected = settings.defaultPresetImage,
                        onSelected = { selected -> updateSettings { it.copy(defaultPresetImage = selected) } },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_default_image_format)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption("jpeg", "JPEG"),
                            SsSegmentOption("webp", "WebP"),
                        ),
                        selected = settings.defaultImageFormat,
                        onSelected = { selected -> updateSettings { it.copy(defaultImageFormat = selected) } },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_default_audio)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption(QualityPreset.AUDIO_SPEECH, audioPresetLabel(QualityPreset.AUDIO_SPEECH)),
                            SsSegmentOption(QualityPreset.AUDIO_MUSIC_HIGH, audioPresetLabel(QualityPreset.AUDIO_MUSIC_HIGH)),
                            SsSegmentOption(QualityPreset.AUDIO_MUSIC_SMALL, audioPresetLabel(QualityPreset.AUDIO_MUSIC_SMALL)),
                        ),
                        selected = settings.defaultPresetAudio,
                        onSelected = { selected -> updateSettings { it.copy(defaultPresetAudio = selected) } },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_default_compat)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SsSegmented(
                        options = listOf(
                            SsSegmentOption(Compatibility.COMPATIBLE, stringResource(R.string.compat_compatible)),
                            SsSegmentOption(Compatibility.EFFICIENT, stringResource(R.string.compat_efficient)),
                        ),
                        selected = settings.defaultCompat,
                        onSelected = { selected -> updateSettings { it.copy(defaultCompat = selected) } },
                    )
                }
            }

            SsSection(title = stringResource(R.string.settings_skip)) {
                SsListRow(
                    title = stringResource(R.string.settings_skip_enabled),
                    showDivider = true,
                    trailing = {
                        Switch(
                            checked = settings.skipEnabled,
                            onCheckedChange = { checked -> updateSettings { it.copy(skipEnabled = checked) } },
                        )
                    },
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = settings.videoSkipRatio.toString(),
                        onValueChange = { v ->
                            v.toFloatOrNull()?.let { updateSettings { s -> s.copy(videoSkipRatio = it) } }
                        },
                        label = { Text(stringResource(R.string.settings_video_skip_ratio)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = InputShape,
                    )
                    OutlinedTextField(
                        value = settings.videoSkipAbs.toString(),
                        onValueChange = { v ->
                            v.toULongOrNull()?.let { updateSettings { s -> s.copy(videoSkipAbs = it) } }
                        },
                        label = { Text(stringResource(R.string.settings_video_skip_abs)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = InputShape,
                    )
                    OutlinedTextField(
                        value = settings.imageSkipRatio.toString(),
                        onValueChange = { v ->
                            v.toFloatOrNull()?.let { updateSettings { s -> s.copy(imageSkipRatio = it) } }
                        },
                        label = { Text(stringResource(R.string.settings_image_skip_ratio)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = InputShape,
                    )
                    OutlinedTextField(
                        value = settings.imageSkipAbs.toString(),
                        onValueChange = { v ->
                            v.toULongOrNull()?.let { updateSettings { s -> s.copy(imageSkipAbs = it) } }
                        },
                        label = { Text(stringResource(R.string.settings_image_skip_abs)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = InputShape,
                    )
                }
                SsListRow(
                    title = stringResource(R.string.settings_keep_gps),
                    showDivider = false,
                    trailing = {
                        Switch(
                            checked = settings.keepGps,
                            onCheckedChange = { checked -> updateSettings { it.copy(keepGps = checked) } },
                        )
                    },
                )
            }

            SsSection(title = stringResource(R.string.settings_logs_title)) {
                SsListRow(
                    title = stringResource(R.string.settings_logs_title),
                    subtitle = stringResource(R.string.settings_logs_desc),
                    onClick = onOpenLogs,
                    showChevron = true,
                    showDivider = false,
                )
            }

            AboutSection(engine = engine, engineError = engineError)
        }
    }

    if (showAuthDialog) {
        AuthDialog(
            onDismissRequest = { showAuthDialog = false },
            onSuccess = {
                showAuthDialog = false
                refreshUserState()
            },
        )
    }

    if (showLogoutConfirm) {
        SsAlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.profile_logout)) },
            text = { Text(stringResource(R.string.profile_logout_confirm)) },
            confirmButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.profile_logout),
                    onClick = {
                        showLogoutConfirm = false
                        scope.launch {
                            SpaceSlimmerApiClient.logout(context)
                            refreshUserState()
                        }
                    },
                )
            },
            dismissButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showLogoutConfirm = false },
                )
            },
        )
    }

    if (showLanguageDialog) {
        val currentCode = LocaleHelper.currentLanguageCode(context)
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.language_select_dialog_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(LocaleHelper.SUPPORTED_LANGUAGES) { item ->
                        val isSelected = item.code == currentCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showLanguageDialog = false
                                    val next = settings.copy(language = item.code)
                                    settings = next
                                    if (engine == null) {
                                        settingsError = engineError ?: context.getString(R.string.settings_save_failed)
                                    } else {
                                        runCatching { engine.setSettings(next) }
                                            .onSuccess { settingsError = null }
                                            .onFailure {
                                                settingsError = it.message ?: context.getString(R.string.settings_save_failed)
                                            }
                                    }
                                    LocaleHelper.applyLanguage(context, item.code)
                                    LocaleHelper.recreateActivity(context)
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                val nativeLabel = if (item.code == LocaleHelper.CODE_SYSTEM) {
                                    stringResource(R.string.language_follow_system)
                                } else {
                                    item.nativeName
                                }
                                Text(
                                    text = nativeLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = item.englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun defaultSettings(): Settings = Settings(
    defaultPresetVideo = QualityPreset.VIDEO_BALANCED,
    defaultPresetImage = QualityPreset.IMAGE_HIGH,
    defaultPresetAudio = QualityPreset.AUDIO_SPEECH,
    defaultImageFormat = "jpeg",
    defaultCompat = Compatibility.COMPATIBLE,
    skipEnabled = true,
    videoSkipRatio = 0.08f,
    videoSkipAbs = 5u * 1024u * 1024u,
    imageSkipRatio = 0.10f,
    imageSkipAbs = 200u * 1024u,
    keepGps = true,
    vaultDays = 30u,
    autoDeleteVault = false,
    parallelProfile = ParallelProfile.AUTO,
    language = LocaleHelper.CODE_SYSTEM,
    estimateScaleImage = 1f,
    estimateScaleVideo = 1f,
    estimateScaleAudio = 1f,
    idleChargeOnly = false,
)

@Composable
private fun parallelProfileLabel(profile: ParallelProfile): String = when (profile) {
    ParallelProfile.ECO -> stringResource(R.string.settings_parallel_eco)
    ParallelProfile.AUTO -> stringResource(R.string.settings_parallel_auto)
    ParallelProfile.FAST -> stringResource(R.string.settings_parallel_fast)
}
