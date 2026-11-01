package com.travel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.platform.rememberExternalUrlLauncher
import com.travel.app.data.DestinationCatalog
import com.travel.app.data.DestinationCountry
import com.travel.app.data.DestinationPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data class Ready(val catalog: DestinationCatalog) : CatalogLoad
    data object Failed : CatalogLoad
}

private data class DestinationResults(
    val key: String = "",
    val countries: List<DestinationCountry> = emptyList(),
    val places: List<DestinationPlace> = emptyList(),
    val selectedPlaceId: String? = null,
)

@Composable
fun DestinationPickerScreen(
    initialCountryCode: String?,
    initialCountryValue: String,
    selectedPlaceId: String?,
    selectedPlaceValue: String,
    startWithCountries: Boolean,
    enabled: Boolean,
    onCountrySelected: (DestinationCountry, Boolean) -> Unit,
    onPlaceSelected: (DestinationPlace) -> Unit,
    onCustomPlace: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    var showSources by remember { mutableStateOf(false) }
    val openUrl = rememberExternalUrlLauncher()
    var showCountries by rememberSaveable { mutableStateOf(startWithCountries) }
    var activeCountryCode by rememberSaveable { mutableStateOf(initialCountryCode) }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var placeQuery by rememberSaveable { mutableStateOf("") }
    val load by produceState<CatalogLoad>(CatalogLoad.Loading, retry) {
        value = CatalogLoad.Loading
        value = try {
            CatalogLoad.Ready(withContext(Dispatchers.Default) { DestinationCatalog.load() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CatalogLoad.Failed
        }
    }
    val catalog = (load as? CatalogLoad.Ready)?.catalog
    val activeCountry = remember(catalog, activeCountryCode, initialCountryValue) {
        catalog?.countries?.firstOrNull { it.code == activeCountryCode }
            ?: catalog?.matchCountry(initialCountryValue)
    }
    val choosingCountries = showCountries || activeCountry == null
    val query = if (choosingCountries) countryQuery else placeQuery
    val searchKey = "${choosingCountries}|${activeCountry?.code.orEmpty()}|$query"
    val results by produceState(DestinationResults(), catalog, searchKey, selectedPlaceId, selectedPlaceValue) {
        if (catalog != null) {
            value = withContext(Dispatchers.Default) {
                if (choosingCountries) DestinationResults(searchKey, countries = catalog.searchCountries(query))
                else {
                    val code = activeCountry!!.code
                    val legacySelection = if (selectedPlaceId == null && selectedPlaceValue.isNotBlank()) {
                        catalog.searchPlaces(code, selectedPlaceValue).singleOrNull {
                            it.name == selectedPlaceValue || it.englishName.equals(selectedPlaceValue, ignoreCase = true)
                        }?.id
                    } else null
                    DestinationResults(searchKey, places = catalog.searchPlaces(code, query), selectedPlaceId = selectedPlaceId ?: legacySelection)
                }
            }
        }
    }
    val filtering = catalog != null && results.key != searchKey

    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = enabled) { Icon(Icons.Outlined.ArrowBack, "여행 작성으로 돌아가기") }
                Text(if (choosingCountries) "국가 선택" else "도시·지역 선택", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (!choosingCountries && activeCountry != null) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(activeCountry.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showCountries = true }, enabled = enabled) { Text("국가 변경") }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { if (choosingCountries) countryQuery = it else placeQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
                enabled = enabled && catalog != null,
                label = { Text(if (choosingCountries) "국가 검색" else "도시·지역 검색") },
                placeholder = { Text("한글 또는 영문으로 검색") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {{
                    IconButton(onClick = { if (choosingCountries) countryQuery = "" else placeQuery = "" }, enabled = enabled) {
                        Icon(Icons.Outlined.Close, "검색어 지우기")
                    }
                }} else null,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            when {
                load is CatalogLoad.Loading -> PickerLoading("여행지를 불러오고 있어요", Modifier.weight(1f))
                load is CatalogLoad.Failed -> Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("여행지를 불러오지 못했어요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("잠시 후 다시 시도해 주세요.", color = TextSecondary, fontSize = 14.sp)
                        ActionButton("다시 시도", onClick = { retry++ }, enabled = enabled)
                    }
                }
                filtering -> PickerLoading("검색하고 있어요", Modifier.weight(1f))
                choosingCountries && results.countries.isEmpty() -> PickerEmpty(query, true, Modifier.weight(1f))
                !choosingCountries && results.places.isEmpty() -> PickerEmpty(query, false, Modifier.weight(1f))
                else -> {
                    // A distinct lazy-list instance resets scroll position when the search or country changes.
                    key(searchKey) {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                            if (choosingCountries) {
                                items(results.countries, key = { it.code }) { country ->
                                    val selected = country.code == activeCountry?.code
                                    DestinationRow(
                                        name = country.name,
                                        detail = country.englishName.takeUnless { it.equals(country.name, ignoreCase = true) }.orEmpty(),
                                        kind = null,
                                        selected = selected,
                                        enabled = enabled,
                                        onClick = {
                                            val changedCountry = country.code != activeCountry?.code
                                            activeCountryCode = country.code
                                            showCountries = false
                                            placeQuery = ""
                                            onCountrySelected(country, changedCountry)
                                        },
                                    )
                                }
                            } else {
                                items(results.places, key = { it.id }) { place ->
                                    val details = listOf(place.englishName, place.region)
                                        .filter { it.isNotBlank() && !it.equals(place.name, ignoreCase = true) }
                                        .distinctBy { it.lowercase() }
                                        .joinToString(" · ")
                                    DestinationRow(
                                        name = place.name,
                                        detail = details,
                                        kind = if (place.kind == "region") "지역" else "도시",
                                        selected = place.id == results.selectedPlaceId,
                                        enabled = enabled,
                                        onClick = { onPlaceSelected(place) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (!choosingCountries && catalog != null && query.isNotBlank()) {
                val customName = query.trim()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("찾는 지역이 없다면 직접 입력할 수 있어요.", fontSize = 12.sp, color = TextSecondary)
                    TextButton(
                        onClick = { onCustomPlace(customName) },
                        enabled = enabled && customName.length in 1..80,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("‘$customName’ 사용", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (customName.length > 80) Text("도시·지역은 80자까지 입력할 수 있어요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = Hairline)
            TextButton(onClick = { showSources = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("GeoNames · Unicode CLDR · 출처", fontSize = 12.sp, color = TextSecondary) }
        }
    }
    if (showSources) AlertDialog(
        onDismissRequest = { showSources = false },
        title = { Text("지역 데이터 출처") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("지명 © GeoNames · CC BY 4.0. 도시·행정지역과 검색 이름을 가공했습니다.", fontSize = 14.sp)
                TextButton(onClick = { openUrl("https://www.geonames.org/") }) { Text("GeoNames") }
                TextButton(onClick = { openUrl("https://creativecommons.org/licenses/by/4.0/") }) { Text("CC BY 4.0 라이선스") }
                Text("국가명 © Unicode CLDR · Unicode-3.0", fontSize = 14.sp)
                TextButton(onClick = { openUrl("https://cldr.unicode.org/") }) { Text("Unicode CLDR") }
                TextButton(onClick = { openUrl("https://www.unicode.org/license.txt") }) { Text("Unicode 라이선스") }
            }
        },
        confirmButton = { TextButton(onClick = { showSources = false }) { Text("닫기") } },
    )
}

@Composable
private fun DestinationRow(name: String, detail: String, kind: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Grouped else Canvas)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            if (detail.isNotBlank()) Text(detail, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        }
        if (kind != null) Text(kind, fontSize = 12.sp, color = TextSecondary)
        Icon(if (selected) Icons.Outlined.Check else Icons.Outlined.ChevronRight, if (selected) "선택됨" else null, tint = if (selected) Accent else TextSecondary, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(Modifier.padding(start = 24.dp), color = Hairline)
}

@Composable
private fun PickerLoading(message: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(message, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PickerEmpty(query: String, countries: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (query.isNotBlank()) "검색 결과가 없어요" else "등록된 ${if (countries) "국가가" else "도시·지역이"} 없어요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(if (!countries) "도시·지역의 다른 이름으로 검색해 보세요." else if (query.isNotBlank()) "다른 한글·영문 이름으로 검색해 보세요." else "잠시 후 다시 시도해 주세요.", color = TextSecondary, fontSize = 14.sp)
        }
    }
}
