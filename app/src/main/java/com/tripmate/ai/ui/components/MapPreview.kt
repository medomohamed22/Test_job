package com.tripmate.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.tripmate.ai.BuildConfig
import com.tripmate.ai.R

data class MapPin(val id: String, val title: String, val lat: Double, val lng: Double, val done: Boolean = false)

/**
 * Real Google Map when a Maps key is configured; otherwise an honest schematic (not a map):
 * relative positions of stops only, clearly labelled.
 */
@Composable
fun MapPreview(pins: List<MapPin>, modifier: Modifier = Modifier, height: Dp = 200.dp, highlightId: String? = null) {
    val shaped = modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.large)
    if (pins.isEmpty()) {
        Box(shaped.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.map_no_coordinates), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (BuildConfig.MAPS_API_KEY.isNotBlank()) GoogleMapView(pins, shaped, highlightId) else SchematicMap(pins, shaped, highlightId)
}

@Composable
private fun GoogleMapView(pins: List<MapPin>, modifier: Modifier, highlightId: String?) {
    val first = pins.first()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(first.lat, first.lng), 12f) }
    GoogleMap(modifier = modifier, cameraPositionState = camera, uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)) {
        MapEffect(pins) { map ->
            if (pins.size > 1) {
                val b = LatLngBounds.builder().apply { pins.forEach { include(LatLng(it.lat, it.lng)) } }.build()
                runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(b, 80)) }
            }
        }
        pins.forEach { pin ->
            val state = remember(pin.id, pin.lat, pin.lng) { MarkerState(position = LatLng(pin.lat, pin.lng)) }
            Marker(state = state, title = pin.title, alpha = if (pin.id == highlightId || highlightId == null) 1f else 0.6f)
        }
        Polyline(points = pins.map { LatLng(it.lat, it.lng) }, color = MaterialTheme.colorScheme.primary, width = 8f)
    }
}

@Composable
private fun SchematicMap(pins: List<MapPin>, modifier: Modifier, highlightId: String?) {
    val primary = MaterialTheme.colorScheme.primary
    val coral = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    // Canvas coordinates are absolute, so geography is never mirrored in RTL (east stays right).
    val description = stringResource(R.string.map_schematic_cd, pins.size)
    Box(modifier.background(bg).semantics { contentDescription = description }) {
        Canvas(Modifier.matchParentSize().padding(22.dp)) {
            val minLat = pins.minOf { it.lat }; val maxLat = pins.maxOf { it.lat }
            val minLng = pins.minOf { it.lng }; val maxLng = pins.maxOf { it.lng }
            val spanLat = (maxLat - minLat).coerceAtLeast(0.005); val spanLng = (maxLng - minLng).coerceAtLeast(0.005)
            fun pos(p: MapPin): Offset {
                val x = ((p.lng - minLng) / spanLng).toFloat() * size.width
                val y = (1f - ((p.lat - minLat) / spanLat).toFloat()) * size.height
                return Offset(x, y)
            }
            for (i in 0..4) {
                drawLine(grid, Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f), 1f)
                drawLine(grid, Offset(size.width * i / 4f, 0f), Offset(size.width * i / 4f, size.height), 1f)
            }
            val pts = pins.map(::pos)
            for (i in 1 until pts.size) {
                drawLine(primary.copy(alpha = 0.7f), pts[i - 1], pts[i], 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            pins.forEachIndexed { i, p ->
                val c = pts[i]
                val color = when { p.id == highlightId -> coral; p.done -> muted; else -> primary }
                drawCircle(color.copy(alpha = 0.25f), 20f, c)
                drawCircle(color, 11f, c)
                drawCircle(bg, 11f, c, style = Stroke(3f))
            }
        }
        Text(
            stringResource(R.string.map_schematic_label),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
