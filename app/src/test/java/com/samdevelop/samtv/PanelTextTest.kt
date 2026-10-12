package com.samdevelop.samtv

import com.samdevelop.samtv.ui.common.PanelText
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTextTest {
    @Test fun datasDoPainel() {
        assertEquals("31/12/2026", PanelText.date("2026-12-31 23:59:59"))
        assertEquals("31/12/2026", PanelText.date("2026-12-31T23:59:59-03:00"))
        assertEquals("Sem vencimento", PanelText.date(null))
        assertEquals("Sem vencimento", PanelText.date("  "))
        assertEquals("amanhã", PanelText.date("amanhã"))
        assertEquals("Vencimento: ilimitado", PanelText.expiry(null))
        assertEquals("Vence em 01/02/2027", PanelText.expiry("2027-02-01"))
    }
}
