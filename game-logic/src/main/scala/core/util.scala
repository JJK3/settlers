package core

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent._
import org.apache.log4j._

import scala.util.Random

class IllegalArgumentException(msg: String) extends Exception(msg) {}

object Util {
    def while_with_timeout(timeout: Long = 10000, sleep_millis: Long = 100)(test: () => Boolean) = {
        val maxTimestamp = System.currentTimeMillis + timeout
        while (test()) {
            if (System.currentTimeMillis > maxTimestamp)
                throw new TimeoutException("Timeout expired. " + timeout)
            Thread.sleep(sleep_millis)
        }
    }
}

class Mutex extends ReentrantLock {

    def synchronize[A](block: () => A): A = {
        try {
            this.lock
            block.apply()
        } finally {
            this.unlock
        }
    }
}

class UtilList[T](list: List[T]) {
    private val log = Logger.getLogger(classOf[UtilList[_]])

    def pick_random(): T = {
        if (list.isEmpty) throw new IllegalArgumentException("List cannot be empty")
        val rand = new Random(System.currentTimeMillis())
        list(rand.nextInt(list.size))
    }

    /** select a random element from a list and return a new list with that element removed. */
    def remove_random(): (T, List[T]) = {
        if (list.isEmpty) throw new IllegalArgumentException("List cannot be empty")
        val rand = new Random(System.currentTimeMillis())
        val index = rand.nextInt(list.size)
        (list(index), remove(index))
    }

    def remove(index: Int): List[T] = {
        list.take(index) ::: list.drop(index + 1)
    }

    /**
     * This removes elements from an array without making the array uniq.
     * i.e. [1,1,1,2].difference_without_uniq([1]) = [1,1,2]
     */
    def diff_without_unique(other: List[T]) = {
        var result = list
        other.foreach { obj: T =>
            val i = result.indexOf(obj)
            if (i > -1)
                result = new UtilList(result).remove(i)
        }
        result
    }

    /**
     * Spawn new threads for each iteration of the list.
     * Order is not guaranteed.
     */
    def iterate_threaded(
        wait_for_threads_to_finish: Boolean = false,
        time_limit: Int = 5 * 60,
        throw_timeout_exception: Boolean = true)(block: (T) => Unit): Unit = {
        if (list.isEmpty) {
            return
        }
        val runnables = list.map { t: T =>
            new Callable[Any]() {
                override def call() = {
                    try {
                        block.apply(t);
                    } catch {
                        case e =>
                            log.error(e, e)
                            throw e
                    }
                }
            }
        }
        //Submit and wait for them to finish
        val futures = runnables.map {
            SettlersExecutor.executor.submit(_)
        }

        if (wait_for_threads_to_finish) {
            try {
                futures.foreach { _.get(time_limit, TimeUnit.SECONDS) }
            } catch {
                case x: TimeoutException =>
                    if (throw_timeout_exception)
                        throw x;
                case x: Exception => throw x
            }
        }
    }
}

object UtilList {
    implicit def listToMyList[T](l: List[T]) = new UtilList(l)
}
