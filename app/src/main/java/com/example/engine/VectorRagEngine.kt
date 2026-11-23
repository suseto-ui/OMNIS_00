package com.example.engine

import com.example.data.MemoryFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

data class VectorRagMatch(
    val fragment: MemoryFragment,
    val cosineSimilarity: Float,
    val vector64: FloatArray,
    val matchedTerms: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VectorRagMatch
        return fragment.id == other.fragment.id && cosineSimilarity == other.cosineSimilarity
    }

    override fun hashCode(): Int {
        var result = fragment.id.hashCode()
        result = 31 * result + cosineSimilarity.hashCode()
        return result
    }
}

object VectorRagEngine {
    const val VECTOR_DIMENSION = 64

    /**
     * Deterministická L2-normalizace 64D vektoru s ošetřením NaN/Inf a nulové normy.
     */
    fun normalizeL264D(rawVector: FloatArray): FloatArray {
        val out = FloatArray(VECTOR_DIMENSION)
        val limit = minOf(rawVector.size, VECTOR_DIMENSION)
        var sumSquares = 0.0f
        for (i in 0 until limit) {
            val v = rawVector[i]
            val finiteVal = if (v.isFinite()) v else 0.0f
            out[i] = finiteVal
            sumSquares += finiteVal * finiteVal
        }
        if (!sumSquares.isFinite() || sumSquares <= 1e-8f) {
            return FloatArray(VECTOR_DIMENSION)
        }
        val norm = sqrt(sumSquares)
        if (!norm.isFinite() || norm <= 0.0001f) {
            return FloatArray(VECTOR_DIMENSION)
        }
        for (i in 0 until VECTOR_DIMENSION) {
            out[i] /= norm
        }
        return out
    }

    /**
     * Kóduje textový řetězec do 64-dimenzionálního L2 normalizovaného hustého vektoru
     * využívajícího sémantické sub-word (n-gram) kódování.
     */
    fun encodeTo64DVector(text: String): FloatArray {
        val vector = FloatArray(VECTOR_DIMENSION)
        val features = extractSubwordsAndTokens(text)
        if (features.isEmpty()) {
            return vector
        }

        // Feature Frequency & Hashing
        val counts = mutableMapOf<Int, Int>()
        features.forEach { feat ->
            val dimIndex = abs(feat.hashCode()) % VECTOR_DIMENSION
            counts[dimIndex] = (counts[dimIndex] ?: 0) + 1
        }

        val total = features.size.toFloat()
        counts.forEach { (dimIndex, count) ->
            val tf = count.toFloat() / total
            val idf = 1.0f + ln(1.0f + (VECTOR_DIMENSION.toFloat() / (1.0f + count)))
            val valFinite = tf * idf
            vector[dimIndex] = if (valFinite.isFinite()) valFinite else 0.0f
        }

        return normalizeL264D(vector)
    }

    /**
     * Vypočítá kosinovou sémantickou podobnost dvou 64-dimenzionálních vektorů:
     * cos(theta) = (u . v) / (||u|| * ||v||)
     */
    fun calculateCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != VECTOR_DIMENSION || vectorB.size != VECTOR_DIMENSION) return 0.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in 0 until VECTOR_DIMENSION) {
            val a = if (vectorA[i].isFinite()) vectorA[i] else 0.0f
            val b = if (vectorB[i].isFinite()) vectorB[i] else 0.0f
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (!denominator.isFinite() || denominator < 0.00001f) return 0.0f
        val cos = dotProduct / denominator
        return if (cos.isFinite()) cos.coerceIn(0.0f, 1.0f) else 0.0f
    }

    /**
     * Provede vektorové RAG vyhledávání nad seznamem paměťových fragmentů s hybridním vyhodnocením.
     */
    fun searchVectorMemory(
        query: String,
        fragments: List<MemoryFragment>,
        topK: Int = 3
    ): List<VectorRagMatch> {
        val queryVector = encodeTo64DVector(query)
        val queryTokens = tokenize(query)

        return fragments.map { fragment ->
            val docText = "${fragment.title} ${fragment.summary} ${fragment.tags}"
            val docVector = encodeTo64DVector(docText)
            val denseCosSim = calculateCosineSimilarity(queryVector, docVector)

            val matched = queryTokens.filter { q ->
                fragment.title.contains(q, ignoreCase = true) ||
                fragment.summary.contains(q, ignoreCase = true) ||
                fragment.tags.contains(q, ignoreCase = true)
            }

            // Hybridní skóre kombinující hustý vektor a tokenový překryv
            val tokenOverlapRatio = if (queryTokens.isNotEmpty()) {
                matched.size.toFloat() / queryTokens.size.toFloat()
            } else 0f

            val hybridScore = (denseCosSim * 0.70f + tokenOverlapRatio * 0.30f).coerceIn(0f, 1f)

            VectorRagMatch(
                fragment = fragment,
                cosineSimilarity = hybridScore,
                vector64 = docVector,
                matchedTerms = matched
            )
        }
        .sortedByDescending { it.cosineSimilarity }
        .take(topK)
    }

    suspend fun encodeTo64DVectorAsync(text: String): FloatArray =
        withContext(Dispatchers.Default) {
            encodeTo64DVector(text)
        }

    suspend fun searchVectorMemoryAsync(
        query: String,
        fragments: List<MemoryFragment>,
        topK: Int = 3
    ): List<VectorRagMatch> = withContext(Dispatchers.Default) {
        searchVectorMemory(query, fragments, topK)
    }

    /**
     * Sestaví RAG vyhledaný kontext do formátovaného bloku pro injekci do rezonančního jádra.
     */
    fun buildRagContextPrompt(matches: List<VectorRagMatch>): String {
        if (matches.isEmpty()) return ""
        val builder = StringBuilder()
        builder.append("[RAG SÉMANTICKÝ KONTEXT Z VEKTOROVÉ PAMĚTI]\n")
        matches.forEachIndexed { index, match ->
            val simPct = (match.cosineSimilarity * 100).toInt()
            builder.append("${index + 1}. [${match.fragment.title}] (Shoda: $simPct%)\n")
            builder.append("   Souhrn: ${match.fragment.summary}\n")
            if (match.matchedTerms.isNotEmpty()) {
                builder.append("   Matched tokens: ${match.matchedTerms.joinToString(", ")}\n")
            }
        }
        return builder.toString()
    }

    private fun extractSubwordsAndTokens(text: String): List<String> {
        val tokens = tokenize(text)
        val features = mutableListOf<String>()
        features.addAll(tokens)
        tokens.forEach { token ->
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    features.add(token.substring(i, i + 3))
                }
            }
        }
        return features
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace("[^a-záčďéěíňóřšťúůýž0-9]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
    }
}
