package ca.stewartclan.andb.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test
    fun writesQuotingOnlyWhatNeedsIt() {
        val text = Csv.write(listOf(listOf("Title", "Note"), listOf("Dune, part 1", "He said \"hi\"\nthen left")))
        assertEquals(
            Csv.BOM + "Title,Note\r\n\"Dune, part 1\",\"He said \"\"hi\"\"\nthen left\"\r\n",
            text
        )
    }

    @Test
    fun roundTrips() {
        val rows = listOf(
            listOf("a", "", "c,d"),
            listOf("\"quoted\"", "line1\r\nline2", "é ü ☆"),
            listOf("", "", "")
        )
        assertEquals(rows, Csv.read(Csv.write(rows)))
    }

    @Test
    fun readsLfEndingsWithoutBomOrFinalNewline() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.read("a,b\n1,2"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.read("a,b\n1,2\n"))
    }

    @Test
    fun emptyCellsAndRows() {
        assertEquals(listOf(listOf("", "")), Csv.read(","))
        assertEquals(listOf(listOf("x"), listOf("y")), Csv.read("x\n\ny\n"))
        assertEquals(emptyList<List<String>>(), Csv.read(""))
    }
}
