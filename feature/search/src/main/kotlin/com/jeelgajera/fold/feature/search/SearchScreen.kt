package com.jeelgajera.fold.feature.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.FoldFilterChip
import com.jeelgajera.fold.core.design.component.FoldIconButton
import com.jeelgajera.fold.core.design.component.FoldScopeChip
import com.jeelgajera.fold.core.design.component.SectionHeading
import com.jeelgajera.fold.core.design.component.TypeBadge
import com.jeelgajera.fold.core.design.component.accentEdge
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.component.foldGlass
import com.jeelgajera.fold.core.design.icon.FoldIcon
import com.jeelgajera.fold.core.design.icon.FoldIconPaths
import com.jeelgajera.fold.core.design.theme.FoldMotion
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.mime.FileCategory
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.util.Formatting

/**
 * Search.
 *
 * The result rows highlight the matched substring in both the file name and, for
 * a contents hit, the matched line -- which is what makes a contents search worth
 * the wait: you can see *why* a file matched without opening it.
 */
@Composable
fun SearchScreen(
    onOpenPath: (FsPath) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = FoldTheme.colors

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
    ) {
        item {
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClear = viewModel::clearQuery,
                )
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchScope.entries.forEach { scope ->
                    FoldScopeChip(
                        label = scope.label(),
                        selected = state.scope == scope,
                        onSelect = { viewModel.setScope(scope) },
                    )
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.dividerStrong, FoldRules.sectionDivider)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // CONTENTS first: it is the filter with a real cost, and burying it
                // behind four cheap ones would hide that.
                FoldFilterChip(
                    label = stringResource(R.string.search_filter_contents),
                    count = if (state.searchContents) {
                        stringResource(R.string.search_on)
                    } else {
                        stringResource(R.string.search_off)
                    },
                    checked = state.searchContents,
                    onToggle = viewModel::toggleContents,
                )
                listOf(
                    FileCategory.DOCUMENTS to R.string.search_filter_docs,
                    FileCategory.IMAGES to R.string.search_filter_images,
                    FileCategory.VIDEO to R.string.search_filter_video,
                    FileCategory.ARCHIVES to R.string.search_filter_archives,
                ).forEach { (category, label) ->
                    FoldFilterChip(
                        label = stringResource(label),
                        count = "",
                        checked = category in state.categories,
                        onToggle = { viewModel.toggleCategory(category, it) },
                    )
                }
            }
        }

        item {
            SectionHeading(
                text = if (state.query.isBlank()) {
                    stringResource(R.string.search_prompt)
                } else {
                    stringResource(R.string.search_matches, state.results.size)
                },
                trailing = {
                    // The measured timing, with the size of the corpus it searched.
                    Text(
                        text = stringResource(
                            R.string.search_timing,
                            state.elapsedMillis,
                            Formatting.count(state.indexedFiles),
                        ),
                        style = FoldTheme.typography.meta,
                        color = colors.onBackgroundMuted,
                        maxLines = 1,
                    )
                },
            )
        }

        if (state.scope == SearchScope.VAULT) {
            item {
                com.jeelgajera.fold.core.design.component.FoldCallout(
                    tag = stringResource(R.string.search_vault_locked_tag),
                    text = stringResource(R.string.search_vault_locked),
                )
            }
        }

        items(state.results, key = { "${it.entry.path}:${it.contentMatch?.lineNumber ?: 0}" }) { result ->
            ResultRow(
                result = result,
                query = state.query,
                onClick = { onOpenPath(FsPath.raw(result.entry.parentPath)) },
            )
        }

        if (state.query.isNotBlank() && state.results.isEmpty() && !state.searching) {
            item {
                Text(
                    stringResource(R.string.search_no_matches, state.query),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = FoldTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .foldGlass(
                fill = colors.onBackground.copy(alpha = 0.07f),
                opaqueFallback = colors.surface,
                borderColor = colors.onBackground.copy(alpha = 0.16f),
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FoldIcon(FoldIconPaths.Search, tint = colors.accent, strokeWidth = 1.8f, size = 17.dp)

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = FoldTheme.typography.body.copy(
                color = colors.onBackground,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
            ),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_placeholder),
                        style = FoldTheme.typography.body.copy(fontSize = 16.sp),
                        color = colors.onBackgroundMuted,
                        maxLines = 1,
                    )
                }
                inner()
            },
        )

        BlinkingCaret()

        // The clear control keeps its 44dp target and only fades, so the row does
        // not reflow the moment the field goes from empty to non-empty.
        Box(Modifier.alpha(if (query.isEmpty()) 0f else 1f)) {
            FoldIconButton(
                contentDescription = stringResource(R.string.search_clear),
                onClick = onClear,
                size = 44.dp,
            ) {
                FoldIcon(
                    FoldIconPaths.Close,
                    tint = colors.onBackground.copy(alpha = 0.55f),
                    strokeWidth = 1.8f,
                    size = 14.dp,
                )
            }
        }
    }
}

