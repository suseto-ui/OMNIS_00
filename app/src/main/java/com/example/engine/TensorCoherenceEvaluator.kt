package com.example.engine

import kotlin.math.abs

data class CoherenceMetrics(
    val overallScore: Float,
    val precision: Float,
    val relevance: Float,
    val consistency: Float,
    val depth: Float,
    val contextFit: Float,
    val tensorStability: Float,
    val cosineSimilarity: Float,
    val queryVector: FloatArray,
    val responseVector: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CoherenceMetrics
        return overallScore == other.overallScore && cosineSimilarity == other.cosineSimilarity
    }

    override fun hashCode(): Int {
        var result = overallScore.hashCode()
        result = 31 * result + cosineSimilarity.hashCode()
        return result
    }
}

object TensorCoherenceEvaluator {

    /**
     * Vyhodnotí sémantickou koherenci a 8D tenzorovou stabilitu vygenerované odpovědi O.M.N.I.S.
     */
    fun evaluateCoherence(
        query: String,
        response: String,
        ragMatches: List<VectorRagMatch> = emptyList(),
        eulerCharacteristic: Int = 2,
        entropy: Float = 0.42f,
        percolationThreshold: Float = 0.65f
    ): CoherenceMetrics {
        val qVector = VectorRagEngine.encodeTo64DVector(query)
        val rVector = VectorRagEngine.encodeTo64DVector(response)
        val cosSim = VectorRagEngine.calculateCosineSimilarity(qVector, rVector)

        // 1. Preciznost (Precision) - termínová shoda a hustota odborných výrazů
        val techTermsCount = countTechnicalTerms(response)
        val precisionScore = (cosSim * 50f + (techTermsCount.coerceAtMost(10) * 5f)).coerceIn(40f, 99f)

        // 2. Relevance - sémantická shoda s dotazem operátora
        val relevanceScore = (cosSim * 100f).coerceIn(30f, 100f)

        // 3. Konzistence - nízká entropie odpovedi a stabilita struktury
        val consistencyScore = ((1.0f - entropy.coerceIn(0f, 1f)) * 100f).coerceIn(50f, 98f)

        // 4. Hloubka (Depth) - délka a strukturovanost technického textu
        val lengthFactor = (response.length.toFloat() / 500f).coerceIn(0.4f, 1.0f)
        val depthScore = (lengthFactor * 85f + (techTermsCount * 2f)).coerceIn(40f, 99f)

        // 5. Kontextové Přizpůsobení (Context Fit) - shoda vyhledaných RAG kontextů
        val avgRagMatch = if (ragMatches.isNotEmpty()) {
            ragMatches.map { it.cosineSimilarity }.average().toFloat() * 100f
        } else {
            75f
        }
        val contextFitScore = (avgRagMatch * 0.7f + cosSim * 30f).coerceIn(30f, 100f)

        // 6. Tenzorová Stabilita - výpočet na základě Eulerovy charakteristiky a perkolace
        val eulerDev = abs(eulerCharacteristic - 2)
        val stabilityFactor = (percolationThreshold - (eulerDev * 0.1f)).coerceIn(0.2f, 1.0f)
        val tensorStabilityScore = (stabilityFactor * 100f).coerceIn(40f, 100f)

        // Celkové agregované skóre koherence
        val overall = (
            precisionScore * 0.20f +
            relevanceScore * 0.25f +
            consistencyScore * 0.15f +
            depthScore * 0.15f +
            contextFitScore * 0.15f +
            tensorStabilityScore * 0.10f
        ).coerceIn(0f, 100f)

        return CoherenceMetrics(
            overallScore = overall,
            precision = precisionScore,
            relevance = relevanceScore,
            consistency = consistencyScore,
            depth = depthScore,
            contextFit = contextFitScore,
            tensorStability = tensorStabilityScore,
            cosineSimilarity = cosSim,
            queryVector = qVector,
            responseVector = rVector
        )
    }

    private fun countTechnicalTerms(text: String): Int {
        val keywords = listOf(
            "tenzor", "vektor", "entropie", "perkolace", "rag", "kosinus",
            "jádro", "akge", "omnis", "invariant", "topologie", "barycentrum",
            "matice", "kvantový", "sémantika", "synapse", "klastr", "koronární"
        )
        val lower = text.lowercase()
        return keywords.count { lower.contains(it) }
    }
}
