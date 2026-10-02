package app.pocketos.domain

import androidx.compose.ui.text.AnnotatedString
import app.pocketos.ui.components.AmountInput
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun `sanitize keeps digits and one decimal point within the currency's minor units`() {
        assertEquals("1250000", AmountInput.sanitize("1,250,000", "IRT"))
        assertEquals("1250000", AmountInput.sanitize("۱۲۵۰۰۰۰", "IRT"))
        assertEquals("12.5", AmountInput.sanitize("12.5", "USD"))
        assertEquals("12.99", AmountInput.sanitize("12.999", "USD"))
        assertEquals("12.5", AmountInput.sanitize("12٫5", "USD"))
        assertEquals("1234.5", AmountInput.sanitize("1,234.5", "USD"))
        assertEquals("125", AmountInput.sanitize("12.5", "IRT"))
        assertEquals("0.5", AmountInput.sanitize(".5", "EUR"))
        assertEquals("7", AmountInput.sanitize("007", "USD"))
        assertEquals("", AmountInput.sanitize("abc", "USD"))
    }

    @Test
    fun `adapt trims fraction digits when switching currency`() {
        assertEquals("12", AmountInput.adapt("12.50", "IRT"))
        assertEquals("12.5", AmountInput.adapt("12.5", "USD"))
        assertEquals("1000", AmountInput.adapt("1000", "JPY"))
    }

    @Test
    fun `grouping inserts separators in threes and maps the cursor`() {
        val t = AmountInput.Grouping(persian = false).filter(AnnotatedString("1250000.5"))
        assertEquals("1,250,000.5", t.text.text)
        assertEquals(0, t.offsetMapping.originalToTransformed(0))
        assertEquals(11, t.offsetMapping.originalToTransformed(9))
        assertEquals(9, t.offsetMapping.transformedToOriginal(11))
        // Cursor after "1" sits after the first separator.
        assertEquals(2, t.offsetMapping.originalToTransformed(1))
        assertEquals("1250000", AmountInput.sanitize("1,250,000", "USD"))
        assertEquals("999", AmountInput.Grouping(false).filter(AnnotatedString("999")).text.text)
        assertEquals("1,000", AmountInput.Grouping(false).filter(AnnotatedString("1000")).text.text)
    }

    @Test
    fun `persian grouping localises digits and separators`() {
        val t = AmountInput.Grouping(persian = true).filter(AnnotatedString("1250000"))
        assertEquals("۱٬۲۵۰٬۰۰۰", t.text.text)
    }
}
