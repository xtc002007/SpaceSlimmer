package com.spaceslimmer.app.ui.preview



import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.dp

import com.spaceslimmer.app.R

import com.spaceslimmer.app.ui.components.SsCard

import com.spaceslimmer.app.ui.components.SsHeroMetric

import com.spaceslimmer.app.ui.components.SsListRow

import com.spaceslimmer.app.ui.components.SsPrimaryButton

import com.spaceslimmer.app.ui.components.SsSecondaryButton

import com.spaceslimmer.app.ui.components.SsSection

import com.spaceslimmer.app.ui.components.SsSegmentOption

import com.spaceslimmer.app.ui.components.SsSegmented

import com.spaceslimmer.app.ui.theme.SpaceSlimmerTheme

import com.spaceslimmer.app.ui.theme.ThemeMode



@Preview(name = "Design system — light", showBackground = true)

@Composable

private fun DesignSystemLightPreview() {

    SpaceSlimmerTheme(themeMode = ThemeMode.Light) {

        DesignSystemPreviewContent()

    }

}



@Preview(name = "Design system — dark", showBackground = true)

@Composable

private fun DesignSystemDarkPreview() {

    SpaceSlimmerTheme(themeMode = ThemeMode.Dark) {

        DesignSystemPreviewContent()

    }

}



@Preview(name = "Settings appearance — light", showBackground = true)

@Composable

private fun SettingsAppearanceLightPreview() {

    SpaceSlimmerTheme(themeMode = ThemeMode.Light) {

        SettingsAppearancePreviewContent()

    }

}



@Preview(name = "Settings appearance — dark", showBackground = true)

@Composable

private fun SettingsAppearanceDarkPreview() {

    SpaceSlimmerTheme(themeMode = ThemeMode.Dark) {

        SettingsAppearancePreviewContent()

    }

}



@Composable

private fun DesignSystemPreviewContent() {

    Column(

        modifier = Modifier.padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp),

    ) {

        SsHeroMetric(

            title = "Estimated savings",

            value = "1.2 – 2.4 GB",

            hint = "From cached radar scan",

        )

        SsSegmented(

            options = listOf(

                SsSegmentOption("light", "Light"),

                SsSegmentOption("dark", "Dark"),

                SsSegmentOption("system", "System"),

            ),

            selected = "light",

            onSelected = {},

        )

        SsCard {

            Column(modifier = Modifier.padding(16.dp)) {

                SsListRow(title = "Skip small files", subtitle = "Default on")

                Text(

                    text = "Secondary actions",

                    style = MaterialTheme.typography.labelLarge,

                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),

                )

                SsPrimaryButton(text = "Start compress", onClick = {})

                SsSecondaryButton(text = "Pick photos", onClick = {})

            }

        }

    }

}



@Composable

private fun SettingsAppearancePreviewContent() {

    Column(modifier = Modifier.padding(16.dp)) {

        SsSection(title = stringResource(R.string.settings_appearance)) {

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                SsSegmented(

                    options = listOf(

                        SsSegmentOption(ThemeMode.Light, stringResource(R.string.theme_light)),

                        SsSegmentOption(ThemeMode.Dark, stringResource(R.string.theme_dark)),

                        SsSegmentOption(ThemeMode.System, stringResource(R.string.theme_system)),

                    ),

                    selected = ThemeMode.System,

                    onSelected = {},

                )

            }

        }

    }

}


