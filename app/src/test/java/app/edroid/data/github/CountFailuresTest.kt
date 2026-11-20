package app.edroid.data.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CountFailuresTest {

    @Test
    fun `a failure is counted and never stops the rest`() = runTest {
        val seen = mutableListOf<Int>()
        val reported = mutableListOf<Int>()

        val failures = countFailures(listOf(1, 2, 3, 4), onFailure = { item, _ -> reported += item }) {
            seen += it
            if (it % 2 == 0) {
                throw IOException("rate limit")
            }
        }

        assertEquals(2, failures)
        assertEquals(listOf(1, 2, 3, 4), seen)
        assertEquals(listOf(2, 4), reported)
    }

    @Test
    fun `no failure counts zero`() = runTest {
        assertEquals(0, countFailures(listOf(1, 2)) {})
        assertEquals(0, countFailures(emptyList<Int>()) { throw IOException() })
    }

    @Test
    fun `cancellation is rethrown, not counted`() {
        val seen = mutableListOf<Int>()

        assertThrows(CancellationException::class.java) {
            runTest {
                countFailures(listOf(1, 2)) {
                    seen += it
                    throw CancellationException("cancelled")
                }
            }
        }
        assertEquals(listOf(1), seen)
    }
}
