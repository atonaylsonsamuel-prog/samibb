package com.samdevelop.samtv.ui.common

/** Textos ligados ao painel. */
object PanelText {
    private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})""")

    /** "2026-12-31 23:59:59" ou ISO 8601 → "31/12/2026". Null → "Sem vencimento". */
    fun date(raw: String?): String {
        if (raw.isNullOrBlank()) return "Sem vencimento"
        val m = ISO_DATE.find(raw.trim()) ?: return raw
        val (y, mo, d) = m.destructured
        return "$d/$mo/$y"
    }

    fun expiry(raw: String?): String = if (raw.isNullOrBlank()) "Vencimento: ilimitado" else "Vence em ${date(raw)}"
}
