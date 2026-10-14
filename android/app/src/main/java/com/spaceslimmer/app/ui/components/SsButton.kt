package com.spaceslimmer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.ui.theme.ButtonShape

@Composable
fun SsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expand: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            if (expand) Modifier.fillMaxWidth().height(44.dp) else Modifier.heightIn(min = 40.dp),
        ),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun SsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expand: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(
            if (expand) Modifier.fillMaxWidth().height(44.dp) else Modifier.heightIn(min = 40.dp),
        ),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}
