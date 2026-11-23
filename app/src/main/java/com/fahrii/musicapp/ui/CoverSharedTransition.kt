package com.fahrii.musicapp.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * Shared element transition untuk cover album: cover kecil di mini player "berpindah" mulus
 * menjadi cover melingkar di halaman Sedang Diputar (dan sebaliknya saat ditutup).
 *
 * Cara pakai:
 *  - AppRoot membungkus seluruh isi dengan [CoverTransitionHost].
 *  - Tiap AnimatedVisibility (bottom bar & Now Playing) memanggil [ProvideCoverVisibilityScope].
 *  - Cover di MiniPlayer & NowPlayingScreen memakai [coverSharedElement] dan [CoverMorphShape].
 */

/** Durasi terbang cover (ms). AnimatedVisibility di kedua sisi harus minimal selama ini. */
const val COVER_FLIGHT_MS = 380

/** Durasi terbang cover saat menutup (ms): lebih singkat dari membuka supaya tidak terasa melayang. */
const val COVER_CLOSE_MS = 300

/** Ukuran cover mini player (dp). Dipakai [CoverMorphShape] sebagai titik awal morph. */
private const val MINI_COVER_DP = 44f

/** Sudut membulat cover mini player (dp). */
private const val MINI_COVER_RADIUS_DP = 8f

/** Selisih lebar (dp) di atas ukuran mini player saat sudut sudah menjadi lingkaran penuh. */
private const val MORPH_RANGE_DP = 140f

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

private val LocalCoverVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Pembungkus root. Menyediakan SharedTransitionScope ke seluruh isi di bawahnya. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CoverTransitionHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}

/**
 * Menyediakan AnimatedVisibilityScope tempat cover berada. Kirim `null` untuk mematikan
 * transisi (mis. saat pemutar ditutup dengan geser ke bawah, yang sudah punya animasi sendiri).
 */
@Composable
fun ProvideCoverVisibilityScope(
    scope: AnimatedVisibilityScope?,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalCoverVisibilityScope provides scope) {
        content()
    }
}

/**
 * Menggambar elemen di overlay transisi dengan urutan di ATAS cover yang sedang terbang
 * (cover memakai zIndexInOverlay default 0). Tanpa ini, tombol di tepi cover tergambar di bawah
 * cover selama terbang, lalu loncat ke atasnya begitu cover turun dari overlay.
 * Letakkan SETELAH `.offset(...)` dan SEBELUM [coverEdgePop].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.coverEdgeOverlay(): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    return with(sharedScope) {
        this@coverEdgeOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
    }
}

/** Easing "pop": sedikit melewati target lalu mengendap. */
private val PopEasing = Easing { fraction ->
    val c1 = 1.0f
    val c3 = c1 + 1f
    val t = fraction - 1f
    1f + c3 * t * t * t + c1 * t * t
}

/**
 * Progres 0..1 untuk elemen yang menempel di tepi cover besar (tombol opsi, playlist, antrean).
 * Posisinya dihitung dari ukuran cover besar, jadi baru muncul di akhir penerbangan cover (saat
 * cover sudah hampir sebesar tujuannya), satu per satu ([index] = urutan), dan menghilang cepat
 * begitu penutupan dimulai. Tanpa transisi cover (scope null) selalu 1.
 */
@Composable
fun rememberCoverEdgeProgress(index: Int): State<Float> {
    val scope = LocalCoverVisibilityScope.current
        ?: return remember { mutableStateOf(1f) }
    return scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(
                    durationMillis = 200,
                    delayMillis = COVER_FLIGHT_MS - 150 + index * 40,
                    easing = PopEasing
                )
            } else {
                tween(durationMillis = 70, easing = FastOutSlowInEasing)
            }
        },
        label = "coverEdgeProgress"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
}

/**
 * Efek tombol di tepi cover: membesar sedikit sambil memudar di tempat (tanpa bergeser).
 * [fade] (0..1) mengalikan alpha, dipakai untuk memudarkan tombol saat layar digeser ke bawah.
 */
fun Modifier.coverEdgePop(progress: State<Float>, fade: () -> Float = { 1f }): Modifier = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f) * fade()
    val scale = 0.6f + 0.4f * p
    scaleX = scale
    scaleY = scale
}

/** Tidak memotong cover saat terbang; bentuknya diatur oleh [CoverMorphShape]. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val NoOverlayClip = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density
    ): Path? = null
}

/**
 * Tandai cover sebagai shared element. [songUrl] jadi kunci: cover lagu yang sama di mini
 * player dan Now Playing dianggap pasangan. Tanpa host/scope, modifier ini tidak berbuat apa-apa.
 *
 * Letakkan SETELAH modifier ukuran dan SEBELUM `.clip(CoverMorphShape)`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.coverSharedElement(songUrl: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalCoverVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@coverSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = "cover-$songUrl"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = BoundsTransform { initialBounds, targetBounds ->
                // Target lebih kecil dari asal = sedang menutup (kembali ke mini player)
                val closing = targetBounds.width < initialBounds.width
                tween(
                    durationMillis = if (closing) COVER_CLOSE_MS else COVER_FLIGHT_MS,
                    easing = FastOutSlowInEasing
                )
            },
            clipInOverlayDuringTransition = NoOverlayClip
        )
    }
}

/**
 * Bentuk cover yang menyesuaikan lebarnya: 44 dp = kotak sudut 8 dp (mini player), makin lebar
 * makin bulat, sampai lingkaran penuh. Karena hanya bergantung pada lebar, bentuk ikut berubah
 * mulus selama cover terbang, dan di kedua layar hasil akhirnya tetap benar tanpa perlu tahu
 * ukuran cover besar.
 */
object CoverMorphShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = minOf(size.width, size.height)
        val miniPx = with(density) { MINI_COVER_DP.dp.toPx() }
        val rangePx = with(density) { MORPH_RANGE_DP.dp.toPx() }
        val miniRadiusPx = with(density) { MINI_COVER_RADIUS_DP.dp.toPx() }
        val fraction = ((width - miniPx) / rangePx).coerceIn(0f, 1f)
        val radius = lerp(miniRadiusPx, width / 2f, fraction)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
}

/**
 * Mengecilkan cover sebesar [scale] (0..1) di tengah areanya, lewat LAYOUT (bukan graphicsLayer)
 * supaya ukuran shared element ikut mengecil. Dengan begitu saat layar dilepas dan cover terbang
 * ke mini player, terbangnya mulai dari ukuran yang sedang terlihat, bukan dari ukuran penuh.
 * Letakkan SETELAH `.fillMaxSize()` dan SEBELUM [coverSharedElement].
 */
fun Modifier.coverDragShrink(scale: () -> Float): Modifier = layout { measurable, constraints ->
    val s = scale().coerceIn(0.1f, 1f)
    val width = (constraints.maxWidth * s).roundToInt()
    val height = (constraints.maxHeight * s).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            (constraints.maxWidth - width) / 2,
            (constraints.maxHeight - height) / 2
        )
    }
}