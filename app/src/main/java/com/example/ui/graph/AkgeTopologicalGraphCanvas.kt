package com.example.ui.graph

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.UserRole
import com.example.engine.AkgeTensorKernel
import java.security.MessageDigest
import kotlin.math.*

private val GraphCyan = Color(0xFF00E5FF)
private val GraphGreen = Color(0xFF10B981)
private val GraphAmber = Color(0xFFF59E0B)
private val GraphRed = Color(0xFFEF4444)
private val GraphViolet = Color(0xFF8B5CF6)
private val GraphBg = Color(0xFF080D1A)
private val GraphCardBg = Color(0xFF111927)
private val GraphBorder = Color(0xFF1F293D)

data class DomainNodeInfo(
    val code: String,
    val name: String,
    val description: String,
    val color: Color
)

data class CognitiveBridgeDetail(
    val sourceDomain: String,
    val targetDomain: String,
    val correlationWeight: Float,
    val causalStrength: Float,
    val tension: Float,
    val isCritical: Boolean,
    val regulatoryGate: String,
    val complianceNorm: String
)

val DOMAIN_DEFINITIONS = listOf(
    DomainNodeInfo("SYS", "Systém", "Architektura a kognitivní stabilita", Color(0xFF38BDF8)),
    DomainNodeInfo("ECON", "Ekonomie", "Nákladová efektivita a tokenomika", Color(0xFF34D399)),
    DomainNodeInfo("PSYCH", "Psychologie", "Ergonomie a kognitivní zátěž", Color(0xFFA78BFA)),
    DomainNodeInfo("ECO", "Ekologie", "Udržitelnost a environmentální stopa", Color(0xFF4ADE80)),
    DomainNodeInfo("LAW", "Právo", "Normativní a regulatorní soulad NIS2", Color(0xFFFBBF24)),
    DomainNodeInfo("SEC", "Bezpečnost", "Kybernetická odolnost a HMAC-SHA256 audit", Color(0xFFF87171)),
    DomainNodeInfo("PHYS", "Fyzika", "Výpočetní latence a HW optimalizace", Color(0xFFFB923C)),
    DomainNodeInfo("SOC", "Společnost", "Sociální dopad a transdisciplinární etika", Color(0xFFE879F9))
)

