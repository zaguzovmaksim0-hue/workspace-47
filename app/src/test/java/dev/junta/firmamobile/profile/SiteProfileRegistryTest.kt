package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SiteProfileRegistryTest {
    @Test
    fun `shared Clave origins stay globally ambiguous and resolve only inside the selected profile`() {
        val clave = URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider")
        val claveIdent = URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen")
        val airef = ProfileId("airef-instancia-general")
        val mineco = ProfileId("ministerio-economia-instancia-generica")

        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(clave))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(claveIdent))
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(airef, clave)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(airef, claveIdent)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(mineco, clave)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(mineco, claveIdent)?.trustMode,
        )
    }

    @Test
    fun `AEAT client TLS profile is exact and QA only before physical E2E`() {
        val profileId = ProfileId("aeat-mis-datos-censales")
        val source = URI("https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html")
        val target = URI("https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(source))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(target))

        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(source)?.trustMode)
        assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.qaRegistry.resolve(target)?.trustMode)
    }

    @Test
    fun `release and qa registries activate verified carne joven profile`() {
        val carneId = ProfileId("carne-joven-andalucia")

        val releaseProfile = BuiltInSiteProfiles.releaseRegistry.profile(carneId)
        assertNotNull(releaseProfile)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, releaseProfile?.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, releaseProfile?.activation)

        val qaProfile = BuiltInSiteProfiles.qaRegistry.profile(carneId)
        assertNotNull(qaProfile)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, qaProfile?.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, qaProfile?.activation)
    }

    @Test
    fun `resolves carne joven start url and facade url in release registry`() {
        val startUri = URI("https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp")
        val facadeUri = URI("https://ws235.juntadeandalucia.es/authenticationFacade")

        val startResolved = BuiltInSiteProfiles.releaseRegistry.resolve(startUri)
        assertNotNull(startResolved)
        assertEquals(ProfileId("carne-joven-andalucia"), startResolved?.profile?.profileId)
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, startResolved?.trustMode)

        val facadeResolved = BuiltInSiteProfiles.releaseRegistry.resolve(facadeUri)
        assertNotNull(facadeResolved)
        assertEquals(ProfileId("carne-joven-andalucia"), facadeResolved?.profile?.profileId)
        assertEquals(TrustMode.BROWSE_ONLY, facadeResolved?.trustMode)
    }
    @Test
    fun `JCCM certificate probe is QA-only and origin exact`() {
        val profileId = ProfileId("jccm-certificate-login-probe")
        val startUri = URI(
            "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                "formularios/identificacion.phtml",
        )

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUri))

        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(1, profile?.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(startUri)?.trustMode,
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://ventanillaelectronica.jccm.es.evil.example/"),
            ),
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://ventanillaelectronica.jccm.es:444/"),
            ),
        )
    }

    @Test
    fun `aragon shared origin stays fail closed globally and resolves inside selected profile`() {
        val sirawProfileId = ProfileId("aragon-siraw")
        val tramitesProfileId = ProfileId("aragon-solicitud-general-client-auth")
        val sirawStart = URI("https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw")
        val tramitesStart = URI("https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion")

        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.releaseRegistry.resolve(sirawStart)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(tramitesProfileId))

        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(sirawStart))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(tramitesStart))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(sirawProfileId, sirawStart)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(tramitesProfileId, tramitesStart)?.trustMode,
        )
    }

    @Test
    fun `release and qa resolve verified Junta Oficina Virtual login`() {
        val profileId = ProfileId("junta-ofvirtual")
        val startUri = URI("https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs")

        listOf(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.qaRegistry).forEach { registry ->
            val profile = registry.profile(profileId)
            assertNotNull(profile)
            assertEquals(2, profile?.profileVersion)
            assertEquals(CompatibilityStatus.VERIFIED_E2E, profile?.compatibilityStatus)
            assertEquals(ProfileActivation.ENABLED, profile?.activation)
            assertEquals(TrustMode.TRUSTED_SIGNING, registry.resolve(startUri)?.trustMode)
        }
    }

    @Test
    fun `Melilla batch profile is QA-only and origin exact`() {
        val profileId = ProfileId("melilla-sede")
        val startUri = URI(
            "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
                "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
        )

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUri))

        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(1, profile?.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(startUri)?.trustMode,
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.melilla.es.evil.example/")),
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.melilla.es:444/")),
        )
    }

    @Test
    fun `UGR certificate contract is QA-only and origin exact`() {
        val profileId = ProfileId("ugr-certificado-login")
        val startUri = URI("https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUri))

        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(1, profile?.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(startUri)?.trustMode,
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ugr.es.evil.example/")),
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ugr.es:444/")),
        )
    }

    @Test
    fun `OEPM ProtegeO navigation profile is active only in QA and never upgrades to signing trust`() {
        val profileId = ProfileId("oepm-protegeo-general")
        val start = URI(
            "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM",
        )

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.oepm.gob.es.evil.example/")))
    }

    @Test
    fun `Portal Funciona public home is browse-only trust in QA and external auth origins stay closed`() {
        val profileId = ProfileId("portal-funciona-public-home")
        val start = URI("https://sede.funciona.gob.es/es/home")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://auth-api.redsara.es/auth/realms/sgad-appfactory/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://autentica.redsara.es/Autentica/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.funciona.gob.es.evil.example/")))
    }

    @Test
    fun `Junta VEA PEG is QA-only browse and rejects auth API as profile origin`() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val start = URI("https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, BuiltInSiteProfiles.qaRegistry.profile(profileId)?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, BuiltInSiteProfiles.qaRegistry.profile(profileId)?.activation)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://api-veaja.cloud.juntadeandalucia.es/auth/login")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://veaja.cloud.juntadeandalucia.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://veaja.cloud.juntadeandalucia.es:444/")))
    }

}
