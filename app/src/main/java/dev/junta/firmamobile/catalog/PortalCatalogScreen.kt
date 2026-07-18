package dev.junta.firmamobile.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaTealDark

@Composable
fun PortalCatalogScreen(
    repository: PortalCatalogRepository,
    favoriteProfileIds: Set<ProfileId> = emptySet(),
    recentProfileIds: List<ProfileId> = emptyList(),
    onToggleFavorite: (ProfileId) -> Unit = {},
    onOpenPortal: (PortalCatalogItem) -> Unit,
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(PortalCatalogFilter.ALL) }
    val sections = remember(
        repository,
        searchText,
        selectedFilter,
        favoriteProfileIds,
        recentProfileIds,
    ) {
        buildPortalCatalogSections(
            repository.portals(
                PortalCatalogQuery(
                    searchText = searchText,
                    filter = selectedFilter,
                    favoriteProfileIds = favoriteProfileIds,
                    recentProfileIds = recentProfileIds,
                ),
            ),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JuntaPaper)
            .safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            top = 16.dp,
            end = 18.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "catalog-header") {
            CatalogHeader(repository.bundledCatalogVersion)
        }
        item(key = "catalog-search") {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Buscar servicio u organismo") },
                singleLine = true,
                shape = CatalogShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "catalog-filters") {
            PortalFilterGrid(
                selected = selectedFilter,
                onSelected = { selectedFilter = it },
            )
        }

        if (sections.isEmpty()) {
            item(key = "catalog-empty") {
                CatalogNotice(
                    title = "Sin resultados",
                    text = "Pruebe otra búsqueda o seleccione Todos.",
                )
            }
        } else {
            sections.forEach { section ->
                item(key = "section-${section.kind.name}") {
                    CatalogSectionHeader(section)
                }
                items(
                    items = section.items,
                    key = { "portal-${it.profileId.value}" },
                ) { portal ->
                    PortalCard(
                        portal = portal,
                        isFavorite = portal.profileId in favoriteProfileIds,
                        onToggleFavorite = onToggleFavorite,
                        onOpenPortal = onOpenPortal,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(catalogVersion: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "SERVICIOS PÚBLICOS",
            color = JuntaTealDark,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Elija una sede compatible. La confianza técnica siempre procede del perfil activo.",
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "CATÁLOGO LOCAL · V$catalogVersion",
            color = JuntaTeal,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PortalFilterGrid(
    selected: PortalCatalogFilter,
    onSelected: (PortalCatalogFilter) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PortalCatalogFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = CutCornerShape(4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = JuntaPaperElevated,
                    labelColor = JuntaInk,
                    selectedContainerColor = JuntaTeal,
                    selectedLabelColor = JuntaPaperElevated,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == filter,
                    borderColor = JuntaInk,
                    selectedBorderColor = JuntaInk,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp,
                ),
            )
        }
    }
}

@Composable
private fun CatalogSectionHeader(section: PortalCatalogSection) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = JuntaTeal, thickness = 2.dp)
        Text(
            text = section.title,
            color = JuntaTealDark,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = section.description,
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PortalCard(
    portal: PortalCatalogItem,
    isFavorite: Boolean,
    onToggleFavorite: (ProfileId) -> Unit,
    onOpenPortal: (PortalCatalogItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JuntaPaperElevated, CatalogShape)
            .border(2.dp, JuntaInk, CatalogShape)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = portal.displayName,
                color = JuntaInk,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = portal.supportStatus.shortLabel,
                color = portal.supportStatus.statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            text = "${portal.organization} · ${portal.territory}",
            color = JuntaTealDark,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = portal.purpose,
            color = JuntaInk,
            style = MaterialTheme.typography.bodyMedium,
        )

        val services = buildList {
            if (PortalServiceCapability.CERTIFICATE_ACCESS in portal.capabilities) {
                add("Acceso con certificado")
            }
            if (PortalServiceCapability.ELECTRONIC_SIGNATURE in portal.capabilities) {
                add("Firma electrónica")
            }
        }
        if (services.isNotEmpty()) {
            Text(
                text = services.joinToString(" · "),
                color = JuntaTeal,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (portal.signatureFormats.isNotEmpty()) {
            Text(
                text = "Formatos: ${portal.signatureFormats.joinToString { it.name }}",
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = portal.entryUrl.toASCIIString(),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (portal.supportStatus == PortalSupportStatus.BROWSE_ONLY) {
            CatalogInlineWarning("Puede navegar, pero el certificado y la firma están bloqueados.")
        }

        OutlinedButton(
            onClick = { onToggleFavorite(portal.profileId) },
            modifier = Modifier.fillMaxWidth(),
            shape = CatalogShape,
        ) {
            Text(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos")
        }

        Button(
            onClick = { onOpenPortal(portal) },
            enabled = portal.isEnabled,
            modifier = Modifier.fillMaxWidth(),
            shape = CatalogShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = JuntaTeal,
                contentColor = JuntaPaperElevated,
                disabledContainerColor = JuntaHairline,
                disabledContentColor = JuntaMutedInk,
            ),
        ) {
            Text(if (portal.isEnabled) "Abrir sede" else "No disponible")
        }
    }
}

@Composable
private fun CatalogInlineWarning(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, CatalogShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun CatalogNotice(title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JuntaPaperElevated, CatalogShape)
            .border(1.dp, JuntaHairline, CatalogShape)
            .padding(14.dp),
    ) {
        Text(title, color = JuntaTealDark, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(3.dp))
        Text(text, color = JuntaMutedInk, style = MaterialTheme.typography.bodyMedium)
    }
}

internal enum class PortalCatalogSectionKind {
    COMPATIBLE,
    CONTRACT_PENDING,
    FULL_CATALOG,
}

internal data class PortalCatalogSection(
    val kind: PortalCatalogSectionKind,
    val title: String,
    val description: String,
    val items: List<PortalCatalogItem>,
)

internal fun buildPortalCatalogSections(items: List<PortalCatalogItem>): List<PortalCatalogSection> {
    val compatible = items.filter {
        it.supportStatus == PortalSupportStatus.VERIFIED_E2E ||
            it.supportStatus == PortalSupportStatus.IMPLEMENTED_NOT_E2E
    }
    val contractPending = items.filter {
        it.supportStatus == PortalSupportStatus.VERIFIED_CONTRACT
    }
    val fullCatalog = items.filter {
        it.supportStatus == PortalSupportStatus.BROWSE_ONLY ||
            it.supportStatus == PortalSupportStatus.UNSUPPORTED_PROTOCOL
    }

    return buildList {
        if (compatible.isNotEmpty()) {
            add(
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.COMPATIBLE,
                    title = "Compatibles",
                    description = "Servicios implementados; consulte el estado de verificación de cada perfil.",
                    items = compatible,
                ),
            )
        }
        if (contractPending.isNotEmpty()) {
            add(
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.CONTRACT_PENDING,
                    title = "Compatibilidad confirmada; implementación pendiente",
                    description = "El contrato oficial está verificado; consulte el estado de cada perfil.",
                    items = contractPending,
                ),
            )
        }
        if (fullCatalog.isNotEmpty()) {
            add(
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.FULL_CATALOG,
                    title = "Catálogo completo",
                    description = "Los perfiles limitados mantienen bloqueadas las funciones no verificadas.",
                    items = fullCatalog,
                ),
            )
        }
    }
}

