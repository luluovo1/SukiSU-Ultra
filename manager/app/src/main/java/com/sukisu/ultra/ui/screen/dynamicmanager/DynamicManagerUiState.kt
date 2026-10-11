package com.sukisu.ultra.ui.screen.dynamicmanager

import androidx.compose.runtime.Immutable

@Immutable
data class DynamicManagerUiState(
    val isLoading: Boolean = true,
    val currentSignature: String = "",
    val manualDialogVisible: Boolean = false,
    val clearConfirmVisible: Boolean = false,
)

@Immutable
data class DynamicManagerActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onPickApk: () -> Unit = {},
    val onOpenManualDialog: () -> Unit = {},
    val onDismissManualDialog: () -> Unit = {},
    val onApplyManual: (String, String) -> Unit = { _, _ -> },
    val onOpenClearConfirm: () -> Unit = {},
    val onDismissClearConfirm: () -> Unit = {},
    val onConfirmClear: () -> Unit = {},
)
