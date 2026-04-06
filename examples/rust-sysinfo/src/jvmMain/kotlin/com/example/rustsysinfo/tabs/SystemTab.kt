package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun SystemTab(info: SystemInfo?, loadAvg: LoadAvg?) {
    if (info == null) {
        CircularProgressIndicator(color = AppColors.accent)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader("Operating System")
        }
        item {
            InfoCard {
                MetricRow("Name", info.name)
                MetricRow("OS Version", info.osVersion)
                if (info.longOsVersion != null) {
                    MetricRow("Full OS Version", info.longOsVersion)
                }
                MetricRow("Kernel", info.kernelVersion)
                MetricRow("Kernel (full)", info.kernelLongVersion)
                MetricRow("Hostname", info.hostname)
                MetricRow("Architecture", info.cpuArch)
                if (info.distributionId.isNotEmpty()) {
                    MetricRow("Distribution", info.distributionId)
                }
                if (info.distributionIdLike.isNotEmpty()) {
                    MetricRow("Based On", info.distributionIdLike.joinToString(", "))
                }
            }
        }

        item {
            SectionHeader("Runtime")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Uptime", formatDuration(info.uptime), Modifier.weight(1f), AppColors.green)
                StatBox("Cores", info.physicalCores?.toString() ?: "N/A", Modifier.weight(1f), AppColors.cyan)
                StatBox("FD Limit", info.openFilesLimit?.let { formatNumber(it) } ?: "N/A", Modifier.weight(1f), AppColors.purple)
            }
        }

        if (loadAvg != null) {
            item { SectionHeader("Load Average") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("1 min", "%.2f".format(loadAvg.one), Modifier.weight(1f), AppColors.accent)
                    StatBox("5 min", "%.2f".format(loadAvg.five), Modifier.weight(1f), AppColors.accent)
                    StatBox("15 min", "%.2f".format(loadAvg.fifteen), Modifier.weight(1f), AppColors.accent)
                }
            }
        }

        item { SectionHeader("Product") }
        item {
            InfoCard {
                MetricRow("Product", Product.name() ?: "N/A")
                MetricRow("Family", Product.family() ?: "N/A")
                MetricRow("Vendor", Product.vendor_name() ?: "N/A")
                MetricRow("Version", Product.version() ?: "N/A")
            }
        }

        item { SectionHeader("Motherboard") }
        item {
            val mb = Motherboard.new()
            if (mb != null) {
                InfoCard {
                    MetricRow("Name", mb.name() ?: "N/A")
                    MetricRow("Vendor", mb.vendor_name() ?: "N/A")
                    MetricRow("Version", mb.version() ?: "N/A")
                    MetricRow("Serial", mb.serial_number() ?: "N/A")
                }
                mb.close()
            } else {
                InfoCard {
                    MiniLabel("Not available")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
