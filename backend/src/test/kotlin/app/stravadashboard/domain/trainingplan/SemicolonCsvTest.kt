package app.stravadashboard.domain.trainingplan

import kotlin.test.Test
import kotlin.test.assertEquals

class SemicolonCsvTest {
    @Test
    fun `004 R2 - quoted cells and doubled quotes`() {
        val rows = SemicolonCsv.parse("NOTES;\"6x10 - rest 1'30\"\"\";\"a;b\"\r\nx;y\r\n")
        assertEquals(listOf(listOf("NOTES", "6x10 - rest 1'30\"", "a;b"), listOf("x", "y")), rows)
    }

    @Test
    fun `004 R2 - ragged rows read missing cells as empty`() {
        val rows = SemicolonCsv.parse("a;b;c\nd\n;;\ne;f")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d"), listOf("", "", ""), listOf("e", "f")), rows)
        assertEquals("", rows[1].cell(2))
        assertEquals("b", rows[0].cell(1))
    }
}
