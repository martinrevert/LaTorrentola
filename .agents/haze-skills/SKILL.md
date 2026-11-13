---
name: haze-21-tv-material3
description: Standards, architecture, and performance guidelines for integrating Haze 2.1 (dev.chrisbanes.haze) frosted glass and blur effects with Material 3 in Jetpack Compose for Handheld and Compose Material 3 for Android TV (androidx.tv.material3). Covers D-Pad focus reactivity, hazeSource background capture, hazeGlass with translucent surfaces, pre-Android 12 fallbacks, GPU performance management on TV, and avoiding opacity/dimming traps.
metadata:
  version: 2.1.0
  author: LaTorrentola Team
  last-updated: '2026-11-09'
  keywords:
  - Haze
  - Glassmorphism
  - Material 3
  - Android TV
  - D-Pad
  - Compose
  - Blur
  - hazeSource
  - hazeGlass
  - hazeGlassContainer
---

# SKILL.md: Integración de Haze 2.1 con Material 3 en Handheld y Android TV con Navegación D-Pad

Esta guía normativa define los estándares de arquitectura, implementación y rendimiento para integrar la librería **Haze 2.1** en aplicaciones desarrolladas con Jetpack Compose y Compose Multiplatform. Cubre tanto interfaces táctiles para dispositivos móviles como interfaces orientadas a control remoto (D-Pad) en **Android TV**, exigiendo el uso obligatorio del sistema de diseño **Material 3** (`androidx.compose.material3` para móviles y `androidx.tv.material3` para televisores).

---

## 1. Manifiesto de Dependencias y Versiones

Todos los artefactos de Haze deben mantenerse estrictamente en la versión **2.1.0** para asegurar compatibilidad binaria y el soporte estable del efecto **Glass** sin requerir anotaciones experimentales (`@ExperimentalHazeApi`).

```kotlin
// build.gradle.kts (Módulo de la Aplicación)
dependencies {
    // 1. Infraestructura base y Blur para Compose Multiplatform
    implementation("dev.chrisbanes.haze:haze:2.1.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")

    // 2. Módulo estable de Glass y Fusión de Superficies
    implementation("dev.chrisbanes.haze:haze-glass:2.1.0")

    // 3. Integración mandatoria con Material 3 en Handheld (Mobile/Desktop)
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("dev.chrisbanes.haze:haze-blur-material3:2.1.0")
    implementation("dev.chrisbanes.haze:haze-glass-material3:2.1.0")

    // 4. Integración mandatoria con Compose Material 3 para Android TV
    implementation("androidx.tv:tv-material:1.1.0")
}
```

---

## 2. Arquitectura de Captura y Jerarquía de Modificadores

La arquitectura de Haze 2.1 se fundamenta en la separación estricta entre la fuente de captura de píxeles (`hazeSource`), la especificación de entrada (`HazeInput`) y el estilo visual (`GlassStyle` o `HazeBlurStyle`).

### 2.1 Registro del Fondo (`hazeSource`)

Para que los componentes de la interfaz puedan aplicar desenfoque o refracción sobre el contenido subyacente, el contenedor o imagen de fondo debe registrarse en el estado compartido mediante `Modifier.hazeSource`:

```kotlin
val hazeState = rememberHazeState()

Box(modifier = Modifier.fillMaxSize()) {
    // Fondo registrado como fuente de renderizado
    AsyncImage(
        model = backgroundUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
    )

    // Capa de interfaz superpuesta
    TVContentOverlay(hazeState = hazeState)
}
```

---

## 3. Integración Mandatoria de Material 3 (Handheld vs. TV)

La librería Haze requiere adaptar sus estilos a las paletas de color y superficies del sistema de diseño Material 3. La implementación varía según el factor de forma del dispositivo, utilizando la nomenclatura canónica de Google:

| Característica | Handheld / Desktop | Android TV (`tv-material`) |
| :--- | :--- | :--- |
| **Paquete de UI** | `androidx.compose.material3` | `androidx.tv.material3` |
| **Integración Blur** | `HazeBlurStyle.Material3()` | `HazeBlurStyle.Material3()` |
| **Integración Glass** | `GlassStyle.material3()` | `GlassStyle.material3()` o `GlassStyle.regular.then { tint(...) }` |
| **Modelo de Entrada** | Toque / Gesto (`press`, `hover`) | Foco de D-Pad (`focus`, `FocusRequester`) |
| **Componentes Canónicos** | `Surface`, `Button`, `Card` | `TvSurface`, `TvButton`, `TvCard` |

