package org.jjk3.core

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

fun <T> List<T>.pick_random(): T {
    if (isEmpty()) throw  IllegalArgumentException("List cannot be empty")
    val rand = Random(System.currentTimeMillis())
    return get(rand.nextInt(size))
}

/** select a random element from a list and return a  list , that element removed. */
fun <T> List<T>.remove_random(): Pair<T, List<T>> {
    if (isEmpty()) throw  IllegalArgumentException("List cannot be empty")
    val rand = Random(System.currentTimeMillis())
    val index = rand.nextInt(size)
    return Pair(get(index), remove(index))
}

fun <T> List<T>.remove(index: Int): List<T> {
    return subList(0, index) + subList(index + 1, size)
}

/**
 * This removes elements from an array ,out making the array uniq.
 * i.e. [1,1,1,2].difference_,out_uniq([1]) = [1,1,2]
 */
fun <T> List<T>.diff_without_unique(other: List<T>): List<T> {
    var result = this
    other.forEach { obj: T ->
        val i = result.indexOf(obj)
        if (i > -1)
            result = result.remove(i)
    }
    return result
}
/*

*/
/**
 * Spawn  threads for each iteration of the list.
 * Order is not guaranteed.
 *//*

fun <T> List<T>.iterate_threaded(
        wait_for_threads_to_finish: Boolean = false,
        time_limit: Long = 5 * 60,
        throw_timeout_exception: Boolean = true, block: (T) -> Unit): Unit {
    if (isEmpty()) {
        return
    }
    val runnables = map { t: T -> Callable<Unit> { block.invoke(t) } }

    //Submit and wait for them to finish
    val futures = runnables.map { SettlersExecutor.executor.submit(it) }

    if (wait_for_threads_to_finish) {
        try {
            futures.forEach { it.get(time_limit, TimeUnit.SECONDS) }
        } catch (e: Exception) {
            when (e) {
                is TimeoutException -> if (throw_timeout_exception) {
                    throw e
                }
                else -> throw e
            }
        }
    }
}*/
