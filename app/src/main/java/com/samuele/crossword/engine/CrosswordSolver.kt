package com.samuele.crossword.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Solver CSP con:
 *  - Domini iniziali filtrati per lunghezza + dedup + normalizzazione
 *  - Grafo di overlap precalcolato
 *  - MRV + degree heuristic (tie-break)
 *  - Forward checking con fail-fast su dominio vuoto
 *  - Timeout e cooperazione con coroutine cancellation
 *  - Ritorno di soluzione parziale se non completa
 */
class CrosswordSolver(
    private val grid: CrosswordGrid,
    private val wordBank: List<WordClue>,
    private val timeout: Duration = 30.seconds
) {
    private val cols = grid.cols
    private val slots: List<Slot> = grid.extractSlots()

    /** Per ogni slotId: lista di (otherId, selfIdx, otherIdx). */
    private val overlaps: Map<Int, List<Triple<Int, Int, Int>>> = buildOverlaps()

    /** wordBank normalizzato e deduplicato per lunghezza. */
    private val wordsByLength: Map<Int, List<WordClue>> =
        wordBank
            .map { it.copy(word = it.normalized) }
            .distinctBy { it.word }
            .groupBy { it.word.length }

    // --- Stato di ricerca (resettato ad ogni solve) ---
    private lateinit var domains: Array<MutableList<WordClue>>
    private lateinit var assignment: Array<WordClue?>
    private lateinit var usedWords: HashSet<String>
    private var nodesVisited = 0L
    private var startTime = 0L

    private fun buildOverlaps(): Map<Int, List<Triple<Int, Int, Int>>> {
        val cellToSlots = HashMap<Int, MutableList<Pair<Int, Int>>>() // flat -> [(slotId, idxInSlot)]
        slots.forEach { s ->
            s.cells.forEachIndexed { idx, flat ->
                cellToSlots.getOrPut(flat) { mutableListOf() } += s.id to idx
            }
        }
        val result = HashMap<Int, MutableList<Triple<Int, Int, Int>>>()
        for (s in slots) result[s.id] = mutableListOf()
        cellToSlots.forEach { (_, list) ->
            for (i in list.indices) for (j in i + 1 until list.size) {
                val (aId, aIdx) = list[i]
                val (bId, bIdx) = list[j]
                result[aId]!! += Triple(bId, aIdx, bIdx)
                result[bId]!! += Triple(aId, bIdx, aIdx)
            }
        }
        return result
    }

    private fun timedOut(): Boolean =
        System.currentTimeMillis() - startTime > timeout.inWholeMilliseconds

    /**
     * Genera fino a [limit] soluzioni DIVERSE.
     * Ogni soluzione è trovata proseguendo il backtracking dopo la prima,
     * così si ottengono varianti reali (non duplicati).
     */
    suspend fun solveAll(limit: Int = 20): List<CrosswordResult> {
        val missingLengths = slots.map { it.length }.toSet()
            .filter { (wordsByLength[it]?.size ?: 0) == 0 }
        if (missingLengths.isNotEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<CrosswordResult>()
        val seen = HashSet<String>()   // firma univoca per deduplicare

        initState()
        startTime = System.currentTimeMillis()

        var pass = 0
        while (results.size < limit && !timedOut() && pass < 5) {
            pass++
            backtrackCollect(seen, results, limit)
            if (results.size >= limit) break
        }

        return results
    }

    private suspend fun backtrackCollect(
        seen: MutableSet<String>,
        out: MutableList<CrosswordResult>,
        limit: Int
    ): Boolean {
        coroutineContext.ensureActive()
        nodesVisited++
        if (timedOut()) return false

        val slotId = selectUnassigned()
        if (slotId == -1) {
            // Soluzione completa trovata
            val signature = buildSignature()
            if (signature !in seen) {
                seen += signature
                out += buildResult()
            }
            return out.size < limit
        }

        val candidates = domains[slotId].toMutableList()
        // IMPORTANTE: shuffle per ottenere varianti diverse ad ogni run
        candidates.shuffle()

        for (candidate in candidates) {
            if (out.size >= limit || timedOut()) return false
            if (!isConsistent(slotId, candidate)) continue
            assignment[slotId] = candidate
            usedWords += candidate.word
            val removed = forwardCheck(slotId, candidate)
            if (removed != null) {
                backtrackCollect(seen, out, limit)
                restoreDomains(removed)
            }
            usedWords -= candidate.word
            assignment[slotId] = null
            if (out.size >= limit) return false
        }
        return out.size < limit
    }

    private fun buildSignature(): String =
        slots.indices.joinToString("|") { assignment[it]?.word ?: "_" }

    suspend fun solve(): SolveOutcome {
        // Validazione preliminare
        val missingLengths = slots.map { it.length }.toSet()
            .filter { (wordsByLength[it]?.size ?: 0) == 0 }
        if (missingLengths.isNotEmpty()) {
            return SolveOutcome.InvalidInput(
                "Nessuna parola disponibile per lunghezze: $missingLengths"
            )
        }

        initState()
        startTime = System.currentTimeMillis()

        val found = try {
            backtrack()
        } catch (e: CancellationException) {
            throw e
        }

        val result = buildResult()
        return when {
            found && result.unsolvedSlotIds.isEmpty() -> SolveOutcome.Success(result)
            !found && result.unsolvedSlotIds.isEmpty() -> SolveOutcome.Success(result)
            nodesVisited > 0 -> SolveOutcome.Partial(result)
            else -> SolveOutcome.NoSolution
        }
    }

    private fun initState() {
        domains = Array(slots.size) { i ->
            val len = slots[i].length
            (wordsByLength[len] ?: emptyList()).toMutableList()
        }
        assignment = arrayOfNulls(slots.size)
        usedWords = HashSet()
        nodesVisited = 0
    }

    /** MRV + degree heuristic come tie-break. */
    private fun selectUnassigned(): Int {
        var best = -1
        var bestDomain = Int.MAX_VALUE
        var bestDegree = -1
        for (i in slots.indices) {
            if (assignment[i] != null) continue
            val d = domains[i].size
            if (d == 0) return i  // fail-fast
            val degree = overlaps[i]?.count { (otherId, _, _) ->
                assignment[otherId] == null
            } ?: 0
            if (d < bestDomain || (d == bestDomain && degree > bestDegree)) {
                best = i; bestDomain = d; bestDegree = degree
            }
        }
        return best
    }

    private suspend fun backtrack(): Boolean {
        coroutineContext.ensureActive()
        nodesVisited++
        if (nodesVisited % 512L == 0L &&
            System.currentTimeMillis() - startTime > timeout.inWholeMilliseconds
        ) return false

        val slotId = selectUnassigned()
        if (slotId == -1) return true

        val candidates = domains[slotId].toList()
        // LCV: prova prima le parole che vincolano meno gli altri
        val ordered = candidates.sortedBy { candidate ->
            var kills = 0
            overlaps[slotId]?.forEach { (otherId, _, otherIdx) ->
                if (assignment[otherId] == null) {
                    val required = candidate.word[otherIdx]
                    kills += domains[otherId].count { it.word[0] != required } // approssimazione veloce
                }
            }
            kills
        }

        for (candidate in ordered) {
            if (!isConsistent(slotId, candidate)) continue

            assignment[slotId] = candidate
            usedWords += candidate.word

            val removed = forwardCheck(slotId, candidate)
            if (removed != null) {
                if (backtrack()) return true
                restoreDomains(removed)
            }

            usedWords -= candidate.word
            assignment[slotId] = null
        }
        return false
    }

    private fun isConsistent(slotId: Int, candidate: WordClue): Boolean {
        if (candidate.word in usedWords) return false
        overlaps[slotId]?.forEach { (otherId, selfIdx, otherIdx) ->
            val other = assignment[otherId] ?: return@forEach
            if (other.word[otherIdx] != candidate.word[selfIdx]) return false
        }
        return true
    }

    /** Ritorna null se un dominio diventa vuoto (fail-fast). */
    private fun forwardCheck(
        slotId: Int,
        candidate: WordClue
    ): List<Triple<Int, WordClue, Int>>? {
        val removed = ArrayList<Triple<Int, WordClue, Int>>() // (slotId, word, idx)
        overlaps[slotId]?.forEach { (otherId, selfIdx, otherIdx) ->
            if (assignment[otherId] != null) return@forEach
            val required = candidate.word[selfIdx]
            val dom = domains[otherId]
            val toRemove = ArrayList<WordClue>()
            for (w in dom) {
                if (w.word[otherIdx] != required) toRemove += w
            }
            if (toRemove.isNotEmpty()) {
                dom.removeAll(toRemove.toSet())
                toRemove.forEach { removed += Triple(otherId, it, 0) }
                if (dom.isEmpty()) {
                    // rollback immediato
                    restoreDomains(removed)
                    return null
                }
            }
        }
        return removed
    }

    private fun restoreDomains(removed: List<Triple<Int, WordClue, Int>>) {
        removed.forEach { (sid, w, _) -> domains[sid].add(w) }
    }

    private fun buildResult(): CrosswordResult {
        val letters = Array(grid.rows) { CharArray(cols) { ' ' } }
        val assignmentsMap = HashMap<Int, WordClue>()
        val unsolved = mutableSetOf<Int>()
        for (i in slots.indices) {
            val w = assignment[i]
            if (w == null) { unsolved += slots[i].id; continue }
            assignmentsMap[slots[i].id] = w
            slots[i].cells.forEachIndexed { k, flat ->
                letters[flat / cols][flat % cols] = w.word[k]
            }
        }
        val numbered = Numbering.assignNumbers(grid, slots)
        return CrosswordResult(
            rows = grid.rows,
            cols = cols,
            whiteCells = grid.whiteCells,
            letters = letters,
            slots = numbered,
            assignments = assignmentsMap,
            unsolvedSlotIds = unsolved,
            complete = unsolved.isEmpty()
        )
    }
}
