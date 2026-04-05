package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun NetworkTab(networks: List<NetworkInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Network Interfaces", networks.size) }

        items(networks) { net ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Badge(net.name, color = AppColors.textPrimary)
                    Badge("MTU ${net.mtu}", color = AppColors.textMuted)
                }
                Spacer(Modifier.height(4.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Rx Total", formatBytes(net.totalReceived), Modifier.weight(1f), AppColors.green)
                    StatBox("Tx Total", formatBytes(net.totalTransmitted), Modifier.weight(1f), AppColors.cyan)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Rx/s", "${formatBytes(net.received)}/s", Modifier.weight(1f), AppColors.green)
                    StatBox("Tx/s", "${formatBytes(net.transmitted)}/s", Modifier.weight(1f), AppColors.cyan)
                }

                MetricRow("Packets Rx (delta)", formatNumber(net.packetsReceived))
                MetricRow("Packets Tx (delta)", formatNumber(net.packetsTransmitted))
                MetricRow("Total Packets Rx", formatNumber(net.totalPacketsReceived))
                MetricRow("Total Packets Tx", formatNumber(net.totalPacketsTransmitted))

                if (net.errorsReceived > 0 || net.errorsTransmitted > 0) {
                    MetricRow("Errors Rx/Tx (delta)", "${net.errorsReceived} / ${net.errorsTransmitted}")
                }
                if (net.totalErrorsReceived > 0 || net.totalErrorsTransmitted > 0) {
                    MetricRow("Total Errors Rx/Tx", "${net.totalErrorsReceived} / ${net.totalErrorsTransmitted}")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
