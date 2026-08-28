package dev.junta.firmamobile.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortalRegionResolverTest {
    @Test
    fun `resolves every autonomous community including local and English aliases`() {
        val aliases = mapOf(
            PortalRegionCode.ANDALUSIA to listOf("Andalucía", "Andalusia"),
            PortalRegionCode.ARAGON to listOf("Aragón", "Aragon"),
            PortalRegionCode.ASTURIAS to listOf("Principado de Asturias", "Asturias"),
            PortalRegionCode.CANTABRIA to listOf("Cantabria"),
            PortalRegionCode.CASTILE_AND_LEON to listOf("Castilla y León", "Castile and Leon"),
            PortalRegionCode.CASTILE_LA_MANCHA to listOf("Castilla-La Mancha", "Castile La Mancha"),
            PortalRegionCode.CANARY_ISLANDS to listOf("Canarias", "Canary Islands"),
            PortalRegionCode.CATALONIA to listOf("Cataluña", "Catalunya", "Catalonia"),
            PortalRegionCode.EXTREMADURA to listOf("Extremadura"),
            PortalRegionCode.GALICIA to listOf("Galicia"),
            PortalRegionCode.BALEARIC_ISLANDS to listOf("Illes Balears", "Islas Baleares"),
            PortalRegionCode.MURCIA to listOf("Región de Murcia", "Murcia"),
            PortalRegionCode.MADRID to listOf("Comunidad de Madrid", "Madrid"),
            PortalRegionCode.NAVARRE to listOf("Comunidad Foral de Navarra", "Nafarroa", "Navarre"),
            PortalRegionCode.BASQUE_COUNTRY to listOf("País Vasco", "Euskadi", "Basque Country"),
            PortalRegionCode.LA_RIOJA to listOf("La Rioja"),
            PortalRegionCode.VALENCIAN_COMMUNITY to listOf("Comunitat Valenciana", "Valencian Community"),
            PortalRegionCode.CEUTA to listOf("Ciudad Autónoma de Ceuta", "Ceuta"),
            PortalRegionCode.MELILLA to listOf("Ciudad Autónoma de Melilla", "Melilla"),
        )

        aliases.forEach { (expected, names) ->
            names.forEach { name ->
                assertEquals(
                    name,
                    expected,
                    PortalRegionResolver.resolve(RegionAddress("es", name, null, null)),
                )
            }
        }
    }

    @Test
    fun `resolves all Spanish provinces when community name is unavailable`() {
        val provinces = mapOf(
            PortalRegionCode.ANDALUSIA to listOf("Almería", "Cádiz", "Córdoba", "Granada", "Huelva", "Jaén", "Málaga", "Sevilla"),
            PortalRegionCode.ARAGON to listOf("Huesca", "Teruel", "Zaragoza"),
            PortalRegionCode.ASTURIAS to listOf("Asturias"),
            PortalRegionCode.CANTABRIA to listOf("Cantabria"),
            PortalRegionCode.CASTILE_AND_LEON to listOf("Ávila", "Burgos", "León", "Palencia", "Salamanca", "Segovia", "Soria", "Valladolid", "Zamora"),
            PortalRegionCode.CASTILE_LA_MANCHA to listOf("Albacete", "Ciudad Real", "Cuenca", "Guadalajara", "Toledo"),
            PortalRegionCode.CANARY_ISLANDS to listOf("Las Palmas", "Santa Cruz de Tenerife"),
            PortalRegionCode.CATALONIA to listOf("Barcelona", "Girona", "Lleida", "Tarragona"),
            PortalRegionCode.EXTREMADURA to listOf("Badajoz", "Cáceres"),
            PortalRegionCode.GALICIA to listOf("A Coruña", "Lugo", "Ourense", "Pontevedra"),
            PortalRegionCode.BALEARIC_ISLANDS to listOf("Illes Balears"),
            PortalRegionCode.MURCIA to listOf("Murcia"),
            PortalRegionCode.MADRID to listOf("Madrid"),
            PortalRegionCode.NAVARRE to listOf("Navarra"),
            PortalRegionCode.BASQUE_COUNTRY to listOf("Álava", "Bizkaia", "Gipuzkoa"),
            PortalRegionCode.LA_RIOJA to listOf("La Rioja"),
            PortalRegionCode.VALENCIAN_COMMUNITY to listOf("Alicante", "Castellón", "València"),
            PortalRegionCode.CEUTA to listOf("Ceuta"),
            PortalRegionCode.MELILLA to listOf("Melilla"),
        )

        provinces.forEach { (expected, names) ->
            names.forEach { province ->
                assertEquals(
                    province,
                    expected,
                    PortalRegionResolver.resolve(
                        RegionAddress("ES", null, "Provincia de $province", null),
                    ),
                )
            }
        }
    }

    @Test
    fun `outside Spain or an unknown Spanish address does not guess`() {
        assertNull(
            PortalRegionResolver.resolve(
                RegionAddress("PT", "Andalucía", null, null),
            ),
        )
        assertNull(
            PortalRegionResolver.resolve(
                RegionAddress("ES", "Territorio desconocido", null, null),
            ),
        )
    }
}
