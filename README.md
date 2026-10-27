# CorTV 📺

Reproductor multimedia para **Android TV** y **teléfonos Android**: canales de TV en vivo de todo
el mundo, películas, series, animes y una sección infantil, con una interfaz pensada para el
control remoto (D-pad). Los canales en vivo vienen de la base de datos abierta de
[iptv-org](https://github.com/iptv-org/iptv) y solo se muestran los que fueron verificados.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Compose for TV](https://img.shields.io/badge/Compose%20for%20TV-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer-FF0000)
![Android TV](https://img.shields.io/badge/Android%20TV-minSdk%2023-3DDC84?logo=android&logoColor=white)

---

## Tabla de contenido

- [Funcionalidades](#funcionalidades)
- [Módulos](#módulos)
- [Stack tecnológico](#stack-tecnológico)
- [Cómo funciona el catálogo](#cómo-funciona-el-catálogo)
- [Compilar y ejecutar](#compilar-y-ejecutar)
- [Verificar canales](#verificar-canales)
- [Aviso legal](#aviso-legal)

## Funcionalidades

- 📡 **TV en vivo** a pantalla completa con *zapping* (arriba/abajo cambia de canal) entre los canales
  del país elegido; bandera del país y calidad del stream en cada tarjeta.
- 🎬 **Películas, Series, Animes y Kids** con portadas obtenidas de TVMaze / Wikipedia (cacheadas en disco).
- ▶️ **Seguir viendo**: guarda el progreso de cada título y el último canal visto; se puede quitar una
  tarjeta con pulsación larga.
- ⭐ **Favoritos**, 🕘 **Historial** y 🔎 **Buscador**.
- 📺 Series con selección de temporada y episodio.
- 🌎 **Ajustes**: país de los canales (con cantidad de canales por país), idioma de la app y estilo
  de subtítulos con vista previa.
- 🎮 Navegación completa con control remoto: menú lateral, foco inicial y recuperación de foco al volver
  del reproductor.
- 🙈 Los canales que fallan al reproducir se ocultan automáticamente.

## Módulos

| Módulo | Descripción |
|--------|-------------|
| `:cortv_tv` | App para **Android TV** (Compose for TV, menú lateral, reproductor en vivo y VOD). |
| `:app` | App para **teléfonos** con Compose Material 3 y el mismo catálogo. |
| `:common` | Lógica compartida: modelos (`MediaItem`, `Episode`), parser M3U, cliente de la API de iptv-org, portadas y `PreferencesManager` (favoritos, historial, progreso). |
| `tools/` | Script en Python que verifica qué streams cargan y tienen audio. |

## Stack tecnológico

| Área | Tecnología |
|------|------------|
| Lenguaje | Kotlin 2.4 |
| UI | Jetpack Compose, Compose for TV (`tv-material`), Material 3, Material Icons |
| Reproducción | AndroidX Media3 (ExoPlayer, HLS, DASH, MediaSession) |
| Serialización | kotlinx.serialization (JSON) |
| Imágenes | Coil |
| Datos | API de iptv-org, TVMaze, Wikipedia REST |
| Build | Gradle (Kotlin DSL) + Version Catalog, AGP 9 |

## Cómo funciona el catálogo

1. `IptvOrgApi` descarga *streams*, canales, logos, categorías y la lista de bloqueados de
   `iptv-org.github.io/api`, los une por canal y descarta canales NSFW, cerrados o bloqueados.
2. El resultado se cachea en disco durante 12 horas.
3. Solo se ofrecen los streams listados en `common/src/main/assets/verified_streams.txt`.
4. `PosterApi` completa las portadas de películas y series y también se cachea.

## Compilar y ejecutar

Requisitos: Android Studio reciente (compatible con AGP 9), JDK 17+, Android SDK 37.

```bash
# Android TV
./gradlew :cortv_tv:assembleDebug
adb install -r cortv_tv/build/outputs/apk/debug/cortv_tv-debug.apk

# Teléfono
./gradlew :app:assembleDebug
```

## Verificar canales

La lista de streams verificados depende de la red/país del equipo, así que conviene regenerarla de
vez en cuando:

```bash
python -m pip install imageio-ffmpeg
python tools/verify_channels.py common/src/main/assets/verified_streams.txt
```

El script comprueba que cada URL responda HTTP 200 y luego lee unos segundos con ffmpeg para
confirmar que tenga video y audio no silencioso.

## Aviso legal

CorTV no aloja contenido. Los enlaces de TV en vivo provienen de la lista pública de iptv-org; cada
stream pertenece a su respectivo propietario.

---

Desarrollado por **Adrián Alvarez** ([@Cordobot](https://github.com/Cordobot)).

© 2026 Adrián Alvarez. Todos los derechos reservados.
