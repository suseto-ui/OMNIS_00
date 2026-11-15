package com.example.ui.memory

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
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
import com.example.data.MemoryFragment
import com.example.engine.CoherenceMetrics
import com.example.engine.TensorCoherenceEvaluator
import com.example.engine.VectorRagEngine
import com.example.engine.VectorRagMatch
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RagMemoryView(
    sampleFragments: List<MemoryFragment>,
    onInjectContext: (MemoryFragment) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var testQuery by remember { mutableStateOf("Analýza topologických invariantů tenzorového jádra a RAG paměti") }
    var testResponse by remember { mutableStateOf("Tenzorové jádro AKGE v8.2 vykazuje stabilní perkolaci p_c = 0.68 a sémantickou koherenci přes 64D RAG vyhledávací index.") }

    val ragMatches by produceState(
        initialValue = emptyList(),
        key1 = testQuery,
        key2 = sampleFragments
    ) {
        value = VectorRagEngine.searchVectorMemoryAsync(testQuery, sampleFragments, topK = 4)
    }

    val coherence by produceState(
        initialValue = CoherenceMetrics(
            overallScore = 0f,
            precision = 0f,
            relevance = 0f,
            consistency = 0f,
            depth = 0f,
            contextFit = 0f,
            tensorStability = 0f,
            cosineSimilarity = 0f,
            queryVector = FloatArray(VectorRagEngine.VECTOR_DIMENSION),
            responseVector = FloatArray(VectorRagEngine.VECTOR_DIMENSION)
        ),
        key1 = testQuery,
        key2 = testResponse,
        key3 = ragMatches
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            TensorCoherenceEvaluator.evaluateCoherence(
                query = testQuery,
                response = testResponse,
                ragMatches = ragMatches
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("rag_memory_view_container")
    ) {
        // Top Header Title
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
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = OmnisCyan,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "VEKTOROVÁ RAG MEZIPAMĚŤ & KOHERENCE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "64D TF-IDF EMEBDING | KOSINOVÁ PODOBNOST | 6-OSÁ ANALÝZA",
                        color = OmnisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = OmnisEmerald.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald)
            ) {
                Text(
                    text = "${coherence.overallScore.toInt()}% KOHERENCE",
                    color = OmnisEmerald,
                    fontSize = 11.sp,
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
            // 1. Interactive Scratchpad Input Card
            item {
                ScratchpadInputCard(
                    testQuery = testQuery,
                    onQueryChange = { testQuery = it },
                    testResponse = testResponse,
                    onResponseChange = { testResponse = it }
                )
            }

            // 2. 64D Vector Heat-Strip Spectrum
            item {
                Vector64SpectrumCard(queryVector = coherence.queryVector)
            }

            // 3. Radar Chart & Coherence Metrics Breakdown
            item {
                CoherenceRadarCard(coherence = coherence)
            }

            // 4. RAG Vector Search Matches
            item {
                Text(
                    text = "NEJRELEVANTNĚJŠÍ PŘÍPADY Z VEKTOROVÉ MEZIPAMĚTI",
                    color = OmnisCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (ragMatches.isEmpty()) {
                item {
                    Text(
                        text = "Žádné shody nebyly nalezeny v paměťových fragmentech.",
                        color = OmnisTextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                items(ragMatches, key = { it.fragment.id }) { match ->
                    VectorMatchCard(match = match, onInjectContext = onInjectContext)
                }
            }
        }
    }
}

@Composable
private fun ScratchpadInputCard(
    testQuery: String,
    onQueryChange: (String) -> Unit,
    testResponse: String,
    onResponseChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "TESTOVACÍ SÉMANTICKÝ SCRATCHPAD",
                color = OmnisCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = testQuery,
                onValueChange = onQueryChange,
                label = { Text("Dotaz operátora / Vstupní prompt", color = OmnisTextMuted, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scratchpad_query_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = OmnisCyan,
                    unfocusedBorderColor = OmnisBorderDark,
                    focusedContainerColor = OmnisBgDark,
                    unfocusedContainerColor = OmnisBgDark
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = testResponse,
                onValueChange = onResponseChange,
                label = { Text("Vygenerovaná odpověď O.M.N.I.S.", color = OmnisTextMuted, fontSize = 11.sp) },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scratchpad_response_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = OmnisEmerald,
                    unfocusedBorderColor = OmnisBorderDark,
                    focusedContainerColor = OmnisBgDark,
                    unfocusedContainerColor = OmnisBgDark
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun Vector64SpectrumCard(queryVector: FloatArray) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPEKTRUM 64D VEKTORU EMBEDDINGU",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "64 DIMENZÍ TF-IDF",
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 64D Bar Heatmap Visualization
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(OmnisBgDark)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val vectorList = remember(queryVector) { queryVector.take(64) }
                val maxVal = remember(queryVector) { (queryVector.maxOrNull() ?: 1.0f).coerceAtLeast(0.001f) }

                vectorList.forEachIndexed { idx, valVal ->
                    val heightRatio = (valVal / maxVal).coerceIn(0.1f, 1.0f)
                    val barColor = when {
                        heightRatio > 0.7f -> OmnisEmerald
                        heightRatio > 0.3f -> OmnisCyan
                        else -> OmnisCyan.copy(alpha = 0.3f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightRatio)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoherenceRadarCard(coherence: CoherenceMetrics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "RADAR SÉMANTICKÉ KOHERENCE",
                color = OmnisEmerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Radar Chart Canvas (Left)
                CoherenceRadarCanvas(
                    coherence = coherence,
                    modifier = Modifier
                        .size(170.dp)
                        .testTag("radar_chart_canvas")
                )

                // Metric Indicators List (Right)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricBarRow("Preciznost", coherence.precision, OmnisCyan)
                    MetricBarRow("Relevance", coherence.relevance, OmnisEmerald)
                    MetricBarRow("Konzistence", coherence.consistency, Color(0xFFA855F7))
                    MetricBarRow("Hloubka", coherence.depth, OmnisAmber)
                    MetricBarRow("Kontext Fit", coherence.contextFit, Color(0xFFF97316))
                    MetricBarRow("Stabilita 8D", coherence.tensorStability, OmnisCyan)
                }
            }
        }
    }
}

@Composable
private fun CoherenceRadarCanvas(coherence: CoherenceMetrics, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.width / 2f) * 0.8f

        val metricsList = listOf(
            coherence.precision / 100f,
            coherence.relevance / 100f,
            coherence.consistency / 100f,
            coherence.depth / 100f,
            coherence.contextFit / 100f,
            coherence.tensorStability / 100f
        )

        val numAxes = 6
        val angleStep = (2 * Math.PI / numAxes).toFloat()

        // Vykreslení referenčních kruhů (25%, 50%, 75%, 100%)
        for (step in 1..4) {
            val r = radius * (step / 4f)
            val polyPath = Path()
            for (i in 0 until numAxes) {
                val angle = i * angleStep - Math.PI.toFloat() / 2f
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (i == 0) polyPath.moveTo(x, y) else polyPath.lineTo(x, y)
            }
            polyPath.close()
            drawPath(
                path = polyPath,
                color = Color.White.copy(alpha = 0.12f),
                style = Stroke(width = 1f)
            )
        }

        // Vykreslení os
        for (i in 0 until numAxes) {
            val angle = i * angleStep - Math.PI.toFloat() / 2f
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = center,
                end = Offset(x, y),
                strokeWidth = 1f
            )
        }

        // Vykreslení Polygonu Naměřených Metrik
        val metricPath = Path()
        metricsList.forEachIndexed { i, value ->
            val angle = i * angleStep - Math.PI.toFloat() / 2f
            val r = radius * value.coerceIn(0.1f, 1.0f)
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            if (i == 0) metricPath.moveTo(x, y) else metricPath.lineTo(x, y)
        }
        metricPath.close()

        drawPath(
            path = metricPath,
            color = Color(0xFF00F0FF).copy(alpha = 0.35f)
        )
        drawPath(
            path = metricPath,
            color = Color(0xFF00F0FF),
            style = Stroke(width = 2.5f)
        )

        // Vykreslení vrcholů polygonu
        metricsList.forEachIndexed { i, value ->
            val angle = i * angleStep - Math.PI.toFloat() / 2f
            val r = radius * value.coerceIn(0.1f, 1.0f)
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            drawCircle(
                color = Color(0xFF10B981),
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun MetricBarRow(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = OmnisTextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(75.dp)
        )

        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = OmnisBgDark
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "${value.toInt()}%",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun VectorMatchCard(
    match: VectorRagMatch,
    onInjectContext: (MemoryFragment) -> Unit
) {
    val pct = (match.cosineSimilarity * 100).toInt()
    val badgeColor = when {
        match.cosineSimilarity >= 0.70f -> OmnisEmerald
        match.cosineSimilarity >= 0.40f -> OmnisCyan
        else -> OmnisAmber
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = match.fragment.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = "Kos: $pct%",
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = match.fragment.summary,
                color = OmnisTextMuted,
                fontSize = 11.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (match.matchedTerms.isNotEmpty()) {
                    Text(
                        text = "Tokens: ${match.matchedTerms.take(3).joinToString(", ")}",
                        color = OmnisCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = { onInjectContext(match.fragment) },
                    colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Injektovat RAG",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
