package nl.spoonpaco.ninegrid.data.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** De laatste meldingen (breakout, verlanglijst-alarm, ban, reprint), voor het alarmen-widget. Per melding een tekst met en zonder bedragen. */
object AlertLog {
    data class Item(val ts: Long, val kind: String, val text: String, val masked: String)

    private const val PREFS = "widget_alerts"
    private const val KEY = "items"
    private const val MAX = 8

    fun add(ctx: Context, kind: String, text: String, masked: String) {
        val list = (recent(ctx, MAX) + Item(System.currentTimeMillis(), kind, text, masked)).takeLast(MAX)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("ts", it.ts).put("kind", it.kind).put("text", it.text).put("masked", it.masked)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** De laatste [n], nieuwste eerst. */
    fun recent(ctx: Context, n: Int): List<Item> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Item(o.optLong("ts"), o.optString("kind"), o.optString("text"), o.optString("masked")))
        }
        return out.sortedByDescending { it.ts }.take(n)
    }
}