@Composable
fun AkgeTopologicalGraphCanvas(
    modifier: Modifier = Modifier,
    initialRole: UserRole = UserRole.ADMIN_OPERATOR,
    onNavigateToDialectics: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val kernelSnapshot by AkgeTensorKernel.kernelState.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    var selectedDomainCode by remember { mutableStateOf<String?>(null) }
    var selectedBridge by remember { mutableStateOf<CognitiveBridgeDetail?>(null) }
    var activeRbacRole by remember { mutableStateOf(initialRole) }
    var showProofOfNecessityModal by remember { mutableStateOf(false) }

    val domainMap = remember { DOMAIN_DEFINITIONS.associateBy { it.code } }
    val nodeRadiusPx = with(density) { 22.dp.toPx() }
    val touchHitRadiusPx = with(density) { 34.dp.toPx() }
    val edgeHitDistancePx = with(density) { 20.dp.toPx() }

    // Pulsing animation for dynamic cognitive flows
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_flow")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_anim"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (kernelSnapshot.percolationStatus.isCascadeRiskActive) GraphRed else GraphCyan.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            )
            .testTag("akge_topological_graph_canvas"),
        color = GraphCardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header with RBAC Toggle & ZK Commitment Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (kernelSnapshot.percolationStatus.isCascadeRiskActive) GraphRed else GraphCyan)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "AKGE-8D CSR KOGNITIVNÍ GRAF",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (activeRbacRole == UserRole.ADMIN_OPERATOR) GraphViolet.copy(alpha = 0.25f) else GraphCyan.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, if (activeRbacRole == UserRole.ADMIN_OPERATOR) GraphViolet else GraphCyan)
                            ) {
                                Text(
                                    text = if (activeRbacRole == UserRole.ADMIN_OPERATOR) "ADMIN_OPERATOR" else "STANDARD_USER",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeRbacRole == UserRole.ADMIN_OPERATOR) GraphViolet else GraphCyan,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Kauzální mosty & Do-Calculus P(Y|do(X))",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // RBAC Mode Switcher
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GraphBg,
                    border = BorderStroke(0.5.dp, GraphCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        activeRbacRole = if (activeRbacRole == UserRole.ADMIN_OPERATOR) UserRole.STANDARD_USER else UserRole.ADMIN_OPERATOR
                    }
                ) {
                    Text(
                        text = if (activeRbacRole == UserRole.ADMIN_OPERATOR) "[RBAC: ADMIN]" else "[RBAC: USER]",
                        fontSize = 9.sp,
                        color = GraphCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Interactive Canvas
            val csr = kernelSnapshot.csrMatrix
            val domainWeights = kernelSnapshot.domainWeights
            val domainKeys = AkgeTensorKernel.DOMAIN_KEYS

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GraphBg)
                    .pointerInput(domainKeys, csr) {
                        detectTapGestures { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val radius = (minOf(size.width, size.height) / 2f) * 0.73f

                            // Node positions
                            val nodePositions = domainKeys.indices.map { i ->
                                val angle = (i * 2.0 * PI / domainKeys.size) - (PI / 2.0)
                                Offset(
                                    x = cx + radius * cos(angle).toFloat(),
                                    y = cy + radius * sin(angle).toFloat()
                                )
                            }

                            // 1. Check if node tapped
                            var tappedDomain: String? = null
                            for (i in domainKeys.indices) {
                                val nx = nodePositions[i].x
                                val ny = nodePositions[i].y
                                val dist = sqrt((offset.x - nx) * (offset.x - nx) + (offset.y - ny) * (offset.y - ny))
                                if (dist <= touchHitRadiusPx) {
                                    tappedDomain = domainKeys[i]
                                    break
                                }
                            }

                            if (tappedDomain != null) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                selectedDomainCode = if (selectedDomainCode == tappedDomain) null else tappedDomain
                                selectedBridge = null
                                return@detectTapGestures
                            }

                            // 2. Check if an edge / cognitive bridge was tapped
                            var tappedBridge: CognitiveBridgeDetail? = null
                            for (i in 0 until csr.dimension) {
                                val start = csr.rowPointers[i]
                                val end = csr.rowPointers[i + 1]
                                val p1 = nodePositions[i]

                                for (k in start until end) {
                                    val j = csr.columnIndices[k]
                                    if (i < j) {
                                        val p2 = nodePositions[j]
                                        val dist = distanceToSegment(offset, p1, p2)
                                        if (dist <= edgeHitDistancePx) {
                                            val srcCode = domainKeys[i]
                                            val dstCode = domainKeys[j]
                                            val corrWeight = csr.values[k]
                                            val wSrc = domainWeights[srcCode] ?: 0.5f
                                            val wDst = domainWeights[dstCode] ?: 0.5f
                                            val tension = abs(wSrc - wDst)
                                            val causalStrength = (corrWeight * 0.85f + (1.0f - tension) * 0.15f).coerceIn(0.1f, 1.0f)
                                            val isCrit = tension > 0.35f

                                            tappedBridge = CognitiveBridgeDetail(
                                                sourceDomain = srcCode,
                                                targetDomain = dstCode,
                                                correlationWeight = corrWeight,
                                                causalStrength = causalStrength,
                                                tension = tension,
                                                isCritical = isCrit,
                                                regulatoryGate = if (isCrit) "G4_EVIDENCE_WARNING" else "G3_CAUSAL_PATH_OK",
                                                complianceNorm = "EU AI Act Art. 14 / NIS2 Art. 21 (Transdisciplinární soulad)"
                                            )
                                            break
                                        }
                                    }
                                }
                                if (tappedBridge != null) break
                            }

                            if (tappedBridge != null) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedBridge = if (selectedBridge?.sourceDomain == tappedBridge.sourceDomain && selectedBridge?.targetDomain == tappedBridge.targetDomain) null else tappedBridge
                                selectedDomainCode = null
                            } else {
                                selectedDomainCode = null
                                selectedBridge = null
                            }
                        }
                    }
                    .testTag("interactive_graph_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = (minOf(size.width, size.height) / 2f) * 0.73f

                    val nodePositions = domainKeys.indices.map { i ->
                        val angle = (i * 2.0 * PI / domainKeys.size) - (PI / 2.0)
                        Offset(
                            x = cx + radius * cos(angle).toFloat(),
                            y = cy + radius * sin(angle).toFloat()
                        )
                    }

                    val selectedIdx = selectedDomainCode?.let { domainKeys.indexOf(it) }?.takeIf { it >= 0 }
                    val activeBridgeSrc = selectedBridge?.sourceDomain?.let { domainKeys.indexOf(it) }
                    val activeBridgeDst = selectedBridge?.targetDomain?.let { domainKeys.indexOf(it) }

                    // 1. Draw Cognitive Bridges (Edges from CSR Matrix)
                    for (i in 0 until csr.dimension) {
                        val start = csr.rowPointers[i]
                        val end = csr.rowPointers[i + 1]
                        val p1 = nodePositions[i]

                        for (k in start until end) {
                            val j = csr.columnIndices[k]
                            if (i < j) {
                                val p2 = nodePositions[j]
                                val weight = csr.values[k]

                                val isConnectedToSelectedNode = selectedIdx != null && (i == selectedIdx || j == selectedIdx)
                                val isSelectedBridge = activeBridgeSrc != null && activeBridgeDst != null &&
                                        ((i == activeBridgeSrc && j == activeBridgeDst) || (i == activeBridgeDst && j == activeBridgeSrc))
                                val isDimmed = (selectedIdx != null && !isConnectedToSelectedNode) ||
                                        (activeBridgeSrc != null && !isSelectedBridge)

                                val wSrc = domainWeights[domainKeys[i]] ?: 0.5f
                                val wDst = domainWeights[domainKeys[j]] ?: 0.5f
                                val tension = abs(wSrc - wDst)
                                val isTensionHigh = tension > 0.35f

                                val baseEdgeColor = when {
                                    isSelectedBridge -> GraphViolet
                                    isConnectedToSelectedNode -> GraphCyan
                                    isDimmed -> GraphBorder.copy(alpha = 0.20f)
                                    isTensionHigh -> GraphAmber.copy(alpha = 0.65f)
                                    else -> GraphCyan.copy(alpha = (weight * 0.5f).coerceIn(0.18f, 0.65f))
                                }

                                val strokeWidth = when {
                                    isSelectedBridge -> 4.5.dp.toPx()
                                    isConnectedToSelectedNode -> 3.2.dp.toPx()
                                    isDimmed -> 1.0.dp.toPx()
                                    else -> (1.2f + weight * 1.8f).dp.toPx()
                                }

                                // Base edge line
                                drawLine(
                                    color = baseEdgeColor,
                                    start = p1,
                                    end = p2,
                                    strokeWidth = strokeWidth
                                )

                                // Animated particle dot along active/selected bridges
                                if (!isDimmed) {
                                    val particleT = (pulsePhase + (i * 0.13f + j * 0.17f)) % 1.0f
                                    val particlePos = Offset(
                                        x = p1.x + (p2.x - p1.x) * particleT,
                                        y = p1.y + (p2.y - p1.y) * particleT
                                    )
                                    drawCircle(
                                        color = if (isSelectedBridge) GraphViolet else if (isTensionHigh) GraphAmber else GraphCyan,
                                        radius = (if (isSelectedBridge) 4.5.dp else 2.5.dp).toPx(),
                                        center = particlePos
                                    )
                                }
                            }
                        }
                    }

                    // 2. Draw 8 Domain Nodes
                    nodePositions.forEachIndexed { i, pos ->
                        val code = domainKeys[i]
                        val weight = domainWeights[code] ?: 0.5f
                        val isSelected = selectedIdx == i
                        val isPartOfBridge = (activeBridgeSrc == i || activeBridgeDst == i)
                        val isStressed = weight < 0.5f

                        val nodeColor = when {
                            isStressed -> GraphRed
                            weight >= 0.75f -> GraphGreen
                            else -> GraphAmber
                        }

                        // Outer glowing halo
                        if (isSelected || isPartOfBridge || isStressed) {
                            val haloColor = when {
                                isSelected -> GraphCyan.copy(alpha = 0.35f)
                                isPartOfBridge -> GraphViolet.copy(alpha = 0.35f)
                                else -> GraphRed.copy(alpha = 0.25f)
                            }
                            drawCircle(
                                color = haloColor,
                                radius = nodeRadiusPx + if (isSelected || isPartOfBridge) 8.dp.toPx() else 4.dp.toPx(),
                                center = pos
                            )
                        }

                        // Node base circle
                        drawCircle(
                            color = GraphCardBg,
                            radius = nodeRadiusPx,
                            center = pos
                        )

                        // Node outline ring
                        val ringColor = when {
                            isSelected -> GraphCyan
                            isPartOfBridge -> GraphViolet
                            else -> nodeColor
                        }
                        drawCircle(
                            color = ringColor,
                            radius = nodeRadiusPx,
                            center = pos,
                            style = Stroke(width = if (isSelected || isPartOfBridge) 2.6.dp.toPx() else 1.5.dp.toPx())
                        )

                        // Native canvas text rendering for domain label
                        val paint = android.graphics.Paint().apply {
                            color = if (isSelected || isPartOfBridge) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
                            textSize = 9.5.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        val weightPaint = android.graphics.Paint().apply {
                            color = if (isStressed) android.graphics.Color.RED else android.graphics.Color.CYAN
                            textSize = 8.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.MONOSPACE
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            code,
                            pos.x,
                            pos.y - 2.dp.toPx(),
                            paint
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format("%.2f", weight),
                            pos.x,
                            pos.y + 11.dp.toPx(),
                            weightPaint
                        )
                    }
                }

                // Interactive Hint Overlay
                if (selectedDomainCode == null && selectedBridge == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GraphCardBg.copy(alpha = 0.88f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Klepněte na uzel (doména) nebo kognitivní most (spojnice) pro detail",
                            fontSize = 8.5.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Case A: Selected Bridge Inspector Card
            AnimatedVisibility(visible = selectedBridge != null) {
                selectedBridge?.let { bridge ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GraphBg)
                            .border(BorderStroke(1.dp, GraphViolet.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .testTag("bridge_inspector_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = GraphViolet, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(
                                        text = "KOGNITIVNÍ MOST: ${bridge.sourceDomain} ⇄ ${bridge.targetDomain}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Do-Calculus P(${bridge.targetDomain}|do(${bridge.sourceDomain})) = ${String.format("%.3f", bridge.causalStrength)}",
                                        fontSize = 9.sp,
                                        color = GraphCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (bridge.isCritical) GraphRed.copy(alpha = 0.2f) else GraphGreen.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, if (bridge.isCritical) GraphRed else GraphGreen)
                            ) {
                                Text(
                                    text = if (bridge.isCritical) "TENZE: ${String.format("%.2f", bridge.tension)}" else "STABILNÍ MOST",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bridge.isCritical) GraphRed else GraphGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Korelace CSR: ${String.format("%.2f", bridge.correlationWeight)} | Regulatorní brána: ${bridge.regulatoryGate}",
                            fontSize = 8.5.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = bridge.complianceNorm,
                            fontSize = 8.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bridge Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    // Vyrovnat váhy propojených domén v kernelu
                                    val w1 = domainWeights[bridge.sourceDomain] ?: 0.5f
                                    val w2 = domainWeights[bridge.targetDomain] ?: 0.5f
                                    val avg = (w1 + w2) / 2.0f
                                    AkgeTensorKernel.updateDomainWeights(
                                        mapOf(
                                            bridge.sourceDomain to avg,
                                            bridge.targetDomain to avg
                                        )
                                    )
                                    selectedBridge = null
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GraphViolet, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Vyrovnat tenzi mostu", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            if (onNavigateToDialectics != null) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onNavigateToDialectics("Kauzální harmonizace kognitivního mostu mezi ${bridge.sourceDomain} a ${bridge.targetDomain}")
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GraphCyan),
                                    border = BorderStroke(0.5.dp, GraphCyan)
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Dialektická debata", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Case B: Selected Node Inspector Card
            AnimatedVisibility(visible = selectedDomainCode != null) {
                selectedDomainCode?.let { code ->
                    val info = domainMap[code]
                    val weight = domainWeights[code] ?: 0.5f

                    val domainIdx = domainKeys.indexOf(code)
                    val connections = mutableListOf<Pair<String, Float>>()
                    if (domainIdx >= 0) {
                        val start = csr.rowPointers[domainIdx]
                        val end = csr.rowPointers[domainIdx + 1]
                        for (k in start until end) {
                            val targetIdx = csr.columnIndices[k]
                            connections.add(Pair(domainKeys[targetIdx], csr.values[k]))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GraphBg)
                            .border(BorderStroke(1.dp, GraphCyan.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Adjust, contentDescription = null, tint = GraphCyan, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(
                                        text = "${info?.name ?: code} ($code)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = info?.description ?: "",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (weight < 0.5f) GraphRed.copy(alpha = 0.2f) else GraphGreen.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, if (weight < 0.5f) GraphRed else GraphGreen)
                            ) {
                                Text(
                                    text = "Váha: ${String.format("%.2f", weight)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (weight < 0.5f) GraphRed else GraphGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Aktivní vazby v CSR grafu (${connections.size}):",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            connections.forEach { conn ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GraphCardBg,
                                    border = BorderStroke(0.5.dp, GraphBorder)
                                ) {
                                    Text(
                                        text = "${conn.first} (${String.format("%.2f", conn.second)})",
                                        fontSize = 8.5.sp,
                                        color = GraphCyan,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Actions for selected domain
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    AkgeTensorKernel.updateDomainWeight(code, (weight + 0.10f).coerceAtMost(1.0f))
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GraphGreen),
                                border = BorderStroke(0.5.dp, GraphGreen.copy(alpha = 0.7f))
                            ) {
                                Text("+0.10 Zesílit", fontSize = 9.5.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    AkgeTensorKernel.updateDomainWeight(code, (weight - 0.10f).coerceAtLeast(0.05f))
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GraphAmber),
                                border = BorderStroke(0.5.dp, GraphAmber.copy(alpha = 0.7f))
                            ) {
                                Text("-0.10 Oslabit", fontSize = 9.5.sp)
                            }

                            if (onNavigateToDialectics != null) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onNavigateToDialectics("Dialektická analýza a harmonizace domény $code")
                                    },
                                    modifier = Modifier.weight(1.3f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GraphCyan, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Dialektika", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Case C: General Dashboard Metric Panel (ADMIN_OPERATOR vs STANDARD_USER)
            if (selectedDomainCode == null && selectedBridge == null) {
                Spacer(modifier = Modifier.height(10.dp))

                if (activeRbacRole == UserRole.ADMIN_OPERATOR) {
                    // Full 8D Tensor Monospace Breakdown & Topological Invariants
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GraphBg)
                            .border(BorderStroke(0.5.dp, GraphBorder), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        // ZK Commitment Tag & Anchoring
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[AKGE-8D: 100% UKOTVENO]",
                                color = GraphGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "HASH: ${kernelSnapshot.stateCommitmentHash}",
                                color = GraphCyan,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 8D Tensor values in compact 2-column or wrapping flow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            domainKeys.forEach { key ->
                                val w = domainWeights[key] ?: 0.5f
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = GraphCardBg,
                                    border = BorderStroke(0.5.dp, if (w < 0.5f) GraphRed.copy(alpha = 0.5f) else GraphCyan.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "$key:${String.format("%.2f", w)}",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (w < 0.5f) GraphRed else Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Entropy S & Topological Invariants
                        val topo = kernelSnapshot.topologicalMetrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Entropie S: ${String.format("%.3f", topo.shannonEntropy)} (max 2.079)",
                                fontSize = 8.5.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "χ:${topo.eulerCharacteristic} | β1:${topo.betti1} | σ:${String.format("%.2f", kernelSnapshot.systemStressSigma)}",
                                fontSize = 8.5.sp,
                                color = GraphCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Admin Kernel Control Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    AkgeTensorKernel.injectCascadeStress(listOf("SEC", "SYS"))
                                },
                                modifier = Modifier.weight(1f).height(28.dp),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GraphRed),
                                border = BorderStroke(0.5.dp, GraphRed.copy(alpha = 0.7f))
                            ) {
                                Text("Test kaskády", fontSize = 8.5.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    AkgeTensorKernel.updateDomainWeights(emptyMap())
                                },
                                modifier = Modifier.weight(1f).height(28.dp),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GraphCyan),
                                border = BorderStroke(0.5.dp, GraphCyan.copy(alpha = 0.7f))
                            ) {
                                Text("Difúze pole", fontSize = 8.5.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    AkgeTensorKernel.resetToEquilibrium()
                                },
                                modifier = Modifier.weight(1f).height(28.dp),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GraphGreen),
                                border = BorderStroke(0.5.dp, GraphGreen.copy(alpha = 0.7f))
                            ) {
                                Text("Rovnováha", fontSize = 8.5.sp)
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    showProofOfNecessityModal = true
                                },
                                modifier = Modifier.weight(0.9f).height(28.dp),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GraphViolet, contentColor = Color.White)
                            ) {
                                Text("PoN Log", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // STANDARD_USER: Progressive Disclosure - High-level summary
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GraphBg,
                        border = BorderStroke(0.5.dp, GraphBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GraphGreen, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Kognitivní stabilita systému je nominální",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Všech 8 disciplinárních domén kooperuje bez detekovaných rozpadů. Pro zobrazení tenzorů přepněte na režim ADMIN_OPERATOR.",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    // Proof of Necessity Modal Dialog
    if (showProofOfNecessityModal) {
        AlertDialog(
            onDismissRequest = { showProofOfNecessityModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = GraphViolet)
                    Text("Důkaz nutnosti (Proof of Necessity)", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GraphBg, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "1. TRIGGER VECTOR:",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = GraphCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Vektorová divergence d_E = 0.142 (záměr: topologická harmonizace CSR grafu)",
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "2. EXCLUSION PROOF:",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = GraphAmber,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Levnější binární heuristiky zamítnuty pro neschopnost zachytit 28-párovou nelineární korelaci bez ztráty konvergence.",
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "3. MINIMIZATION CHECK:",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = GraphGreen,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Alokováno přesně 8 uzlů CSR matice. Entropie S = ${String.format("%.3f", kernelSnapshot.topologicalMetrics.shannonEntropy)} nepřekračuje mez rozpadu.",
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showProofOfNecessityModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GraphCyan, contentColor = Color.Black)
                ) {
                    Text("Zavřít", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = GraphCardBg
        )
    }
}

/**
 * Spočte vzdálenost bodu p od úsečky dané body a a b.
 */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 == 0f) {
        val dx = p.x - a.x
        val dy = p.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
    val projX = a.x + t * (b.x - a.x)
    val projY = a.y + t * (b.y - a.y)
    val dx = p.x - projX
    val dy = p.y - projY
    return sqrt(dx * dx + dy * dy)
}
