package com.vgallery.app.data.gallery

/**
 * A–Z the way a file manager means it: a run of digits inside a name compares as a number, not as
 * text, so `1(2).png` sorts before `1(10).png`. Plain text order puts `1(10)` first — 1 before 2 —
 * which is exactly why a numbered folder looked shuffled even though it *was* "sorted".
 *
 * Case only decides names that are otherwise equal, so the ordering is always deterministic and a
 * `sortedWith` built on it is well behaved.
 */
fun naturalCompare(left: String, right: String): Int {
    var i = 0
    var j = 0
    while (i < left.length && j < right.length) {
        val a = left[i]
        val b = right[j]
        if (a.isDigit() && b.isDigit()) {
            val aStart = i
            val bStart = j
            while (i < left.length && left[i].isDigit()) i++
            while (j < right.length && right[j].isDigit()) j++
            // Leading zeros carry no value; a longer run of significant digits is the bigger number.
            val aDigits = left.substring(aStart, i).trimStart('0')
            val bDigits = right.substring(bStart, j).trimStart('0')
            if (aDigits.length != bDigits.length) return aDigits.length - bDigits.length
            val byValue = aDigits.compareTo(bDigits)
            if (byValue != 0) return byValue
            // Same number written differently ("7" / "007"): keep reading the rest of the name.
            continue
        }
        val aLower = a.lowercaseChar()
        val bLower = b.lowercaseChar()
        if (aLower != bLower) return aLower.compareTo(bLower)
        i++
        j++
    }
    // One name is the other plus more characters: the shorter one comes first ("IMG" < "IMG1").
    if (i < left.length) return 1
    if (j < right.length) return -1
    return left.compareTo(right)
}
