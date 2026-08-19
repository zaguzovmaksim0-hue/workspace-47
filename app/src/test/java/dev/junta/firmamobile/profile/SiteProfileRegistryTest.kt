package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteProfileRegistryTest {
    @Test
    fun `shared Clave origins stay globally ambiguous and resolve only inside the selected profile`() {
        val clave = URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider")
        val claveIdent = URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen")
        val airef = ProfileId("airef-instancia-general")
        val mugeju = ProfileId("mugeju-remision-documentacion-client-auth")
        val mineco = ProfileId("ministerio-economia-instancia-generica")
        val avila = ProfileId("diputacion-avila-instancia-general")
        val jccmRegistro = ProfileId("jccm-registro-generico")
        val palencia = ProfileId("diputacion-palencia-solicitud-general")
        val elHierro = ProfileId("el-hierro-solicitud-general")

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
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(mugeju, clave)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(mugeju, claveIdent)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(avila, clave)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(palencia, clave)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(palencia, claveIdent))
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(elHierro, clave)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(avila, claveIdent)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(jccmRegistro, clave)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(jccmRegistro, claveIdent)?.trustMode,
        )
    }

    @Test
    fun `shared Transportes origin resolves exact starts but stays ambiguous for origin only URLs`() {
        val signingId = ProfileId("transportes-qys-cert-login")
        val sepesId = ProfileId("sepes-transportes-public-complaints")
        val signingStart = URI("https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002")
        val sepesStart = URI("https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones")
        val originOnly = URI("https://sede.transportes.gob.es/")

        assertEquals(signingId, BuiltInSiteProfiles.qaRegistry.resolve(signingStart)?.profile?.profileId)
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(signingStart)?.trustMode)
        assertEquals(sepesId, BuiltInSiteProfiles.qaRegistry.resolve(sepesStart)?.profile?.profileId)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(sepesStart)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(originOnly))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolveForProfile(signingId, originOnly)?.trustMode)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolveForProfile(sepesId, originOnly)?.trustMode)
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
    fun `release and qa resolve verified aragon siraw login`() {
        val profileId = ProfileId("aragon-siraw")
        val startUri = URI("https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw")

        listOf(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.qaRegistry).forEach { registry ->
            val profile = registry.profile(profileId)
            assertNotNull(profile)
            assertEquals(CompatibilityStatus.VERIFIED_E2E, profile?.compatibilityStatus)
            assertEquals(ProfileActivation.ENABLED, profile?.activation)
            assertEquals(TrustMode.TRUSTED_SIGNING, registry.resolve(startUri)?.trustMode)
        }
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
    fun `Castilla Leon QUJU public form is browse-only trust in QA and near origins stay closed`() {
        val profileId = ProfileId("castilla-leon-quju-public")
        val start = URI("https://presidencia.jcyl.es/QUJU?O=1")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.tramitacastillayleon.jcyl.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://presidencia.jcyl.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://presidencia.jcyl.es:444/")))
    }

    @Test
    fun `BOE public Sede is browse-only trust in QA and extranet stays closed`() {
        val profileId = ProfileId("boe-sede-public-home")
        val start = URI("https://www.boe.es/informacion/index.php")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://extranet.boe.es/quejas_el/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.boe.es.evil.example/")))
    }

    @Test
    fun `Fondos Europeos public Sede is browse-only trust in QA and external service origins stay closed`() {
        val profileId = ProfileId("fondos-europeos-sede-public-home")
        val start = URI("https://sedefondoscomunitarios.gob.es/")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://tramitesfondoseuropeos.hacienda.gob.es/dossier")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://reg.redsara.es/es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sedefondoscomunitarios.gob.es.evil.example/")))
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
    fun `DGSFP public Sede is browse-only trust in QA and sensitive routes remain profile-scoped closed`() {
        val profileId = ProfileId("dgsfp-sede-public-home")
        val start = URI("https://www.sededgsfp.gob.es/")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://www.sededgsfp.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://www.sededgsfp.gob.es:444/")))
    }

    @Test
    fun `DGOJ public navigation stays browse-only in QA and external signing systems stay closed`() {
        val profileId = ProfileId("dgoj-public-navigation")
        val start = URI("https://sede.ordenacionjuego.gob.es/")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ordenacionjuego.gob.es/tramite/"))?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://clave.gob.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://administracionelectronica.gob.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ordenacionjuego.gob.es.evil.example/")))
    }

    @Test
    fun `CNMV public Sede is browse-only trust in QA and near origins stay closed`() {
        val profileId = ProfileId("cnmv-sede-public-home")
        val start = URI("https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.cnmv.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.cnmv.gob.es:444/")))
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

    @Test
    fun `Murcia CARM PASE navigation is QA-only and redirect origins stay browse only`() {
        val profileId = ProfileId("murcia-carm-pase")
        val start = URI(
            "https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288",
        )

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        val profile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(profile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile?.activation)
        assertEquals(emptySet<Capability>(), profile?.capabilities)
        assertTrue(profile?.endpoints?.isEmpty() == true)
        assertTrue(profile?.operationPolicies?.isEmpty() == true)
        assertNull(profile?.clientAuthPolicy)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)

        listOf(
            "https://validate.perfdrive.com/challenge",
            "https://pase.carm.es/pase/login",
            "https://conclave.carm.es/TokenServlet",
        ).forEach { raw ->
            val uri = URI(raw)
            assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, uri)?.trustMode)
            assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolveRedirect(profileId, uri)?.trustMode)
        }
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.carm.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveRedirect(profileId, URI("https://pasarela.clave.gob.es/Clave2/")))
    }

    @Test
    fun `Asturias Sede navigation trusts only the observed miPrincipado redirect in QA`() {
        val profileId = ProfileId("asturias-sede-tramite-navigation")
        val start = URI("https://sede.asturias.es/ast/-/dboid-6269000011903512107573")
        val redirected = URI("https://miprincipado.asturias.es/ast/-/dboid-6269000011903512107573")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(redirected))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolveRedirect(profileId, redirected)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://tramita.asturias.es/sta/Relec/STARhssoManager")))
        val miPrincipadoStart = URI(
            "https://miprincipado.asturias.es/-/dboid-6269000102616541907573?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites",
        )
        val miPrincipado = BuiltInSiteProfiles.qaRegistry.resolve(miPrincipadoStart)
        assertEquals(ProfileId("asturias-miprincipado"), miPrincipado?.profile?.profileId)
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, miPrincipado?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://miprincipado.asturias.es/unscoped")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://miprincipado.asturias.es.evil.example/")))
    }

    @Test
    fun `Madrid Cuenta Digital 53F1 keeps gestiona as redirect only and gestiona2 untrusted`() {
        val profileId = ProfileId("comunidad-madrid-cuenta-digital-53f1")
        val start = URI("https://digital.comunidad.madrid/ext/53F1")
        val login = URI("https://gestiona.comunidad.madrid/auto_login/acceso.jsf")
        val postTls = URI("https://gestiona2.comunidad.madrid/auto_certificado/SelCertificado")

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, login)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, postTls))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://digital.comunidad.madrid.evil.example/ext/53F1")))
    }

}
