package com.sukisu.ultra.ui.screen.dynamicmanager

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
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.screen.susfs.component.miuix.DialogFieldMiuix
import com.sukisu.ultra.ui.screen.susfs.component.miuix.UniversalDialogMiuix
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun DynamicManagerMiuix(
    state: DynamicManagerUiState,
    actions: DynamicManagerActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.dynamic_manager_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    val statusTitle = stringResource(R.string.dynamic_manager_status_title)
                    ArrowPreference(
                        title = statusTitle,
                        summary = when {
                            state.isLoading -> stringResource(R.string.dynamic_manager_loading)
                            state.currentSignature.isBlank() -> stringResource(R.string.dynamic_manager_not_set)
                            else -> state.currentSignature
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.Security,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = statusTitle,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { actions.onRefresh() }
                    )
                }

                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    val title = stringResource(R.string.dynamic_manager_from_apk)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.dynamic_manager_from_apk_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.Android,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = title,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { actions.onPickApk() }
                    )
                }

                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    val title = stringResource(R.string.dynamic_manager_manual)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.dynamic_manager_manual_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.Edit,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = title,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { actions.onOpenManualDialog() }
                    )
                }

                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    val title = stringResource(R.string.dynamic_manager_clear)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.dynamic_manager_clear_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.Delete,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = title,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { actions.onOpenClearConfirm() }
                    )
                }

                Text(
                    text = stringResource(R.string.dynamic_manager_note),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (state.manualDialogVisible) {
        ManualSetDialogMiuix(
            onDismiss = actions.onDismissManualDialog,
            onApply = actions.onApplyManual,
        )
    }
}

@Composable
private fun ManualSetDialogMiuix(
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
) {
    var sizeText by remember { mutableStateOf("") }
    var hashText by remember { mutableStateOf("") }

    UniversalDialogMiuix(
        showDialog = true,
        onDismiss = onDismiss,
        onConfirm = {
            onApply(sizeText, hashText)
            true
        },
        titleRes = R.string.dynamic_manager_manual,
        fields = listOf(
            DialogFieldMiuix.TextField(
                value = sizeText,
                onValueChange = { sizeText = it },
                labelRes = R.string.dynamic_manager_size_label,
            ),
            DialogFieldMiuix.TextField(
                value = hashText,
                onValueChange = { hashText = it },
                labelRes = R.string.dynamic_manager_hash_label,
            ),
        ),
        confirmTextRes = R.string.dynamic_manager_apply,
        cancelTextRes = R.string.cancel,
    )
}
