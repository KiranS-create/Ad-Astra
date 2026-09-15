package org.sih.itantra.core.speech.benchmark.multilang

import java.text.Normalizer
import kotlin.math.min

/**
 * Result of Word Error Rate (WER) and Character Error Rate (CER) calculation.
 */
data class AlignmentResult(
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val hits: Int,
    val totalReference: Int,
    val errorRate: Double
)

data class WerCerScore(
    val wer: AlignmentResult,
    val cer: AlignmentResult,
    val exactMatch: Boolean,
    val normalizedReference: String,
    val normalizedHypothesis: String
)

/**
 * Indic-script aware Word Error Rate (WER) and Character Error Rate (CER) evaluator.
 * Handles Unicode normalization (NFC), Indic diacritics/viramas/matras, and whitespace tokenization.
 */
object WerCerCalculator {

    private val PUNCTUATION_REGEX = Regex("""[.,\/#!?$%\^&\*;:{}=\-_`~()\"'।॥+—–\[\]<>]""")
    private val MULTIPLE_WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Normalizes text for evaluation:
     * - Unicode NFC normalization
     * - Lowercases ASCII characters (preserves Indic scripts without change)
     * - Strips punctuation and symbols
     * - Trims and collapses multiple spaces into a single space
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        val stripped = PUNCTUATION_REGEX.replace(nfc, " ")
        return MULTIPLE_WHITESPACE_REGEX.replace(stripped.lowercase(), " ").trim()
    }

    /**
     * Splits normalized text into word tokens.
     */
    fun tokenizeWords(text: String): List<String> {
        val norm = normalize(text)
        if (norm.isEmpty()) return emptyList()
        return norm.split(" ").filter { it.isNotBlank() }
    }

    /**
     * Splits normalized text into character tokens (graphemes/code points).
     */
    fun tokenizeChars(text: String): List<String> {
        val norm = normalize(text).replace(" ", "")
        if (norm.isEmpty()) return emptyList()
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < norm.length) {
            val cp = norm.codePointAt(i)
            val charCount = Character.charCount(cp)
            tokens.add(norm.substring(i, i + charCount))
            i += charCount
        }
        return tokens
    }

    /**
     * Calculates alignment statistics (Substitutions, Deletions, Insertions, Hits, Error Rate)
     * using the Levenshtein dynamic programming matrix.
     */
    fun <T> calculateAlignment(ref: List<T>, hyp: List<T>): AlignmentResult {
        val n = ref.size
        val m = hyp.size

        if (n == 0 && m == 0) {
            return AlignmentResult(substitutions = 0, deletions = 0, insertions = 0, hits = 0, totalReference = 0, errorRate = 0.0)
        }
        if (n == 0) {
            return AlignmentResult(substitutions = 0, deletions = 0, insertions = m, hits = 0, totalReference = 0, errorRate = 1.0)
        }
        if (m == 0) {
            return AlignmentResult(substitutions = 0, deletions = n, insertions = 0, hits = 0, totalReference = n, errorRate = 1.0)
        }

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                if (ref[i - 1] == hyp[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = min(
                        dp[i - 1][j - 1] + 1, // Substitution
                        min(
                            dp[i - 1][j] + 1, // Deletion
                            dp[i][j - 1] + 1  // Insertion
                        )
                    )
                }
            }
        }

        var i = n
        var j = m
        var subs = 0
        var dels = 0
        var ins = 0
        var hits = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && ref[i - 1] == hyp[j - 1]) {
                hits++
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                subs++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                dels++
                i--
            } else {
                ins++
                j--
            }
        }

        val totalErrors = subs + dels + ins
        val errorRate = totalErrors.toDouble() / n.toDouble()

        return AlignmentResult(
            substitutions = subs,
            deletions = dels,
            insertions = ins,
            hits = hits,
            totalReference = n,
            errorRate = errorRate
        )
    }

    /**
     * Evaluates WER, CER, and Exact Match for a reference and hypothesis pair.
     */
    fun evaluate(reference: String, hypothesis: String): WerCerScore {
        val normRef = normalize(reference)
        val normHyp = normalize(hypothesis)

        val refWords = tokenizeWords(normRef)
        val hypWords = tokenizeWords(normHyp)
        val werResult = calculateAlignment(refWords, hypWords)

        val refChars = tokenizeChars(normRef)
        val hypChars = tokenizeChars(normHyp)
        val cerResult = calculateAlignment(refChars, hypChars)

        val exactMatch = normRef == normHyp

        return WerCerScore(
            wer = werResult,
            cer = cerResult,
            exactMatch = exactMatch,
            normalizedReference = normRef,
            normalizedHypothesis = normHyp
        )
    }

    /**
     * Aggregates multiple scores into weighted average WER, CER, and overall Exact Match rate.
     */
    fun aggregate(scores: List<WerCerScore>): AggregateAccuracyMetrics {
        if (scores.isEmpty()) {
            return AggregateAccuracyMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        val totalRefWords = scores.sumOf { it.wer.totalReference }
        val totalWordErrors = scores.sumOf { it.wer.substitutions + it.wer.deletions + it.wer.insertions }
        val totalRefChars = scores.sumOf { it.cer.totalReference }
        val totalCharErrors = scores.sumOf { it.cer.substitutions + it.cer.deletions + it.cer.insertions }
        val exactMatches = scores.count { it.exactMatch }

        val avgWer = if (totalRefWords > 0) totalWordErrors.toDouble() / totalRefWords.toDouble() else 0.0
        val avgCer = if (totalRefChars > 0) totalCharErrors.toDouble() / totalRefChars.toDouble() else 0.0
        val exactMatchRate = exactMatches.toDouble() / scores.size.toDouble()

        return AggregateAccuracyMetrics(
            averageWer = avgWer,
            averageCer = avgCer,
            exactMatchRate = exactMatchRate,
            totalSubstitutions = scores.sumOf { it.wer.substitutions },
            totalDeletions = scores.sumOf { it.wer.deletions },
            totalInsertions = scores.sumOf { it.wer.insertions },
            totalHits = scores.sumOf { it.wer.hits },
            totalReferenceWords = totalRefWords,
            totalReferenceChars = totalRefChars,
            exactMatchCount = exactMatches,
            totalUtterances = scores.size
        )
    }
}

data class AggregateAccuracyMetrics(
    val averageWer: Double,
    val averageCer: Double,
    val exactMatchRate: Double,
    val totalSubstitutions: Int,
    val totalDeletions: Int,
    val totalInsertions: Int,
    val totalHits: Int,
    val totalReferenceWords: Int,
    val totalReferenceChars: Int,
    val exactMatchCount: Int,
    val totalUtterances: Int
)
