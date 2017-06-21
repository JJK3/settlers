package org.jjk3.board

import java.util.*
import java.util.concurrent.TimeoutException

class IllegalArgumentException(msg: String) : Exception(msg)
object Util {
    fun while_with_timeout(timeout: Long = 10000, sleep_millis: Long = 100, test: () -> Boolean) {
        val maxTimestamp = System.currentTimeMillis() + timeout
        while (test.invoke()) {
            if (System.currentTimeMillis() > maxTimestamp) {
                throw TimeoutException("Timeout expired. " + timeout)
            }
            Thread.sleep(sleep_millis)
        }
    }
}

fun <T> List<T>.pickRandom(): T {
    if (isEmpty()) throw  IllegalArgumentException("List cannot be empty")
    val rand = Random(System.currentTimeMillis())
    return get(rand.nextInt(size))
}

/** select a random element from a list and return a  list with that element removed. */
fun <T> List<T>.remove_random(): Pair<T, List<T>> {
    if (isEmpty()) throw  IllegalArgumentException("List cannot be empty")
    val rand = Random(System.currentTimeMillis())
    val index = rand.nextInt(size)
    return Pair(get(index), remove(index))
}

fun <T> List<T>.remove(index: Int): List<T> {
    return subList(0, index) + subList(index + 1, size)
}
