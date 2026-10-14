package com.sukisu.ultra.ui.screen.home

import androidx.compose.runtime.Immutable
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.KernelVersion
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import kotlin.math.abs

@Immutable
data class HomeUiState(
    val kernelVersion: KernelVersion,
    val ksuVersion: Int?,
    val managerUAPIVersion: Int,
    val kernelUAPIVersion: Int?,
    val lkmMode: Boolean?,
    val isLkmBundled: Boolean,
    val isManager: Boolean,
    val isManagerPrBuild: Boolean,
    val isKernelPrBuild: Boolean,
    val requiresNewKernel: Boolean,
    val requiresNewManager: Boolean,
    val isRootAvailable: Boolean,
    val isSafeMode: Boolean,
    val isLateLoadMode: Boolean,
    val checkUpdateEnabled: Boolean,
    val latestVersionInfo: LatestVersionInfo,
    val currentManagerVersionCode: Long,
    val systemInfo: SystemInfo,
    val showFullStatus: Boolean = true,
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    val showLkmUpdate: Boolean
        get() {
            if (!isManager || lkmMode != true || !isLkmBundled) return false
            if (requiresNewKernel || requiresNewManager) return false
            // If the running kernel was built from the same kernel-source
            // commit as this manager APK, a manager-only update must not
            // prompt for a kernel reflash.
            val kernelFull = systemInfo.kernelFullVersion
            if (!kernelFull.isNullOrBlank()) {
                val kernelCommit = kernelFull.substringAfterLast('-').substringBefore('@')
                if (kernelCommit.isNotBlank() && kernelCommit == BuildConfig.KERNEL_COMMIT) {
                    return false
                }
            }
            // Same commit can yield a 1-off versionCode: the kernel counts
            // commits via the GitHub API while the manager uses git rev-list.
            // Only warn when the gap is larger than that known skew.
            val kernel = ksuVersion ?: return false
            return abs(kernel.toLong() - currentManagerVersionCode) > 1L
        }

    // Jailbreak mode runs on locked bootloaders, so flashing a boot image would brick the device.
    val canInstallKernelUpdate: Boolean
        get() = lkmMode == true && !isLateLoadMode

    val showCustomLkmBadge: Boolean
        get() = lkmMode == true && !isLkmBundled

    val showRootWarning: Boolean
        get() = ksuVersion != null && !isRootAvailable

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild

    val hasUpdate: Boolean
        get() = latestVersionInfo.versionCode > currentManagerVersionCode
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit = {},
)
