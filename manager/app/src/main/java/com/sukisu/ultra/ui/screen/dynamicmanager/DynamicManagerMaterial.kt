package com.sukisu.ultra.ui.screen.dynamicmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveDialog
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors

@Composable
fun DynamicManagerMaterial(
    state: DynamicManagerUiState,
    actions: DynamicManagerActions,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dynamic_manager_title)) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                scrollBehavior = scrollBehavior,
                colors = expressiveTopAppBarColors(),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            contentPadding = innerPadding,
        ) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    content = listOf({
                        val statusTitle = stringResource(R.string.dynamic_manager_status_title)
                        SegmentedListItem(
                            onClick = actions.onRefresh,
                            headlineContent = { Text(statusTitle) },
                            supportingContent = {
                                Text(
                                    when {
                                        state.isLoading -> stringResource(R.string.dynamic_manager_loading)
                                        state.currentSignature.isBlank() -> stringResource(R.string.dynamic_manager_not_set)
                                        else -> state.currentSignature
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Security,
                                    statusTitle,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingContent = {
                                Icon(Icons.Rounded.Refresh, null)
                            }
                        )
                    })
                )

                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    content = listOf({
                        val title = stringResource(R.string.dynamic_manager_from_apk)
                        SegmentedListItem(
                            onClick = actions.onPickApk,
                            headlineContent = { Text(title) },
                            supportingContent = { Text(stringResource(R.string.dynamic_manager_from_apk_summary)) },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Android,
                                    title,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    })
                )

                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    content = listOf({
                        val title = stringResource(R.string.dynamic_manager_manual)
                        SegmentedListItem(
                            onClick = actions.onOpenManualDialog,
                            headlineContent = { Text(title) },
                            supportingContent = { Text(stringResource(R.string.dynamic_manager_manual_summary)) },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Edit,
                                    title,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    })
                )

                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    content = listOf({
                        val title = stringResource(R.string.dynamic_manager_clear)
                        SegmentedListItem(
                            onClick = actions.onOpenClearConfirm,
                            headlineContent = { Text(title) },
                            supportingContent = { Text(stringResource(R.string.dynamic_manager_clear_summary)) },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    title,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    })
                )

                Text(
                    text = stringResource(R.string.dynamic_manager_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                )
            }
        }
    }

    if (state.manualDialogVisible) {
        ManualSetDialogMaterial(
            onDismiss = actions.onDismissManualDialog,
            onApply = actions.onApplyManual,
        )
    }
}

@Composable
private fun ManualSetDialogMaterial(
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
) {
    var sizeText by remember { mutableStateOf("") }
    var hashText by remember { mutableStateOf("") }

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dynamic_manager_manual)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it },
                    label = { Text(stringResource(R.string.dynamic_manager_size_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hashText,
                    onValueChange = { hashText = it },
                    label = { Text(stringResource(R.string.dynamic_manager_hash_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(sizeText, hashText) }) {
                Text(stringResource(R.string.dynamic_manager_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
