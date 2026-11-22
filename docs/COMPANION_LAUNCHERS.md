# Companions de extensión (estado actual)

Objetivo: convertir una extensión instalada en una app independiente con nombre e icono propios,
que abra directamente el catálogo de esa extensión y mantenga Home/Biblioteca/Configuración aisladas.

## Ya implementado en el host

- `MainActivity` acepta la acción explícita `eu.kanade.extyomi.OPEN_EXTENSION` con extras:
  - `sourceId` (`Long`): ID de la fuente de la extensión.
  - `kind` (`String`): `"anime"` (por defecto) o `"manga"`.
- Al recibirla, el host vuelve a la raíz y abre `BrowseAnimeSourceScreen(sourceId, null)` o
  `BrowseMangaSourceScreen(sourceId, null)`.
- Funciona tanto en arranque frío como con la app ya abierta (`onNewIntent`).

Este es el punto de entrada que usarán los APK lanzador generados en el dispositivo.

## Pendiente

1. **Aislamiento real por extensión**: modo companion con `sourceId` persistido, filtrando
   Biblioteca e Historial y ocultando pestañas ajenas en Home; ajustes contextuales según
   `kind` (anime → reproductor, manga → visor).
2. **Plantilla de lanzador**: módulo Android mínimo con una actividad que lee `sourceId`/`kind`
   y lanza la acción indicada contra el paquete del host (`com.pepethecarton17.extyomi` o
   `...preview`).
3. **Generador en dispositivo**: elegir extensión, nombre e icono, parchear la plantilla con
   ARSCLib, firmar con `com.android.tools.build:apksig` y escribir el APK en la carpeta SAF
   elegida; instalar con `PackageInstaller`.
4. **Spike de firma**: verificar que `apksig` funciona en Android (el JAR no usa
   `java.nio.file` ni BouncyCastle, por lo que es prometedor) y que el keystore puede
   generarse/guardarse con Android Keystore.

## Resultado del spike de `apksig` (JVM)

- Artefacto usable: `com.android.tools.build:apksig:8.9.1`, disponible en el repositorio
  Maven de Google (`https://dl.google.com/dl/android/maven2/...`); Maven Central solo
  publica la versión 2.3.0.
- El JAR no referencia `java.nio.file` ni BouncyCastle; usa `java.security` y
  `java.util.zip`, ambos disponibles en Android.
- Prueba realizada: se firmó un APK Preview real (78 MB) con un keystore PKCS12
  provisional usando `ApkSigner.SignerConfig.Builder` + `ApkSigner.Builder`
  (V1/V2/V3, `minSdkVersion=23`). Salida correcta y verificada con
  `apksigner verify --print-certs` (exit 0, huella SHA-256 del certificado de prueba).
- Queda pendiente repetir la prueba dentro de un proceso Android (ART), donde
  `java.security.KeyStore` puede estar respaldado por Android Keystore.

## Notas de seguridad

- No se redistribuyen APKs de terceros: el companion es un lanzador propio.
- La instalación debe pedir confirmación explícita al usuario (`USER_ACTION_REQUIRED`).
- Todo lo que genere el companion se guarda únicamente en la carpeta elegida vía SAF.
