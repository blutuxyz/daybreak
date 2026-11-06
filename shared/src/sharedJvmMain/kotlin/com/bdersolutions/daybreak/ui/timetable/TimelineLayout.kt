package com.bdersolutions.daybreak.ui.timetable

import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalTime

/** How many lessons are drawn side by side. More than this collapse into a "+N" chip. */
const val MAX_VISIBLE_COLUMNS = 2

/**
 * Lessons that did not fit beside each other, as one run in time. [hidden] are the blocks that are
 * not drawn; [concurrent] is everything that overlaps the run (hidden or not), for the list that the
 * chip opens.
 */
data class TimelineOverflow(
    val start: LocalTime,
    val end: LocalTime,
    val hidden: List<TimelineBlock>,
    val concurrent: List<TimelineBlock>,
)

/**
 * The result of laying a day out. [visible] are placed in at most [MAX_VISIBLE_COLUMNS] columns,
 * [overflows] stand for the blocks that were left out, and [all] is every block, for the gaps
 * between lessons.
 */
data class TimelineLayout(
    val all: List<TimelineBlock>,
    val visible: List<TimelineBlock>,
    val overflows: List<TimelineOverflow>,
)

/**
 * Lays [blocks] out in side-by-side columns, at most [maxColumns] of them. When more lessons run at
 * the same time, the ones with the highest [priority] are kept (ties go to the earlier one) as long
 * as no moment has more than [maxColumns] of them; the rest are reported as [TimelineOverflow].
 */
fun layoutTimeline(
    blocks: List<TimelineBlock>,
    maxColumns: Int = MAX_VISIBLE_COLUMNS,
    priority: (TimelineBlock) -> Int = { 0 },
): TimelineLayout {
    val sorted = blocks.sortedWith(compareBy({ it.start }, { it.end }))
    val chosen = chooseVisible(sorted, maxColumns, priority)
    val chosenSet = chosen.toHashSet()
    val hidden = sorted.filter { it !in chosenSet }
    return TimelineLayout(
        all = sorted,
        visible = placeInColumns(chosen),
        overflows = groupOverflows(hidden, sorted),
    )
}

private fun chooseVisible(
    sorted: List<TimelineBlock>,
    maxColumns: Int,
    priority: (TimelineBlock) -> Int,
): List<TimelineBlock> {
    if (maxOverlap(sorted) <= maxColumns) return sorted
    val order = sorted.indices.sortedWith(
        compareByDescending<Int> { priority(sorted[it]) }.thenBy { it },
    )
    val picked = mutableListOf<TimelineBlock>()
    for (index in order) {
        val candidate = sorted[index]
        if (fits(picked, candidate, maxColumns)) picked += candidate
    }
    return picked
}

private fun overlaps(a: TimelineBlock, b: TimelineBlock): Boolean =
    a.start < b.end && b.start < a.end

private fun maxOverlap(blocks: List<TimelineBlock>): Int =
    blocks.maxOfOrNull { block ->
        blocks.count { it.start <= block.start && block.start < it.end }
    } ?: 0

/** True when adding [candidate] to [picked] keeps every moment at [maxColumns] lessons or fewer. */
private fun fits(picked: List<TimelineBlock>, candidate: TimelineBlock, maxColumns: Int): Boolean {
    val touching = picked.filter { overlaps(it, candidate) }
    if (touching.size < maxColumns) return true
    // The busiest moment of the candidate is where it starts or where a touching block starts.
    val moments = touching.map { it.start }.filter { it > candidate.start } + candidate.start
    return moments.all { moment -> touching.count { it.start <= moment && moment < it.end } < maxColumns }
}

/** Puts overlapping blocks into columns, one cluster of overlapping blocks at a time. */
private fun placeInColumns(blocks: List<TimelineBlock>): List<TimelineBlock> {
    if (blocks.size <= 1) return blocks
    val sorted = blocks.sortedWith(compareBy({ it.start }, { it.end }))
    val result = mutableListOf<TimelineBlock>()
    var cluster = mutableListOf<TimelineBlock>()
    var clusterEnd = LocalTime.MIN

    fun flush() {
        if (cluster.isEmpty()) return
        val columnEnds = mutableListOf<LocalTime>()
        val placed = cluster.map { block ->
            val freeColumn = columnEnds.indexOfFirst { !it.isAfter(block.start) }
            val column = if (freeColumn == -1) {
                columnEnds += block.end
                columnEnds.lastIndex
            } else {
                columnEnds[freeColumn] = block.end
                freeColumn
            }
            block.copy(column = column)
        }
        val count = columnEnds.size.coerceAtLeast(1)
        result += placed.map { it.copy(columnCount = count) }
        cluster = mutableListOf()
        clusterEnd = LocalTime.MIN
    }

    for (block in sorted) {
        if (cluster.isNotEmpty() && !block.start.isBefore(clusterEnd)) {
            flush()
        }
        cluster += block
        if (block.end > clusterEnd) clusterEnd = block.end
    }
    flush()
    return result.sortedWith(compareBy({ it.start }, { it.column }))
}

/** Joins hidden blocks that overlap each other into runs, one chip each. */
private fun groupOverflows(hidden: List<TimelineBlock>, all: List<TimelineBlock>): List<TimelineOverflow> {
    if (hidden.isEmpty()) return emptyList()
    val overflows = mutableListOf<TimelineOverflow>()
    var group = mutableListOf<TimelineBlock>()
    var groupEnd = LocalTime.MIN

    fun close() {
        if (group.isEmpty()) return
        val start = group.minOf { it.start }
        overflows += TimelineOverflow(
            start = start,
            end = groupEnd,
            hidden = group.toList(),
            concurrent = all.filter { it.start < groupEnd && start < it.end },
        )
        group = mutableListOf()
    }

    for (block in hidden) {
        if (group.isNotEmpty() && !block.start.isBefore(groupEnd)) close()
        group += block
        if (block.end > groupEnd) groupEnd = block.end
    }
    close()
    return overflows
}

data class TimelineBlock(
    val lessons: List<UntisLesson>,
    val start: LocalTime,
    val end: LocalTime,
    val column: Int = 0,
    val columnCount: Int = 1,
) {
    val lesson: UntisLesson get() = lessons.first()
    val isCancelled: Boolean get() = lessons.any { it.isCancelled }
    val hasRoomChange: Boolean get() = lessons.any { it.hasRoomChange }
    val hasSubstitution: Boolean get() = lessons.any { it.hasSubstitution }
    val durationMinutes: Int get() = minutesBetweenLessons(start, end)
}

private fun minutesBetweenLessons(start: LocalTime, end: LocalTime): Int =
    java.time.Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)