/**
 * The blinking mark beside the field.
 *
 * Under reduced motion it holds steady instead of blinking, which the token
 * file's reduced-motion contract requires by name.
 */
@Composable
private fun BlinkingCaret() {
    val colors = FoldTheme.colors
    if (FoldTheme.reducedMotion) {
        Box(Modifier.width(2.dp).height(19.dp).background(colors.accent))
        return
    }

    val transition = rememberInfiniteTransition(label = "SearchCaret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = FoldMotion.Linear),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "SearchCaretAlpha",
    )
    Box(Modifier.width(2.dp).height(19.dp).alpha(alpha).background(colors.accent))
}

@Composable
private fun ResultRow(result: SearchResult, query: String, onClick: () -> Unit) {
    val colors = FoldTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .androidxClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypeBadge(mark = result.entry.extension.uppercase().take(4).ifEmpty { "?" })
            Column(Modifier.weight(1f)) {
                Text(
                    highlight(result.entry.name, query, colors.accent, colors.accentInk),
                    style = FoldTheme.typography.body,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Formatting.bytes(result.entry.sizeBytes)} · ${result.entry.parentPath}",
                    style = FoldTheme.typography.meta,
                    color = colors.onBackgroundMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (result.contentMatch != null) {
                    stringResource(R.string.search_where_contents)
                } else {
                    stringResource(R.string.search_where_name)
                },
                style = FoldTheme.typography.labelS,
                color = colors.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
            )
        }

        result.contentMatch?.let { match ->
            Row(
                Modifier
                    .padding(start = 50.dp, top = 12.dp)
                    .fillMaxWidth()
                    .background(colors.onBackground.copy(alpha = 0.05f))
                    .accentEdge(colors.accent)
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.search_line, match.lineNumber),
                    style = FoldTheme.typography.metaS,
                    color = colors.onBackground.copy(alpha = 0.45f),
                    maxLines = 1,
                )
                Text(
                    highlight(match.line, query, colors.accent, colors.accentInk),
                    style = FoldTheme.typography.meta,
                    color = colors.onBackground.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Marks every occurrence of [query] in [text] with an accent-filled span. */
private fun highlight(
    text: String,
    query: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var index = 0
        val haystack = text.lowercase()
        val needle = query.lowercase()
        while (true) {
            val found = haystack.indexOf(needle, index)
            if (found < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, found))
            withStyle(SpanStyle(background = background, color = foreground)) {
                append(text.substring(found, found + query.length))
            }
            index = found + query.length
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit) =
    androidx.compose.foundation.clickable(onClick = onClick)

@Composable
private fun SearchScope.label(): String = stringResource(
    when (this) {
        SearchScope.ALL_STORAGE -> R.string.search_scope_all
        SearchScope.THIS_FOLDER -> R.string.search_scope_folder
        SearchScope.VAULT -> R.string.search_scope_vault
    }
)
