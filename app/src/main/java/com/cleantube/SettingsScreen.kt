package com.cleantube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: MainViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Ayarlar", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        SectionTitle("Görünüş")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = vm.themeMode == 0, onClick = { vm.updateTheme(0) }, label = { Text("Sistem") })
            FilterChip(selected = vm.themeMode == 1, onClick = { vm.updateTheme(1) }, label = { Text("Qaranlıq") })
            FilterChip(selected = vm.themeMode == 2, onClick = { vm.updateTheme(2) }, label = { Text("İşıq") })
            FilterChip(selected = vm.themeMode == 3, onClick = { vm.updateTheme(3) }, label = { Text("AMOLED") })
        }
        Text(
            "Vurğu rəngi",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((i, a) in Accents.withIndex()) {
                val selected = vm.accent == i
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(a.dark)
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { vm.updateAccent(i) }
                )
            }
        }

        SectionTitle("Video uzunluğu")
        Text(
            "Seçdiyin dəqiqədən qısa videolar göstərilmir. Məsələn 3 dəq+ Shorts-u gizlədir, 30 dəq+ seçsən yalnız uzun videolar çıxır.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (m in DurationOptions) {
                FilterChip(
                    selected = vm.minMinutes == m,
                    onClick = { vm.updateMinMinutes(m) },
                    label = { Text(durationLabel(m)) }
                )
            }
        }

        SectionTitle("Ana səhifə")
        SwitchRow(
            "Trend videoları",
            "Tətbiqə girəndə ilk ekranda trend videolar göstərilsin",
            vm.showTrending
        ) { vm.updateShowTrending(it) }
        SwitchRow(
            "İzləməyə davam et",
            "Yarımçıq qalan videolar ana səhifədə görünsün",
            vm.showResume
        ) { vm.updateShowResume(it) }
        Text(
            "Trend bölgəsi",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (r in listOf("AZ", "TR", "US", "RU", "GB", "DE")) {
                FilterChip(selected = vm.region == r, onClick = { vm.updateRegion(r) }, label = { Text(r) })
            }
        }

        SectionTitle("İzləmə vaxtı")
        val stats = vm.weekStats()
        val maxSec = (stats.maxOfOrNull { it.second } ?: 0).coerceAtLeast(60)
        Text(
            "Bu gün: " + (vm.watchToday / 60) + " dəq",
            fontWeight = FontWeight.Medium
        )
        Row(
            Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            for ((label, secs) in stats) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text((secs / 60).toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .width(22.dp)
                            .height((60f * secs / maxSec).dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "Gündəlik limit (çatanda videoda xəbərdarlıq çıxır)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (min in listOf(0, 30, 60, 90, 120, 180)) {
                FilterChip(
                    selected = vm.dailyLimitMin == min,
                    onClick = { vm.updateDailyLimit(min) },
                    label = { Text(if (min == 0) "Söndürülü" else "$min dəq") }
                )
            }
        }

        SectionTitle("Tarixçə")
        SwitchRow(
            "Axtarış tarixçəsi",
            "Söndürəndə yeni axtarışlar saxlanmır və siyahı gizlənir",
            vm.searchHistoryOn
        ) { vm.updateSearchHistoryOn(it) }
        SwitchRow(
            "İzləmə tarixçəsi",
            "Söndürəndə izlədiklərin və qaldığın yer saxlanmır",
            vm.watchHistoryOn
        ) { vm.updateWatchHistoryOn(it) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.clearSearchHistory() }, modifier = Modifier.fillMaxWidth()) {
            Text("Axtarış tarixçəsini sil")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
            Text("İzləmə tarixçəsini sil")
        }

        SectionTitle("YouTube API açarları")
        Text(
            "İki açar əlavə edə bilərsən. Birincinin günlük limiti bitəndə tətbiq avtomatik ikinci açara keçir. Açar yoxdursa YouTube bölməsi, link və Paylaş ilə video açıla bilər.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KeySlot(vm, 1)
        KeySlot(vm, 2)
        Text(
            "\"Sil\" yalnız tətbiqdə daxil etdiyin açarı silir; build zamanı verilən açar qalır.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        SectionTitle("YouTube sayt məlumatları")
        OutlinedButton(onClick = { vm.clearWebData() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cookie və sayt yaddaşını sil")
        }

        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        Text(
            "CleanTube: tövsiyəsiz, commentsiz və Shorts-suz YouTube. Videolar YouTube-un rəsmi pleyeri ilə oynadılır, ona görə YouTube-un öz reklamları söndürülə bilmir. Hər axtarış gündəlik API limitindən təxminən 100 vahid işlədir, trend və kanal videoları isə çox ucuzdur.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun KeySlot(vm: MainViewModel, slot: Int) {
    var text by remember { mutableStateOf("") }
    val has = if (slot == 1) vm.key1Set else vm.key2Set
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            slot.toString() + "-ci açar: " + (if (has) "əlavə olunub" else "yoxdur"),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("AIza...") }
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.saveKey(slot, text)
                    text = ""
                },
                enabled = text.isNotBlank()
            ) { Text("Yadda saxla") }
            if (has) {
                OutlinedButton(onClick = { vm.clearKey(slot) }) { Text("Sil") }
            }
        }
    }
}
