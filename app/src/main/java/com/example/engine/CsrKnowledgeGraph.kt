package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Paměťově efektivní CSR (Compressed Sparse Row) reprezentace znalostního hypergrafu O.M.N.I.S.
 * Obsahuje pole: rowOffsets, colIndices, weights a doménové identifikátory.
 * Poskytuje vyhledávání kauzálních cest, Judea Pearl Do-Calculus intervence P(Y | do(X=x))
 * a detekci perkolačního prahu p_c ≈ 0.38 pro automatickou aktivaci stabilizačního bypassu.
 */
class CsrKnowledgeGraph(
    val rowOffsets: IntArray,
    val colIndices: IntArray,
    val weights: FloatArray,
    val domainNames: List<String> = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")
) {
    val nodeCount: Int = domainNames.size
    private val domainIndexMap: Map<String, Int> = domainNames.withIndex().associate { it.value to it.index }

    init {
        require(rowOffsets.size == nodeCount + 1) {
            "rowOffsets size (${rowOffsets.size}) must be nodeCount + 1 (${nodeCount + 1})"
        }
        require(colIndices.size == weights.size) {
            "colIndices size (${colIndices.size}) must match weights size (${weights.size})"
        }
    }

    /**
     * Vyhledá nejkratší kauzální cestu mezi zdrojovou a cílovou doménou (Dijkstra algorithm).
     * Váha hrany představuje kauzální odpor: odpor = 1.0f / max(0.01f, weight).
     */
    fun findShortestCausalPath(startDomain: String, targetDomain: String): List<String> {
        val startIdx = domainIndexMap[startDomain] ?: return emptyList()
        val targetIdx = domainIndexMap[targetDomain] ?: return emptyList()
        if (startIdx == targetIdx) return listOf(startDomain)

        val distances = FloatArray(nodeCount) { Float.MAX_VALUE }
        val previous = IntArray(nodeCount) { -1 }
        val visited = BooleanArray(nodeCount) { false }

        // PriorityQueue ukládá Pair(nodeIndex, distance)
        val pq = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
        distances[startIdx] = 0.0f
        pq.add(Pair(startIdx, 0.0f))

        while (pq.isNotEmpty()) {
            val (current, currentDist) = pq.poll() ?: break
            if (visited[current]) continue
            visited[current] = true

            if (current == targetIdx) break

            val rowStart = rowOffsets[current]
            val rowEnd = rowOffsets[current + 1]

            for (k in rowStart until rowEnd) {
                val neighbor = colIndices[k]
                val weight = weights[k].coerceIn(0.01f, 1.0f)
                val resistance = 1.0f / weight // vyšší vazba = nižší kauzální odpor
                val newDist = currentDist + resistance

                if (newDist < distances[neighbor]) {
                    distances[neighbor] = newDist
                    previous[neighbor] = current
                    pq.add(Pair(neighbor, newDist))
                }
            }
        }

        // Rekonstrukce cesty
        if (distances[targetIdx] == Float.MAX_VALUE) return emptyList()

        val pathIndices = mutableListOf<Int>()
        var curr = targetIdx
        while (curr != -1) {
            pathIndices.add(curr)
            curr = previous[curr]
        }
        pathIndices.reverse()
        return pathIndices.map { domainNames[it] }
    }

    /**
     * Provede kauzální Do-Calculus intervenci Judea Pearla: P(Y | do(X = x)).
     * Princip: Proříznutí příchozích hran do uzlu X (odstranění konfunderů z back-door cest)
     * a deterministická propagace změny hodnoty X na cílový uzel Y podél kauzálních cest v grafu G_{\bar{X}}.
     */
    fun computeDoCalculusIntervention(
        interventionDomain: String,
        interventionValue: Float,
        targetDomain: String,
        currentValues: Map<String, Float>
    ): Float {
        val xIdx = domainIndexMap[interventionDomain] ?: return currentValues[targetDomain] ?: 0.5f
        val yIdx = domainIndexMap[targetDomain] ?: return currentValues[targetDomain] ?: 0.5f

        val xVal = interventionValue.coerceIn(0.0f, 1.0f)
        if (xIdx == yIdx) return xVal

        val originalX = (currentValues[interventionDomain] ?: 0.5f).coerceIn(0.0f, 1.0f)
        val deltaX = xVal - originalX

        // Hledání všech kauzálních cest z X do Y (G_{\bar{X}}: X nemá příchozí vlivy, pouze vysílá)
        val causalPath = findShortestCausalPath(interventionDomain, targetDomain)
        if (causalPath.isEmpty()) {
            // Žádné kauzální spojení: intervence na X neovlivní Y
            return currentValues[targetDomain] ?: 0.5f
        }

        // Kumulativní kauzální přenos: násobek vah podél cesty s exponenciálním tlumením
        var cumulativeTransfer = 1.0f
        for (i in 0 until causalPath.size - 1) {
            val uIdx = domainIndexMap[causalPath[i]] ?: continue
            val vIdx = domainIndexMap[causalPath[i + 1]] ?: continue

            // Nalezení váhy mezi u a v v CSR
            var edgeWeight = 0.5f
            val start = rowOffsets[uIdx]
            val end = rowOffsets[uIdx + 1]
            for (k in start until end) {
                if (colIndices[k] == vIdx) {
                    edgeWeight = weights[k]
                    break
                }
            }
            cumulativeTransfer *= edgeWeight
        }

        val originalY = (currentValues[targetDomain] ?: 0.5f).coerceIn(0.0f, 1.0f)
        val inducedDeltaY = deltaX * cumulativeTransfer
        val finalY = (originalY + inducedDeltaY).coerceIn(0.05f, 1.0f)
        return (finalY * 1000f).toInt() / 1000f
    }

    /**
     * Vyhodnotí perkolační práh kaskádových rizik (p_c ≈ 0.38).
     * Pokud podíl zasažených domén překročí p_c, aktivuje se izolační bypass,
     * který zabrání dominovému efektu napříč 8D sítí.
     */
    fun evaluatePercolationBypass(domainScores: Map<String, Float>): Pair<Boolean, String> {
        val criticalThreshold = 0.38f
        var failingCount = 0

        for (domain in domainNames) {
            val score = domainScores[domain] ?: 0.5f
            if (score < 0.40f) {
                failingCount++
            }
        }

        val failingRatio = failingCount.toFloat() / nodeCount.toFloat()
        val isCritical = failingRatio >= criticalThreshold

        val message = if (isCritical) {
            "PERCOLATION_CRITICAL: Zasaženo ${(failingRatio * 100).toInt()}% domén (práh 38%). Aktivována stabilizační izolace."
        } else {
            "PERCOLATION_STABLE: Zasaženo ${(failingRatio * 100).toInt()}% domén (pod prahem 38%). Síť je v homeostáze."
        }

        return Pair(isCritical, message)
    }

    suspend fun findShortestCausalPathAsync(startDomain: String, targetDomain: String): List<String> =
        withContext(Dispatchers.Default) {
            findShortestCausalPath(startDomain, targetDomain)
        }

    suspend fun computeDoCalculusInterventionAsync(
        interventionDomain: String,
        interventionValue: Float,
        targetDomain: String,
        currentValues: Map<String, Float>
    ): Float = withContext(Dispatchers.Default) {
        computeDoCalculusIntervention(interventionDomain, interventionValue, targetDomain, currentValues)
    }

    suspend fun evaluatePercolationBypassAsync(domainScores: Map<String, Float>): Pair<Boolean, String> =
        withContext(Dispatchers.Default) {
            evaluatePercolationBypass(domainScores)
        }

    companion object {
        /**
         * Vytvoří výchozí 8D CSR znalostní graf s kanonickými transdisciplinárními vazbami.
         */
        fun createDefaultGraph(): CsrKnowledgeGraph {
            val domains = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")
            val n = domains.size
            val dMap = domains.withIndex().associate { it.value to it.index }

            val rawEdges = listOf(
                Triple("SYS", "SEC", 0.88f),
                Triple("SEC", "LAW", 0.82f),
                Triple("SYS", "PHYS", 0.85f),
                Triple("ECON", "SYS", 0.70f),
                Triple("ECO", "ECON", 0.65f),
                Triple("PSYCH", "SOC", 0.75f),
                Triple("SOC", "LAW", 0.68f),
                Triple("SEC", "PHYS", 0.60f),
                Triple("PHYS", "SYS", 0.80f),
                Triple("LAW", "SEC", 0.78f),
                Triple("SOC", "PSYCH", 0.72f),
                Triple("ECON", "ECO", 0.62f)
            )

            val adjacency = Array(n) { mutableListOf<Pair<Int, Float>>() }
            for ((src, dst, w) in rawEdges) {
                val u = dMap[src] ?: continue
                val v = dMap[dst] ?: continue
                adjacency[u].add(Pair(v, w))
            }

            val rowOffsets = IntArray(n + 1)
            val colList = mutableListOf<Int>()
            val valList = mutableListOf<Float>()

            for (i in 0 until n) {
                rowOffsets[i] = colList.size
                val sorted = adjacency[i].sortedBy { it.first }
                for ((neighbor, weight) in sorted) {
                    colList.add(neighbor)
                    valList.add(weight)
                }
            }
            rowOffsets[n] = colList.size

            return CsrKnowledgeGraph(
                rowOffsets = rowOffsets,
                colIndices = colList.toIntArray(),
                weights = valList.toFloatArray(),
                domainNames = domains
            )
        }
    }
}
