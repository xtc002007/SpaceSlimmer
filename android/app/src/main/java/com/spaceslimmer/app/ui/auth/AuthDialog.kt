package com.spaceslimmer.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.billing.SpaceSlimmerApiClient
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.theme.InputShape
import kotlinx.coroutines.launch

sealed class PaywallReason {
    data class BatchLimit(val currentCount: Int, val maxFree: Int) : PaywallReason()
    data class ProFeature(val featureName: String) : PaywallReason()
    object QuotaExhausted : PaywallReason()
}

@Composable
fun AuthDialog(
    onDismissRequest: () -> Unit,
    onSuccess: () -> Unit,
    initialIsRegister: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(initialIsRegister) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = if (isRegister) stringResource(R.string.auth_register_title)
                    else stringResource(R.string.auth_login_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_guest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.trim()
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    placeholder = { Text(stringResource(R.string.auth_email_hint)) },
                    leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = InputShape,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    placeholder = { Text(stringResource(R.string.auth_password_hint)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = InputShape,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.auth_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (isRegister) stringResource(R.string.auth_switch_to_login)
                        else stringResource(R.string.auth_switch_to_register),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                isRegister = !isRegister
                                errorMessage = null
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            SsPrimaryButton(
                text = if (isRegister) stringResource(R.string.auth_submit_register)
                else stringResource(R.string.auth_submit_login),
                onClick = {
                    if (email.isBlank() || password.length < 6) {
                        errorMessage = context.getString(R.string.auth_err_invalid_credentials)
                        return@SsPrimaryButton
                    }
                    loading = true
                    errorMessage = null
                    scope.launch {
                        val result = if (isRegister) {
                            SpaceSlimmerApiClient.register(context, email, password)
                        } else {
                            SpaceSlimmerApiClient.login(context, email, password)
                        }
                        loading = false
                        if (result.isSuccess) {
                            onSuccess()
                            onDismissRequest()
                        } else {
                            errorMessage = result.errorMessage ?: context.getString(R.string.auth_err_request_failed)
                        }
                    }
                },
                enabled = !loading,
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !loading,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun PaywallGateDialog(
    reason: PaywallReason,
    onDismissRequest: () -> Unit,
    onUnlockSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAuthDialog by remember { mutableStateOf(false) }
    var licenseInput by remember { mutableStateOf("") }
    var licenseLoading by remember { mutableStateOf(false) }
    var licenseError by remember { mutableStateOf<String?>(null) }
    var licenseSuccessMsg by remember { mutableStateOf<String?>(null) }
    var showLicenseInput by remember { mutableStateOf(false) }

    val isLoggedIn = remember { SpaceSlimmerApiClient.isLoggedIn(context) }

    if (showAuthDialog) {
        AuthDialog(
            onDismissRequest = { showAuthDialog = false },
            onSuccess = {
                showAuthDialog = false
                onUnlockSuccess()
            },
        )
        return
    }

    val title = when (reason) {
        is PaywallReason.QuotaExhausted -> stringResource(R.string.paywall_quota_exhausted_title)
        else -> stringResource(R.string.paywall_pro_required_title)
    }

    val desc = when (reason) {
        is PaywallReason.BatchLimit -> stringResource(
            R.string.paywall_batch_limit_desc,
            reason.maxFree,
            reason.currentCount,
        )
        is PaywallReason.ProFeature -> stringResource(
            R.string.paywall_pro_feature_desc,
            reason.featureName,
        )
        is PaywallReason.QuotaExhausted -> stringResource(R.string.paywall_quota_exhausted_desc)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_pro_features_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.profile_pro_feature_1),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.profile_pro_feature_2),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.profile_pro_feature_3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (!showLicenseInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.paywall_have_license_btn),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showLicenseInput = true }
                                .padding(vertical = 4.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = showLicenseInput) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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

                        if (licenseSuccessMsg != null) {
                            Text(
                                text = licenseSuccessMsg!!,
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
                                        licenseSuccessMsg = context.getString(R.string.profile_license_success)
                                        onUnlockSuccess()
                                        onDismissRequest()
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
        },
        confirmButton = {
            if (!showLicenseInput) {
                SsPrimaryButton(
                    text = if (isLoggedIn) stringResource(R.string.paywall_upgrade_pro_redeem) else stringResource(R.string.paywall_action_login),
                    onClick = {
                        if (isLoggedIn) {
                            showLicenseInput = true
                        } else {
                            showAuthDialog = true
                        }
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.paywall_action_cancel))
            }
        },
    )
}
