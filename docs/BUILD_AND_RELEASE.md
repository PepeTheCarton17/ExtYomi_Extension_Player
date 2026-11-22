# ExtYomi: compilación y releases

Hay **dos apps independientes**, con distinto `applicationId` y distinta firma:

| Variante | Repo | `applicationId` | Build type | Tag del release | Título |
|---|---|---|---|---|---|
| Preview | `PepeTheCarton17/ExtYomi_Extension_Player-Preview` | `com.pepethecarton17.extyomi.preview` | `preview` | `preview-vX.Y.Z` | `ExtYomi Preview vX.Y.Z` |
| Estable | `PepeTheCarton17/ExtYomi_Extension_Player` | `com.pepethecarton17.extyomi` | `release` | `vX.Y.Z` | `ExtYomi vX.Y.Z` |

## Versiones

La versión se define en `app/build.gradle.kts` (`versionCode` y `versionName`). Para publicar una nueva:

1. Actualizar `versionCode` y `versionName`.
2. Actualizar `TAG` y `TITLE` en `.github/workflows/preview.yml` y `.github/workflows/stable.yml`.
3. Commit y push a `main` en ambos repos.

Preview se publica automáticamente en cada push a `main` de su repo. Estable se publica al ejecutar el workflow **ExtYomi Stable** manualmente (`workflow_dispatch`) o al pushear un tag `v*`.

## Firmas

Los keystores **no están en el repositorio**. Se generaron como PKCS12 y se guardan como GitHub Secrets en cada repo:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

El workflow decodifica el keystore en `$RUNNER_TEMP` y lo pasa a Gradle mediante la variable de entorno `KEYSTORE_FILE`. `app/build.gradle.kts` define los `signingConfigs` `preview` y `release`.

## Iconos

- Estable: recursos en `app/src/main/res/mipmap-*` — icono del **mouse con triángulo de play**.
- Preview: recursos en `app/src/preview/res/mipmap-*` — icono del **símbolo de pausa**.
- El nombre mostrado se define en `i18n/src/commonMain/moko-resources/base/strings.xml` (`ExtYomi`) y se sobrescribe para Preview en `app/src/preview/res/values/strings.xml` (`ExtYomi Preview`).

## CI

`.github/workflows/build_push.yml` es solo verificación: `spotlessCheck`, `assembleDebug` y tests unitarios. Los builds firmados y los releases los hacen `preview.yml` y `stable.yml`.

`flexible-adapter/` es una copia local (Apache-2.0) del módulo usado por Nekoyomi: el fork de JitPack ya no se puede compilar. Ver `flexible-adapter/README.md`.
