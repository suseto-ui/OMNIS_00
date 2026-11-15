package com.example.ui.audit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CascadeRiskSimulatorEngine
import com.example.engine.DomainTensionState
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CascadeRiskSimulatorView(
    modifier: Modifier = Modifier
) {
    var domainWeights by remember {
        mutableStateOf(
            mapOf(
                "SYS" to 0.48f,
                "ECON" to 0.52f,
                "PSYCH" to 0.45f,
                "ECO" to 0.50f,
                "LAW" to 0.62f,
                "SEC" to 0.58f,
                "PHYS" to 0.40f,
                "SOC" to 0.50f
            )
        )
    }

    val riskState = remember(domainWeights) {
        CascadeRiskSimulatorEngine.evaluateRiskState(domainWeights)
    }

    val badgeColor = when {
        riskState.isCircuitBreakerTripped -> Color.Red
        riskState.isForensicModeActive -> Color(0xFFF97316)
        riskState.systemicTensionIndex >= 0.45f -> OmnisAmber
        else -> OmnisEmerald
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("cascade_simulator_container")
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "SIMULÁTOR KASKÁDOVÝCH RIZIK & 8D TENZE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "8 DOMÉNOVÝCH SÍŤOVÝCH VEKTORŮ | BARYCENTRUM ROVNOVÁHY",
                        color = OmnisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
            ) {
                Text(
                    text = riskState.riskLevel,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Dynamic Octagon Tension Canvas Card
            item {
                OctagonTensionCard(
                    domainWeights = domainWeights,
                    tensionIndex = riskState.systemicTensionIndex,
                    badgeColor = badgeColor
                )
            }

            // 2. Stabilization Control Button
            item {
                Button(
                    onClick = {
                        domainWeights = CascadeRiskSimulatorEngine.computeStabilizationVector(domainWeights)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("run_8d_stabilization_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SPUSTIT 8D STABILIZACI TENZE (BARYCENTRUM)",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 3. 8 Domain Tension Sliders
            item {
                Text(
                    text = "INTERAKTIVNÍ NASTAVENÍ PNUTÍ DOMÉN (0.0 - 1.0):",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CascadeRiskSimulatorEngine.DOMAINS.forEach { domain ->
                            val currentWeight = domainWeights[domain] ?: 0.5f
                            DomainSliderRow(
                                domain = domain,
                                value = currentWeight,
                                onValueChange = { newVal ->
                                    val updated = domainWeights.toMutableMap()
                                    updated[domain] = newVal
                                    domainWeights = updated
                                }
                            )
                        }
                    }
                }
            }

            // 4. Rebalancing Recommendations
            item {
                Text(
                    text = "DOPORUČENÉ DIVERSIFIKAČNÍ A STABILIZAČNÍ ZÁSAHY:",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            items(riskState.rebalancingActions) { actionText ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = OmnisEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = actionText,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OctagonTensionCard(
    domainWeights: Map<String, Float>,
    tensionIndex: Float,
    badgeColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIAGRAM 8D TENZOROVÉHO NAPĚTÍ",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "T_sys = ${String.format(java.util.Locale.US, "%.3f", tensionIndex)}",
                    color = badgeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Octagon Canvas
            OctagonCanvas(
                weights = domainWeights,
                badgeColor = badgeColor,
                modifier = Modifier
                    .size(200.dp)
                    .testTag("octagon_tension_canvas")
            )
        }
    }
}

@Composable
private fun OctagonCanvas(
    weights: Map<String, Float>,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.width / 2f) * 0.82f

        val domains = CascadeRiskSimulatorEngine.DOMAINS
        val numAxes = domains.size
        val angleStep = (2 * Math.PI / numAxes).toFloat()

        // 1. Concentric reference octagons (0.25, 0.50, 0.75, 1.00)
        for (step in 1..4) {
            val r = radius * (step / 4f)
            val path = Path()
            for (i in 0 until numAxes) {
                val angle = i * angleStep - Math.PI.toFloat() / 2f
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.12f),
                style = Stroke(width = 1f)
            )
        }

        // 2. Axes lines
        for (i in 0 until numAxes) {
            val angle = i * angleStep - Math.PI.toFloat() / 2f
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = center,
                end = Offset(x, y),
                strokeWidth = 1f
            )
        }

        // 3. Current Tension Web Polygon
        val tensionPath = Path()
        domains.forEachIndexed { i, domain ->
            val angle = i * angleStep - Math.PI.toFloat() / 2f
            val weight = (weights[domain] ?: 0.5f).coerceIn(0.1f, 1.0f)
            val r = radius * weight
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            if (i == 0) tensionPath.moveTo(x, y) else tensionPath.lineTo(x, y)
        }
        tensionPath.close()

        drawPath(
            path = tensionPath,
            color = badgeColor.copy(alpha = 0.30f)
        )
        drawPath(
            path = tensionPath,
            color = badgeColor,
            style = Stroke(width = 2.5f)
        )

        // 4. Vertex points
        domains.forEachIndexed { i, domain ->
            val angle = i * angleStep - Math.PI.toFloat() / 2f
            val weight = (weights[domain] ?: 0.5f).coerceIn(0.1f, 1.0f)
            val r = radius * weight
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            drawCircle(
                color = badgeColor,
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun DomainSliderRow(
    domain: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val barColor = when {
        value >= 0.85f -> Color.Red
        value >= 0.70f -> Color(0xFFF97316)
        value >= 0.45f -> OmnisAmber
        else -> OmnisEmerald
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = domain,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.0f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = barColor,
                activeTrackColor = barColor,
                inactiveTrackColor = OmnisBgDark
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("slider_domain_$domain")
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${(value * 100).toInt()}%",
            color = barColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(36.dp)
        )
    }
}
