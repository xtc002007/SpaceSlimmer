package com.spaceslimmer.app.ui.components



import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.selection.selectableGroup

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.SemanticsProperties

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.role

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.stateDescription

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

import com.spaceslimmer.app.R



data class SsSegmentOption<T>(

    val value: T,

    val label: String,

    val enabled: Boolean = true,

)



@Composable

fun <T> SsSegmented(

    options: List<SsSegmentOption<T>>,

    selected: T,

    onSelected: (T) -> Unit,

    modifier: Modifier = Modifier,

    scrollable: Boolean = options.size > 3,

) {

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val selectedColor = MaterialTheme.colorScheme.surface

    val scrollState = rememberScrollState()

    val selectedLabel = stringResource(R.string.seg_selected)

    val notSelectedLabel = stringResource(R.string.seg_not_selected)

    Row(

        modifier = modifier

            .fillMaxWidth()

            .clip(RoundedCornerShape(10.dp))

            .background(trackColor)

            .padding(3.dp)

            .selectableGroup()

            .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier),

        horizontalArrangement = Arrangement.spacedBy(3.dp),

    ) {

        options.forEach { option ->

            val isSelected = option.value == selected

            val segmentModifier = if (scrollable) {

                Modifier.padding(horizontal = 2.dp)

            } else {

                Modifier.weight(1f)

            }

            Box(

                modifier = segmentModifier

                    .clip(RoundedCornerShape(8.dp))

                    .background(if (isSelected) selectedColor else trackColor)

                    .semantics {

                        role = Role.RadioButton

                        set(SemanticsProperties.Selected, isSelected)

                        contentDescription = option.label

                        stateDescription = if (isSelected) selectedLabel else notSelectedLabel

                    }

                    .then(

                        if (option.enabled) {

                            Modifier.clickable(

                                interactionSource = remember { MutableInteractionSource() },

                                indication = null,

                                onClick = { onSelected(option.value) },

                            )

                        } else {

                            Modifier

                        },

                    )

                    .padding(vertical = 8.dp, horizontal = if (scrollable) 12.dp else if (options.size >= 5) 2.dp else 4.dp),

                contentAlignment = Alignment.Center,

            ) {

                Text(

                    text = option.label,

                    style = if (options.size >= 5 && !scrollable) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,

                    fontSize = if (options.size >= 5 && !scrollable) 11.5.sp else TextUnit.Unspecified,

                    color = when {

                        !option.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                        isSelected -> MaterialTheme.colorScheme.primary

                        else -> MaterialTheme.colorScheme.onSurfaceVariant

                    },

                    textAlign = TextAlign.Center,

                    maxLines = 1,

                    overflow = TextOverflow.Ellipsis,

                )

            }

        }

    }

}


