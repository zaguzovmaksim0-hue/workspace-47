package dev.junta.firmamobile.catalog

import java.text.Normalizer
import java.util.Locale

data class RegionAddress(
    val countryCode: String?,
    val adminArea: String?,
    val subAdminArea: String?,
    val locality: String?,
)

object PortalRegionResolver {
    fun resolve(address: RegionAddress): PortalRegionCode? {
        if (address.countryCode?.uppercase(Locale.ROOT) != "ES") return null
        return sequenceOf(address.adminArea, address.subAdminArea, address.locality)
            .filterNotNull()
            .flatMap { candidate -> candidate.aliasCandidates() }
            .mapNotNull(REGION_ALIASES::get)
            .firstOrNull()
    }

    private fun String.aliasCandidates(): Sequence<String> {
        val normalized = regionKey()
        return sequenceOf(
            normalized,
            normalized.removePrefix("comunidad autonoma de "),
            normalized.removePrefix("comunidad de "),
            normalized.removePrefix("provincia de "),
            normalized.removeSuffix(" province"),
        ).distinct()
    }

    private fun String.regionKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(MULTIPLE_SPACES, " ")

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val MULTIPLE_SPACES = Regex("\\s+")

    private val REGION_ALIASES: Map<String, PortalRegionCode> = buildMap {
        aliases(PortalRegionCode.ANDALUSIA, "Andalucía", "Andalusia")
        aliases(PortalRegionCode.ARAGON, "Aragón", "Aragon")
        aliases(PortalRegionCode.ASTURIAS, "Principado de Asturias", "Asturias")
        aliases(PortalRegionCode.CANTABRIA, "Cantabria")
        aliases(
            PortalRegionCode.CASTILE_AND_LEON,
            "Castilla y León", "Castilla León", "Castile and Leon",
        )
        aliases(PortalRegionCode.CASTILE_LA_MANCHA, "Castilla-La Mancha", "Castile La Mancha")
        aliases(
            PortalRegionCode.CANARY_ISLANDS,
            "Canarias", "Islas Canarias", "Canary Islands",
        )
        aliases(PortalRegionCode.CATALONIA, "Cataluña", "Catalunya", "Catalonia")
        aliases(PortalRegionCode.EXTREMADURA, "Extremadura")
        aliases(PortalRegionCode.GALICIA, "Galicia")
        aliases(
            PortalRegionCode.BALEARIC_ISLANDS,
            "Illes Balears", "Islas Baleares", "Baleares", "Balearic Islands",
        )
        aliases(PortalRegionCode.MURCIA, "Región de Murcia", "Murcia")
        aliases(PortalRegionCode.MADRID, "Comunidad de Madrid", "Madrid")
        aliases(
            PortalRegionCode.NAVARRE,
            "Comunidad Foral de Navarra", "Navarra", "Nafarroa", "Navarre",
        )
        aliases(
            PortalRegionCode.BASQUE_COUNTRY,
            "País Vasco", "Euskadi", "Basque Country",
        )
        aliases(PortalRegionCode.LA_RIOJA, "La Rioja")
        aliases(
            PortalRegionCode.VALENCIAN_COMMUNITY,
            "Comunidad Valenciana", "Comunitat Valenciana", "Valencian Community",
        )
        aliases(PortalRegionCode.CEUTA, "Ciudad Autónoma de Ceuta", "Ceuta")
        aliases(PortalRegionCode.MELILLA, "Ciudad Autónoma de Melilla", "Melilla")

        aliases(
            PortalRegionCode.ANDALUSIA,
            "Almería", "Cádiz", "Córdoba", "Granada", "Huelva", "Jaén", "Málaga", "Sevilla",
        )
        aliases(PortalRegionCode.ARAGON, "Huesca", "Teruel", "Zaragoza")
        aliases(
            PortalRegionCode.CASTILE_AND_LEON,
            "Ávila", "Burgos", "León", "Palencia", "Salamanca", "Segovia", "Soria",
            "Valladolid", "Zamora",
        )
        aliases(
            PortalRegionCode.CASTILE_LA_MANCHA,
            "Albacete", "Ciudad Real", "Cuenca", "Guadalajara", "Toledo",
        )
        aliases(PortalRegionCode.CANARY_ISLANDS, "Las Palmas", "Santa Cruz de Tenerife")
        aliases(
            PortalRegionCode.CATALONIA,
            "Barcelona", "Girona", "Gerona", "Lleida", "Lérida", "Tarragona",
        )
        aliases(PortalRegionCode.EXTREMADURA, "Badajoz", "Cáceres")
        aliases(
            PortalRegionCode.GALICIA,
            "A Coruña", "La Coruña", "Lugo", "Ourense", "Orense", "Pontevedra",
        )
        aliases(
            PortalRegionCode.BASQUE_COUNTRY,
            "Álava", "Araba", "Bizkaia", "Vizcaya", "Gipuzkoa", "Guipúzcoa",
        )
        aliases(
            PortalRegionCode.VALENCIAN_COMMUNITY,
            "Alicante", "Alacant", "Castellón", "Castelló", "Valencia", "València",
        )
    }

    private fun MutableMap<String, PortalRegionCode>.aliases(
        region: PortalRegionCode,
        vararg names: String,
    ) {
        names.forEach { name -> put(name.regionKey(), region) }
    }
}