---

## 4. Paradigma D-Pad Focus y Estados de Cristal en Android TV

En Android TV, el usuario interactúa mediante las flechas de dirección del control remoto (D-Pad). Por ello, los componentes interactivos no responden a eventos táctiles, sino a cambios en el **estado de foco** (`FocusState`).

### 4.1 Modificadores de Foco y Reactividad de Estilos en Haze 2.1

Haze 2.1 permite que los estilos (`GlassStyle` y `HazeBlurStyle`) lean estados animados de Jetpack Compose internamente. Cuando el foco del D-Pad cambia, el estilo actualiza sus propiedades en el siguiente fotograma sin necesidad de recomponer el punto de llamada del modificador.

El siguiente patrón demuestra cómo construir un componente interactivo de TV enfocado con el D-Pad:

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHazeGlassCard(
    hazeState: HazeState,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // Animación de parámetros impulsada por el foco del D-Pad
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isFocused) 20.dp else 12.dp,
        label = "CornerRadiusAnimation"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
        label = "TintAnimation"
    )

    // El estilo observa los estados animados sin recomponer la llamada a hazeGlass
    val glassStyle = remember(animatedCornerRadius, animatedTint) {
        GlassStyle.regular.then {
            shape(RoundedCornerShape(animatedCornerRadius))
            tint(animatedTint)
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = glassStyle
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f)
        )
    }
}
```

---

## 5. Fusión de Componentes de TV con `hazeGlassContainer`

En interfaces de TV (como carruseles de películas o barras laterales de navegación), los elementos contiguos que reciben foco del D-Pad pueden unirse visualmente mediante el modificador `hazeGlassContainer`.

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlassContainer

@Composable
fun TvHazeCarousel(
    hazeState: HazeState,
    itemsList: List<String>,
    onItemSelected: (String) -> Unit
) {
    // Contenedor que fusiona tarjetas cuando se encuentran a menos de 16.dp
    TvLazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(16.dp)
            .hazeGlassContainer(mergeDistance = 16.dp)
    ) {
        items(itemsList) { item ->
            TvHazeGlassCard(
                hazeState = hazeState,
                title = item,
                onClick = { onItemSelected(item) }
            )
        }
    }
}
```

---

## 6. Estrategia de Rendimiento y Restricciones de Hardware para Android TV

Los System-on-Chip (SoC) de los televisores y dispositivos Android TV poseen unidades de procesamiento gráfico (GPU) significativamente más reducidas que las de un teléfono inteligente flagship. Por ello, se deben aplicar las siguientes directrices de optimización:

### 6.1 Configuración de Performance con `LocalHazePerformanceMode`

Debes envolver la jerarquía de la pantalla de TV en un proveedor de rendimiento con el modo `HazePerformanceMode.Performance` o mediante una fracción fija de calidad (`Fixed`):

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode

