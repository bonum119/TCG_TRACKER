#  TCG Tracker (Android)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Material 3](https://img.shields.io/badge/Material-3-blue.svg)](https://m3.material.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**TCG Tracker** es una aplicación nativa para Android desarrollada en Kotlin que permite a los coleccionistas de juegos de cartas coleccionables gestionar su inventario, consultar precios de mercado en tiempo real, escanear cartas con la cámara y organizar álbumes personalizados.

---

##  Aviso Legal / Disclaimer (Legal Notice)
*Este proyecto es una herramienta de código abierto no oficial creada por fans para fans. **No está afiliada, respaldada, patrocinada ni asociada de ninguna manera con Nintendo, The Pokémon Company, Game Freak o Creatures Inc.** Los nombres de personajes, expansiones, marcas registradas y material gráfico pertenecen a sus respectivos propietarios. Esta aplicación es 100% gratuita, sin ánimo de lucro, sin anuncios y sin monetización.*

*This project is an unofficial open-source fan-made utility tool. It is not affiliated, endorsed, sponsored, or associated with Nintendo, The Pokémon Company, Game Freak, or Creatures Inc. All trademarks and artwork belong to their respective owners. This app is 100% free, non-commercial, ad-free, and unmonetized.*

---

##  Características Principales (Features)

-  **Escáner OCR por Cámara**: Reconocimiento de texto y comparación visual inteligente para identificar cartas rápidamente.
-  **Precios de Mercado Reales**: Consulta de precios en tiempo real en USD (TCGPlayer) y EUR (Cardmarket).
-  **Álbumes y Carpetas**: Organiza tus cartas en álbumes personalizados y compártelos fácilmente.
-  **Exportar e Importar Álbumes**: Genera un archivo `.json` de respaldo y pásaselo a un amigo para que vea tu colección completa al instante.
-  **Lista de Deseos (Wishlist)**: Controla las cartas que estás buscando.
-  **Progreso por Sets y Colecciones**: Explora expansiones con barras de porcentaje completado y distingue qué cartas posees de las que te faltan.
-  **Detección de Valor en Alza**: Indicadores en los resultados de búsqueda para destacar cartas con alta cotización.
-  **Filtros Avanzados y Búsqueda Rápida**: Filtra tu colección por duplicados/repetidas, condición física (*Mint*, *Near Mint*, etc.) y rango de precios con barra deslizante interactiva (*RangeSlider*).
-  **Ordenación Flexible**: Ordena tus cartas por fecha de adición, precio (de más cara a menos cara y viceversa) o por expansión.
-  **Soporte Multidioma**: Selector integrado en configuración para cambiar en tiempo real entre Español e Inglés.
-  **Música de Fondo Ambiental**: Reproductor de música en bucle con opción de silenciar desde los ajustes de la app.

---

##  Tecnologías Utilizadas

- **Lenguaje**: 100% Kotlin
- **Arquitectura**: UI Declarativa con ViewBinding y componentes modernos de Android
- **Concurrencia**: Kotlin Coroutines
- **Navegación**: Jetpack Navigation Component
- **Diseño**: Material Design 3 (Material You)
- **IA / OCR**: Google ML Kit Text Recognition

---

##  Distribución Gratuita

Puedes compilar el archivo APK directamente desde Android Studio o descargar la última versión compilada desde la pestaña **Releases** de este repositorio de GitHub para instalarla de forma gratuita en tu dispositivo Android.
