package dev.junta.firmamobile.browser

@JvmInline
value class NavigationId(val value: String) {
    init {
        require(value.isNotBlank())
        require(value.length <= MAX_LENGTH)
        require(value.none(Char::isISOControl))
    }

    private companion object {
        const val MAX_LENGTH = 128
    }
}