@Composable
fun AndroidTvHazeScreen(content: @Composable () -> Unit) {
    // Fuerza un perfil de bajo consumo de GPU óptimo para SoC de televisores
    CompositionLocalProvider(
        LocalHazePerformanceMode provides HazePerformanceMode.Performance
    ) {
        content()
    }
}
```

### 6.2 Restricción de Reproducción de Video (`SurfaceView`)

En aplicaciones de streaming para TV (que utilicen ExoPlayer o Media3), el video suele renderizarse en un `SurfaceView` por motivos de aceleración de hardware por DRM. 

> **Regla de Arquitectura**: Los píxeles directos de un `SurfaceView` no pueden ser leídos por `hazeSource`. Para permitir que Haze desenfoque o refracte sobre el video en reproducción, debes configurar el reproductor con `PreviewView.ImplementationMode.COMPATIBLE` o asegurar que la capa de video se dibuje en la misma capa gráfica de Compose.

---

## 7. Resumen de Reglas de Calidad para el Skill

1. **Material 3 Obligatorio**: Nunca utilices valores estáticos de color sin vincularlos a `HazeBlurStyle.Material3()` o `GlassStyle.material3()`.
2. **Control por Foco**: Toda superficie interactiva en Android TV debe actualizar su `GlassStyle` mediante `onFocusChanged` y no mediante eventos de presión o toque.
3. **Control de Memoria GPU**: Aplica siempre `HazePerformanceMode.Performance` en vistas de TV para mantener el presupuesto de fotogramas a 60 Hz (16.6 ms por fotograma).
4. **Fusión Responsiva**: Utiliza `hazeGlassContainer` únicamente en filas o grillas con número limitado de elementos enfocables para evitar sobrecargar los cálculos de textura de la GPU.

---

## 8. Arquitectura Coherente de Cristal en Dos Niveles (`HazeModifiers.kt`: Tier 1 vs. Tier 2)

Para garantizar consistencia visual, transiciones fluidas y máxima legibilidad (cumplimiento estricto de WCAG AAA) en todas las pantallas Handheld (y aislamiento total en Android TV), toda integración de Haze debe pasar por los helpers centralizados de `ui/theme/HazeModifiers.kt`:

### 8.1 Tier 1: Barras de Navegación Edge-to-Edge y Controles Flotantes del Reproductor
- **Barras de aplicación (`hazeAppBarGlass` + `hazeAppBarContainerColor`)**: Usado en `TopAppBar`, cabeceras y barras de navegación en todas las pantallas Handheld (incluyendo estados Loading, Error y Success). Configura explícitamente `GlassStyle { shape(RoundedCornerShape(0.dp)) }.material3()` para extender el cristal de borde a borde sin heredar el radio por defecto de `GlassDefaults.shape` (16.dp), con `Color.Transparent` en API 31+ / Preview y respaldo translúcido (`surface.copy(alpha = fallbackAlpha)`) en API < 31. `Modifier.hazeSource` debe colocarse estrictamente en el contenedor hermano desplazable/fondo bajo la barra, nunca envolviendo al `TopAppBar`.
- **Controles flotantes de ExoPlayer (`hazePillGlass`)**: Usado en `LocalPlayerActivity` (Top Bar pill, Center HUD capsule, Bottom Bar card, Quick Seek pill, Double Tap pills) con `surfaceContainerHigh.copy(alpha = 0.48f)` cuando el cristal está activo. `hazePillGlass` acepta `Shape` y sincroniza `.clip(shape)` con `GlassStyle { shape(...) }.material3()`.
- **Pipeline de Media3**: Para muestrear video en tiempo real sin romper el árbol de renderizado de Compose, `PlayerSurface` debe instanciarse con `surfaceType = SURFACE_TYPE_TEXTURE_VIEW` en Handheld con `Modifier.hazeSource(playerHazeState)`. En Android TV se preserva `SURFACE_TYPE_SURFACE_VIEW` y se desactiva cualquier desenfoque (`rememberHandheldHazeState(isTv)` devuelve `null` y `hazeSheetBackdropBlur` queda `enabled = false`).

### 8.2 Tier 2: Modal Bottom Sheets Opacos con Desenfoque Animado de Fondo (`hazeSheetBackdropBlur`)
- **Componentes**: `ActorDetailBottomSheet`, `GenreBottomSheet`, `CastBottomSheet`, `PlayerMobileBottomSheet` y el panel de opciones de reproducción.
- **Anti-Patrón Prohibido**: Usar `Color.Transparent` o transparencia parcial (`0.88f`) sobre `hazeSource` parcial en bottom sheets provoca colisiones visuales con cortes del fondo (p. ej. backdrops de 260dp en `MovieDetailScreen`/`TvDetailScreen`) y transiciones abruptas mitad-desenfocadas al abrir o cerrar el panel.
- **Contrato Normativo**: Todo `ModalBottomSheet` debe usar un contenedor opaco `containerColor = MaterialTheme.colorScheme.surfaceContainerHigh` (sin `hazeGlass` ni parámetro `hazeState`), mientras que la raíz de la pantalla padre aplica `Modifier.hazeSheetBackdropBlur(isSheetVisible = ..., enabled = !isTv)` (`Animatable` simétrico de `0.dp` ↔ `18.dp` en 250ms aplicado en `graphicsLayer` mediante `RenderEffect.createBlurEffect` en Android 12+ para evitar recomposiciones de raíz por fotograma). Esto desenfoca toda la escena subyacente de manera uniforme y garantiza contraste perfecto del texto en todo momento.