private val PortalCatalogFilter.label: String
    get() = when (this) {
        PortalCatalogFilter.ALL -> "Todos"
        PortalCatalogFilter.STATE -> "Estado"
        PortalCatalogFilter.AUTONOMOUS_COMMUNITIES -> "Comunidades Autónomas"
        PortalCatalogFilter.LOCAL_ADMINISTRATION -> "Administración local"
        PortalCatalogFilter.UNIVERSITIES -> "Universidades"
        PortalCatalogFilter.FAVORITES -> "Favoritos"
        PortalCatalogFilter.RECENT -> "Recientes"
        PortalCatalogFilter.CERTIFICATE_ACCESS -> "Acceso con certificado"
        PortalCatalogFilter.ELECTRONIC_SIGNATURE -> "Firma electrónica"
    }

private val PortalSupportStatus.shortLabel: String
    get() = when (this) {
        PortalSupportStatus.VERIFIED_E2E -> "E2E VERIFICADO"
        PortalSupportStatus.IMPLEMENTED_NOT_E2E -> "IMPLEMENTADO · E2E PENDIENTE"
        PortalSupportStatus.VERIFIED_CONTRACT -> "CONTRATO VERIFICADO"
        PortalSupportStatus.BROWSE_ONLY -> "SOLO NAVEGACIÓN"
        PortalSupportStatus.UNSUPPORTED_PROTOCOL -> "PROTOCOLO NO COMPATIBLE"
    }

private val PortalSupportStatus.statusColor: Color
    @Composable get() = when (this) {
        PortalSupportStatus.VERIFIED_E2E -> JuntaTealDark
        PortalSupportStatus.IMPLEMENTED_NOT_E2E -> Color(0xFF7A4D00)
        PortalSupportStatus.VERIFIED_CONTRACT -> JuntaTeal
        PortalSupportStatus.BROWSE_ONLY, PortalSupportStatus.UNSUPPORTED_PROTOCOL ->
            MaterialTheme.colorScheme.error
    }

private val CatalogShape = CutCornerShape(
    topStart = 7.dp,
    topEnd = 5.dp,
    bottomEnd = 7.dp,
    bottomStart = 5.dp,
)
