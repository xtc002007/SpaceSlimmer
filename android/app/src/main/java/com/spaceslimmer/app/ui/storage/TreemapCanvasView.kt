package com.spaceslimmer.app.ui.storage

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.shared.formatBytes
import uniffi.ss_core.TreemapItemView
import uniffi.ss_core.TreemapViewResult
import kotlin.math.abs

/**
 * Treemap Canvas: squarified treemap rendering engine with cushion shading,
 * smart baseline font-metrics text layout, and tap/double-tap interactions.
 * The bottom action bar is positioned BELOW the treemap canvas so it never
 * covers or obscures any tiles.
 */
@Composable
fun TreemapCanvasView(
    treemap: TreemapViewResult?,
    modifier: Modifier = Modifier,
    onSizeChanged: (width: Float, height: Float) -> Unit,
    onDrillDown: (TreemapItemView) -> Unit,
    onCompressItem: (TreemapItemView) -> Unit = {},
) {
    var selectedItem by remember { mutableStateOf<TreemapItemView?>(null) }
    val density = LocalDensity.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        val items = treemap?.items ?: emptyList()

        // 1. Treemap Canvas (takes all available height ABOVE the bottom panel)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            LaunchedEffect(widthPx, heightPx) {
                if (widthPx > 10f && heightPx > 10f) {
                    onSizeChanged(widthPx, heightPx)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items) {
                        detectTapGestures(
                            onTap = { offset ->
                                val clicked = items.firstOrNull { it.contains(offset.x, offset.y) }
                                selectedItem = clicked
                            },
                            onDoubleTap = { offset ->
                                val clicked = items.firstOrNull { it.contains(offset.x, offset.y) }
                                if (clicked != null && clicked.isDir) {
                                    selectedItem = null
                                    onDrillDown(clicked)
                                }
                            }
                        )
                    }
            ) {
                val shadowColor = android.graphics.Color.argb(220, 0, 0, 0)

                // Large Hero tile paints (e.g. DCIM, Movies, large systems)
                val heroPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 13.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.WHITE
                    setShadowLayer(5f, 0f, 1.5f, shadowColor)
                }
                val heroSubPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 10.5f.sp.toPx()
                    typeface = Typeface.DEFAULT
                    color = android.graphics.Color.argb(240, 255, 255, 255)
                    setShadowLayer(4f, 0f, 1.2f, shadowColor)
                }

                // Medium tile paints
                val regularPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 11.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.WHITE
                    setShadowLayer(4f, 0f, 1.2f, shadowColor)
                }
                val regularSubPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 9.5f.sp.toPx()
                    typeface = Typeface.DEFAULT
                    color = android.graphics.Color.argb(230, 255, 255, 255)
                    setShadowLayer(3.5f, 0f, 1f, shadowColor)
                }

                // Small tile single-line paint
                val smallPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 9.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.WHITE
                    setShadowLayer(3f, 0f, 1f, shadowColor)
                }

                val padX = 7.dp.toPx()
                val padY = 6.dp.toPx()
                val lineSpacing = 3.dp.toPx()
                val heroThreshold = 90.dp.toPx()

                for (item in items) {
                    val rectOffset = Offset(item.x, item.y)
                    val rectSize = Size(item.width, item.height)

                    if (rectSize.width < 1.5f || rectSize.height < 1.5f) continue

                    val baseColor = resolveItemColor(item)

                    // 1. Base block color
                    drawRoundRect(
                        color = baseColor,
                        topLeft = rectOffset,
                        size = rectSize,
                        cornerRadius = CornerRadius(4f, 4f),
                    )

                    // 2. Cushion shading gradient
                    val cushionBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.30f),
                        ),
                        start = rectOffset,
                        end = Offset(item.x + item.width, item.y + item.height),
                    )
                    drawRoundRect(
                        brush = cushionBrush,
                        topLeft = rectOffset,
                        size = rectSize,
                        cornerRadius = CornerRadius(4f, 4f),
                    )

                    // 3. Selection outline or boundary stroke
                    if (selectedItem?.id == item.id) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = rectOffset,
                            size = rectSize,
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    } else {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.30f),
                            topLeft = rectOffset,
                            size = rectSize,
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 1f),
                        )
                    }

                    // 4. Text labels with smart FontMetrics baseline
                    val availW = item.width - padX * 2
                    val availH = item.height - padY * 2

                    if (availW > 16.dp.toPx() && availH > 12.dp.toPx()) {
                        val isHero = item.width >= heroThreshold && item.height >= heroThreshold
                        val pTitle = if (isHero) heroPaint else regularPaint
                        val pSub = if (isHero) heroSubPaint else regularSubPaint

                        val titleFm = pTitle.fontMetrics
                        val subFm = pSub.fontMetrics
                        val titleH = titleFm.descent - titleFm.ascent
                        val subH = subFm.descent - subFm.ascent

                        val displayName = resolveDisplayName(context, item.name, item.isCollapsed, item.collapsedCount)
                        val sizeText = formatBytes(item.weight.toLong())

                        drawContext.canvas.nativeCanvas.apply {
                            save()
                            clipRect(
                                item.x + 2f,
                                item.y + 2f,
                                item.x + item.width - 2f,
                                item.y + item.height - 2f
                            )

                            if (availH >= (titleH + lineSpacing + subH * 0.8f)) {
                                // Two lines: Name + Size
                                val elTitle = TextUtils.ellipsize(displayName, pTitle, availW, TextUtils.TruncateAt.END).toString()
                                val baseline1 = item.y + padY - titleFm.ascent
                                drawText(elTitle, item.x + padX, baseline1, pTitle)

                                val elSize = TextUtils.ellipsize(sizeText, pSub, availW, TextUtils.TruncateAt.END).toString()
                                val baseline2 = baseline1 + titleFm.descent + lineSpacing - subFm.ascent
                                drawText(elSize, item.x + padX, baseline2, pSub)
                            } else if (availH >= titleH * 0.9f) {
                                // Single line
                                val pSingle = if (availH < 22.dp.toPx()) smallPaint else pTitle
                                val sFm = pSingle.fontMetrics
                                val singleText = if (availW >= 110.dp.toPx()) {
                                    "$displayName  $sizeText"
                                } else {
                                    displayName
                                }
                                val elSingle = TextUtils.ellipsize(singleText, pSingle, availW, TextUtils.TruncateAt.END).toString()
                                val centerY = item.y + item.height / 2f
                                val baseline = centerY - (sFm.ascent + sFm.descent) / 2f
                                drawText(elSingle, item.x + padX, baseline, pSingle)
                            }

                            restore()
                        }
                    }
                }
            }

            // Top-right subtle hint pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.storage_treemap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 2. Bottom Action & Detail Card (Positioned BELOW the Canvas, never covering it!)
        AnimatedVisibility(
            visible = selectedItem != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            selectedItem?.let { item ->
                val isSystemPseudo = item.name.contains("Free Space", ignoreCase = true) ||
                        item.name.contains("System", ignoreCase = true) ||
                        item.name.contains("\u53ef\u7528\u7a7a\u95f4") ||
                        item.name.contains("\u79c1\u6709\u6570\u636e")

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Category Icon Badge
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(resolveItemColor(item)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when {
                                    isSystemPseudo -> Icons.Default.Info
                                    item.isDir -> Icons.Default.Folder
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Middle Information Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = resolveDisplayName(context, item.name, item.isCollapsed, item.collapsedCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (item.isDir && !isSystemPseudo) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.storage_folder_tag),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                } else if (isSystemPseudo) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.storage_system_stat_tag),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatBytes(item.weight.toLong()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f%%", item.percentage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Action Button (spacious, right-aligned, never squished)
                        if (item.isDir && !isSystemPseudo) {
                            Button(
                                onClick = {
                                    val target = item
                                    selectedItem = null
                                    onDrillDown(target)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.storage_action_drill_down),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else if (!isSystemPseudo) {
                            OutlinedButton(
                                onClick = {
                                    val target = item
                                    selectedItem = null
                                    onCompressItem(target)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.storage_action_compress),
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedItem = null },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves an item's visual color based on category, directory heuristics, or harmonious palette.
 */
internal fun resolveItemColor(item: TreemapItemView): Color {
    val nameLower = item.name.lowercase()
    if (nameLower.contains("dcim") || nameLower.contains("picture") || nameLower.contains("photo") || nameLower.contains("\u76f8\u518c") || nameLower.contains("\u7167\u7247")) {
        return Color(0xFF00897B) // Teal
    }
    if (nameLower.contains("video") || nameLower.contains("movie") || nameLower.contains("\u89c6\u9891") || nameLower.contains("\u5f71\u89c6")) {
        return Color(0xFF3949AB) // Indigo
    }
    if (nameLower.contains("music") || nameLower.contains("audio") || nameLower.contains("sound") || nameLower.contains("\u97f3\u4e50") || nameLower.contains("\u97f3\u9891")) {
        return Color(0xFFFB8C00) // Orange
    }
    if (nameLower.contains("doc") || nameLower.contains("pdf") || nameLower.contains("txt") || nameLower.contains("\u6587\u6863")) {
        return Color(0xFF1E88E5) // Blue
    }
    if (nameLower.contains("download") || nameLower.contains("\u4e0b\u8f7d")) {
        return Color(0xFF00ACC1) // Cyan
    }
    if (nameLower.contains("free space") || nameLower.contains("\u53ef\u7528\u7a7a\u95f4")) {
        return Color(0xFF455A64) // Slate Blue-Grey
    }
    if (nameLower.contains("system") || nameLower.contains("android") || nameLower.contains("\u7cfb\u7edf")) {
        return Color(0xFF5E35B1) // Deep Purple
    }
    if (nameLower.contains("tencent") || nameLower.contains("wechat") || nameLower.contains("weixin") || nameLower.contains("\u5fae\u4fe1")) {
        return Color(0xFF2E7D32) // Forest Green
    }
    if (item.colorHex.isNotBlank() && item.colorHex != "#9E9E9E") {
        return parseHexColor(item.colorHex)
    }
    // Harmonious modern palette based on name hash
    val palette = listOf(
        Color(0xFF00897B),
        Color(0xFF3949AB),
        Color(0xFF5E35B1),
        Color(0xFF1E88E5),
        Color(0xFF00ACC1),
        Color(0xFF43A047),
        Color(0xFFFB8C00),
        Color(0xFF6D4C41),
        Color(0xFF546E7A),
        Color(0xFFD81B60),
    )
    val hash = abs(item.name.hashCode())
    return palette[hash % palette.size]
}

/**
 * Humanizes technical placeholders like `<Free Space>` or `<System / Private App Data>`.
 */
internal fun resolveDisplayName(context: Context, rawName: String, isCollapsed: Boolean, collapsedCount: UInt): String {
    if (isCollapsed) {
        return context.getString(R.string.storage_collapsed_items, collapsedCount.toInt())
    }
    return when {
        rawName.equals("<Free Space>", ignoreCase = true) -> context.getString(R.string.storage_free_space)
        rawName.equals("<System / Private App Data>", ignoreCase = true) -> context.getString(R.string.storage_system_private_data)
        rawName.startsWith("<") && rawName.endsWith(">") -> rawName.removeSurrounding("<", ">").trim()
        else -> rawName
    }
}

/**
 * Parses hex color strings (#RRGGBB or #AARRGGBB).
 */
internal fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val parsed = clean.toLongOrNull(16) ?: 0x9E9E9EL
    val argb = when (clean.length) {
        6 -> (0xFFL shl 24) or parsed
        8 -> parsed
        else -> 0xFF9E9E9EL
    }
    return Color(argb.toInt())
}

private fun TreemapItemView.contains(px: Float, py: Float): Boolean {
    return px >= x && px <= (x + width) && py >= y && py <= (y + height)
}
