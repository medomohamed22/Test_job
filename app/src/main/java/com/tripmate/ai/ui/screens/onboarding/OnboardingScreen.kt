package com.tripmate.ai.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.WalkingLevel
import com.tripmate.ai.ui.components.DestinationArt
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.ui.theme.LocalReducedMotion
import com.tripmate.ai.util.Formatters
import com.tripmate.ai.util.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: UserPreferencesRepository) : ViewModel() {
    fun setLanguage(tag: String) = viewModelScope.launch { prefs.setLanguage(tag); LocaleHelper.apply(tag) }
    fun finish(interests: Set<Interest>, pace: Pace, walking: WalkingLevel, budget: Double, currency: String, done: () -> Unit) =
        viewModelScope.launch { prefs.completeOnboarding(interests, pace, walking, budget, currency); done() }
}

private data class Slide(val motif: ArtMotif, val title: Int, val body: Int)
private val slides = listOf(
    Slide(ArtMotif.PYRAMIDS, R.string.onb1_title, R.string.onb1_body),
    Slide(ArtMotif.NILE, R.string.onb2_title, R.string.onb2_body),
    Slide(ArtMotif.MOSQUE, R.string.onb3_title, R.string.onb3_body),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) } // 0 = slides, 1 = preferences
    AnimatedContent(step, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "onb") { s ->
        if (s == 0) Slides(onLanguage = vm::setLanguage, onContinue = { step = 1 })
        else Preferences(onBack = { step = 0 }) { i, p, w, b, c -> vm.finish(i, p, w, b, c, onDone) }
    }
}

@Composable
private fun Slides(onLanguage: (String) -> Unit, onContinue: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    // No infinite animation at all when reduced motion is requested (also keeps UI tests idle).
    val float = if (reduced) 0f else rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f",
    ).value
    val lang = LocaleHelper.current()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val slide = slides[page]
            Column(Modifier.fillMaxSize()) {
                DestinationArt(
                    slide.motif,
                    Modifier.fillMaxWidth().weight(1f).graphicsLayer { translationY = float * 18f; scaleX = 1.04f; scaleY = 1.04f },
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp).height(220.dp)) {
                    Text(stringResource(slide.title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(slide.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Language picker floats over the hero.
        Row(Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ar" to R.string.lang_arabic, "en" to R.string.lang_english).forEach { (tag, label) ->
                FilterChip(
                    selected = lang == tag, onClick = { onLanguage(tag) }, label = { Text(stringResource(label)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.35f), labelColor = Color.White,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(slides.size) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
            }
            if (pager.currentPage < slides.lastIndex) {
                TextButton(onClick = onContinue) { Text(stringResource(R.string.action_skip)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text(stringResource(R.string.action_next)) }
            } else Button(onClick = onContinue) { Text(stringResource(R.string.action_get_started)) }
        }
    }
}

@Composable
private fun Preferences(onBack: () -> Unit, onFinish: (Set<Interest>, Pace, WalkingLevel, Double, String) -> Unit) {
    var interests by rememberSaveable { mutableStateOf(setOf(Interest.HISTORY, Interest.FOOD).map { it.name }.toSet()) }
    var pace by rememberSaveable { mutableStateOf(Pace.BALANCED) }
    var walking by rememberSaveable { mutableStateOf(WalkingLevel.MODERATE) }
    var budget by rememberSaveable { mutableStateOf("2500") }
    var currency by rememberSaveable { mutableStateOf("EGP") }
    val budgetValue = budget.toDoubleOrNull()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(stringResource(R.string.prefs_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.prefs_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Label(R.string.prefs_interests)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Interest.entries.forEach { i ->
                val on = i.name in interests
                FilterChip(on, { interests = if (on) interests - i.name else interests + i.name }, { Text(stringResource(i.labelRes())) },
                    leadingIcon = if (on) { { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) } } else null)
            }
        }
        Label(R.string.prefs_pace)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Pace.entries.forEachIndexed { idx, p ->
                SegmentedButton(pace == p, { pace = p }, SegmentedButtonDefaults.itemShape(idx, Pace.entries.size)) { Text(stringResource(p.labelRes())) }
            }
        }
        Label(R.string.prefs_walking)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WalkingLevel.entries.forEachIndexed { idx, w ->
                SegmentedButton(walking == w, { walking = w }, SegmentedButtonDefaults.itemShape(idx, WalkingLevel.entries.size)) { Text(stringResource(w.labelRes())) }
            }
        }
        Label(R.string.prefs_budget)
        OutlinedTextField(
            budget, { budget = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.prefs_daily_budget)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = budgetValue == null, supportingText = { Text(stringResource(R.string.prefs_budget_hint)) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Formatters.currencies.forEach { c -> FilterChip(currency == c, { currency = c }, { Text(c) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.prefs_location_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onFinish(interests.map { Interest.valueOf(it) }.toSet(), pace, walking, budgetValue ?: 0.0, currency) },
                enabled = budgetValue != null,
            ) { Text(stringResource(R.string.action_start_exploring_app)) }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.prefs_guest_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Label(res: Int) {
    Spacer(Modifier.height(20.dp))
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}
