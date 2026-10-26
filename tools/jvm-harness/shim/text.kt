package android.text

object TextUtils {
    const val CAP_MODE_CHARACTERS = 4096
    const val CAP_MODE_WORDS = 8192
    const val CAP_MODE_SENTENCES = 16384
    @JvmStatic fun isEmpty(s: CharSequence?): Boolean = s == null || s.isEmpty()
    @JvmStatic fun join(delimiter: CharSequence, tokens: Iterable<*>): String = tokens.joinToString(delimiter.toString())
}

object InputType {
    const val TYPE_CLASS_TEXT = 1
    const val TYPE_TEXT_FLAG_CAP_SENTENCES = 16384
    const val TYPE_TEXT_FLAG_MULTI_LINE = 131072
    const val TYPE_NULL = 0
}
