package org.liamjd.apiviaduct.routing

/**
 * Basic representation of a mime type like "text/html" or "application/json"
 */
data class MimeType(val type: String, val subType: String) {

    /**
     * Check if the given Accept header matches this mime type
     * @param acceptHeader the Accept header to check
     * @return true if the accept header matches this mime type
     */
    fun matches(acceptHeader: String?): Boolean {
        return acceptHeader?.let { parse(it).type == type && parse(acceptHeader).subType == subType } ?: false
    }

    override fun toString() = "$type/$subType"

    /**
     * True if this mime type contains a wildcard, e.g. the whole-type wildcard or "image/&#42;"
     */
    val isWild: Boolean
        get() = type == "*" || subType == "*"

    // TODO : this is just scratching the surface of the requirements around mimetypes
    fun isCompatibleWith(other: MimeType): Boolean = when {
        this == other -> true
        // wildcards, as sent in Accept headers; curl and browsers send a whole-type wildcard by default
        type == "*" && subType == "*" -> true
        other.type == "*" && other.subType == "*" -> true
        type == other.type && (subType == "*" || other.subType == "*") -> true
        else -> type == other.type && subType.contains("+") && other.subType.contains("+")
    }


    /** Constant definitions for the various mime types */
    companion object {
        // application
        val json = MimeType("application", "json")
        val yaml = MimeType("application", "yaml")
        val javascript = MimeType("application", "javascript")
        val pdf = MimeType("application", "pdf")
        val xml = MimeType("application", "xml")
        // text
        val html = MimeType("text", "html")
        val plainText = MimeType("text", "plain")
        val markdown = MimeType("text", "markdown")
        val css = MimeType("text", "css")
        // image
        val jpg = MimeType("image", "jpeg")
        val png = MimeType("image", "png")
        val gif = MimeType("image", "gif")
        val svg = MimeType("image", "svg+xml")
        val webp = MimeType("image", "webp")

        /**
         * Parse a string into a MimeType object, ignoring any parameters
         * @param string the string to parse, e.g. "text/html" or "application/xml;q=0.9"
         * @return a MimeType object
         */
        fun parse(string: String): MimeType {
            val parts = string.substringBefore(';').trim().split('/')
            return if (parts.size >= 2) {
                MimeType(parts[0].lowercase(), parts[1].lowercase())
            } else {
                // a bare type with no subtype, e.g. "*" — treat the subtype as a wildcard
                MimeType(parts[0].lowercase(), "*")
            }
        }
    }
}