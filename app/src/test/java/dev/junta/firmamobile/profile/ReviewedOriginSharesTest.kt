package dev.junta.firmamobile.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewedOriginSharesTest {
    private val catalogPrefix get() = BuiltInSiteProfiles.JSON.substringBefore(DECLARATIONS_SEPARATOR)
    private val declarations get() = BuiltInSiteProfiles.JSON.substringAfter(DECLARATIONS_SEPARATOR)
        .trim().removeSuffix("}").trim()
    private val firstDeclaration get() = declarations.lines().first { it.trim().startsWith("{") }
        .trim().removeSuffix(",")

    @Test
    fun canonicalCatalogPublishesTypedDirectionalDeclarations() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        assertEquals(69, catalog.reviewedOriginShares.size)
        assertEquals(596, catalog.reviewedOriginShares.sumOf { it.laterOwners.size })
        assertTrue(catalog.reviewedOriginShares.all { share ->
            share.firstOwner !in share.laterOwners && share.redirectRoles.isNotEmpty()
        })
    }

    @Test
    fun missingDeclarationsRejectTheWholeCatalog() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse("$catalogPrefix}")
        }
        rejects("[]")
    }

    @Test
    fun removingEvenAnUnusedDirectionRejectsTheWholeCatalog() {
        val unused = declarations.lines().first {
            it.contains("\"firstOwner\":\"aragon-solicitud-general-client-auth\"")
        }.trim().removeSuffix(",")
        rejects(declarations.replaceFirst("$unused,", ""))
    }

    @Test
    fun duplicateAndOverlappingDeclarationsRejectTheWholeCatalog() {
        rejects(declarations.replaceFirst(firstDeclaration, "$firstDeclaration,$firstDeclaration"))
        mutateFirst("\"laterOwners\":[", "\"laterOwners\":[\"missing-peer\",")
    }

    @Test
    fun absentSelfAndUnreviewedOwnersFailClosed() {
        val firstOwner = Regex("\"firstOwner\":\"([^\"]+)\"").find(firstDeclaration)!!.groupValues[1]
        val firstPeer = Regex("\"laterOwners\":\\[\"([^\"]+)\"").find(firstDeclaration)!!.groupValues[1]
        for (peer in listOf(firstOwner, "missing-peer", "reg-age-redsara")) {
            mutateFirst("\"laterOwners\":[\"$firstPeer\"", "\"laterOwners\":[\"$peer\"")
        }
        mutateFirst("\"firstOwner\":\"$firstOwner\"", "\"firstOwner\":\"missing-owner\"")
        mutateFirst("\"laterOwners\":[\"$firstPeer\"", "\"laterOwners\":[\"$firstPeer\",\"$firstPeer\"")
    }

    @Test
    fun canonicalOriginAndExactClaimMembershipAreRequired() {
        val origin = Regex("\"origin\":\"([^\"]+)\"").find(firstDeclaration)!!.groupValues[1]
        val invalid = listOf(
            "$origin:443", "$origin:444", "$origin/", "$origin?q=1", "$origin#fragment",
            "$origin.evil.example", "https://user@${origin.removePrefix("https://")}",
            "https://localhost", "https://127.0.0.1", "https://*.example", "https://unclaimed.example",
            "https://%61plicaciones.aragon.es", "https://aplicaciones.aragon.es.", "http://aplicaciones.aragon.es",
        )
        invalid.forEach { mutateFirst("\"origin\":\"$origin\"", "\"origin\":\"$it\"") }
    }

    @Test
    fun redirectRoleChangesCannotOverrideIndependentPins() {
        // The first declaration is Aragon: both directions admit all four role combinations.
        for (roles in listOf("[]", "[\"BOTH\"]", "[\"UNKNOWN\"]", "[\"BOTH\",\"BOTH\"]")) {
            val changed = firstDeclaration.replace(Regex("\"redirectRoles\":\\[[^]]*]"), "\"redirectRoles\":$roles")
            rejects(declarations.replaceFirst(firstDeclaration, changed))
        }
        val transportes = declarations.lines().first { it.contains("\"origin\":\"https://sede.transportes.gob.es\"") }
        rejects(declarations.replaceFirst(transportes, transportes.replace("[\"NEITHER\"]", "[\"NEITHER\",\"BOTH\"]")))
    }

    @Test
    fun evidenceMustBeCompleteAndBoundToTheFirstOwnerReview() {
        val evidence = Regex("\"evidence\":(\\{[^}]*})").find(firstDeclaration)!!.groupValues[1]
        val invalid = listOf(
            "{}", "null", "{\"url\":\"https://unreviewed.example\",\"reviewedOn\":\"2026-10-24\"}",
            evidence.replace(Regex("\\d{4}-\\d{2}-\\d{2}"), "2026-10-25"),
            evidence.replace("\"reviewedOn\":", "\"unknown\":true,\"reviewedOn\":"),
        )
        invalid.forEach { mutateFirst("\"evidence\":$evidence", "\"evidence\":$it") }
    }

    @Test
    fun declarationKeysTypesAndJsonSyntaxRemainStrict() {
        val invalid = listOf(
            firstDeclaration.dropLast(1) + ",\"unknown\":true}",
            firstDeclaration.replaceFirst("\"origin\":", "\"origin\":null,\"origin\":"),
            firstDeclaration.replaceFirst("\"origin\":", "\"unknown\":"),
            firstDeclaration.replace(Regex("\"firstOwner\":\"[^\"]+\""), "\"firstOwner\":7"),
            firstDeclaration.replace(Regex("\"laterOwners\":\\[[^]]*]"), "\"laterOwners\":null"),
            firstDeclaration.replace(Regex("\"redirectRoles\":\\[[^]]*]"), "\"redirectRoles\":true"),
        )
        invalid.forEach { rejects(declarations.replaceFirst(firstDeclaration, it)) }
        for (value in listOf("null", "{}", "true", "[$firstDeclaration,]")) rejects(value)
        assertThrows(RuntimeException::class.java) {
            SiteProfileCatalogParser.parse(withDeclarations(declarations) + " false")
        }
    }

    @Test
    fun collectionSizeCatalogBudgetAndDepthRemainFinite() {
        val excessive = List(129) { firstDeclaration }.joinToString(",", "[", "]")
        rejects(excessive, prefix = "{\"schemaVersion\":1,\"catalogVersion\":1,\"profiles\":[]")
        val peers = (1..33).joinToString(",", "[", "]") { "\"peer-$it\"" }
        val changed = firstDeclaration.replace(Regex("\"laterOwners\":\\[[^]]*]"), "\"laterOwners\":$peers")
        rejects(declarations.replaceFirst(firstDeclaration, changed))
        rejects("[".repeat(33) + "]".repeat(33))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(" ".repeat(SiteProfileCatalogParser.MAX_CATALOG_CHARS + 1))
        }
    }

    @Test
    fun catalogsWithoutSharedClaimsNeedNoDeclarations() {
        val empty = "{\"schemaVersion\":1,\"catalogVersion\":1,\"profiles\":[]"
        assertTrue(SiteProfileCatalogParser.parse("$empty}").reviewedOriginShares.isEmpty())
        assertEquals(SiteProfileCatalogParser.parse("$empty}"), SiteProfileCatalogParser.parse("$empty,$EMPTY_SHARES}"))
    }

    private fun mutateFirst(old: String, new: String) {
        assertTrue("Missing mutation target: $old", old in firstDeclaration)
        rejects(declarations.replaceFirst(firstDeclaration, firstDeclaration.replaceFirst(old, new)))
    }

    private fun withDeclarations(shares: String, prefix: String = catalogPrefix) =
        "$prefix$DECLARATIONS_SEPARATOR$shares}"

    private fun rejects(shares: String, prefix: String = catalogPrefix) {
        assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(withDeclarations(shares, prefix)) }
    }

    private companion object {
        const val DECLARATIONS_SEPARATOR = ",\n  \"reviewedOriginShares\": "
        const val EMPTY_SHARES = "\"reviewedOriginShares\":[]"
    }
}
