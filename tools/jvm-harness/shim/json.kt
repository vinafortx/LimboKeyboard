package org.json

class JSONException(message: String) : Exception(message)

open class JSONObject {
    private val map = LinkedHashMap<String, Any?>()
    constructor()
    constructor(json: String) { if (json.isBlank()) throw JSONException("empty") }

    fun put(key: String, value: Any?): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Boolean): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Int): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Long): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Double): JSONObject { map[key] = value; return this }
    fun get(key: String): Any? = map[key] ?: throw JSONException("no $key")
    fun optString(key: String, def: String? = ""): String = map[key]?.toString() ?: def ?: ""
    fun optInt(key: String, def: Int = 0): Int = (map[key] as? Number)?.toInt() ?: def
    fun optLong(key: String, def: Long = 0L): Long = (map[key] as? Number)?.toLong() ?: def
    fun optDouble(key: String, def: Double = 0.0): Double = (map[key] as? Number)?.toDouble() ?: def
    fun optBoolean(key: String, def: Boolean = false): Boolean = map[key] as? Boolean ?: def
    fun has(key: String): Boolean = map.containsKey(key)
    fun keys(): Iterator<String> = map.keys.iterator()
    fun length(): Int = map.size
    override fun toString(): String = map.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${if (v is String) "\"$v\"" else v}" }
}

class JSONArray {
    private val list = ArrayList<Any?>()
    constructor()
    constructor(json: String) { if (json.isBlank()) throw JSONException("empty") }
    fun put(value: Any?): JSONArray { list.add(value); return this }
    fun length(): Int = list.size
    fun getJSONObject(index: Int): JSONObject = list[index] as JSONObject
    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun getString(index: Int): String = list[index].toString()
    override fun toString(): String = list.joinToString(",", "[", "]") { if (it is String) "\"$it\"" else it.toString() }
}
