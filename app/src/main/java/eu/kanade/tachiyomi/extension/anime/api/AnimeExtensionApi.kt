package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import eu.kanade.tachiyomi.extension.api.ExtensionIndexPbDto
import eu.kanade.tachiyomi.extension.api.decompressIfGzipped
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.time.Duration.Companion.days

internal class AnimeExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetAnimeExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateAnimeExtensionRepo by injectLazy()
    private val animeExtensionManager: AnimeExtensionManager by injectLazy()
    private val json: Json by injectLazy()

    private val protoBuf = ProtoBuf {}

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<AnimeExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<AnimeExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        val jsonExtensions = try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .awaitSuccess()

            with(json) {
                response
                    .parseAs<List<AnimeExtensionJsonObject>>()
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG) { "JSON index unavailable for $repoBaseUrl (${e.message})" }
            emptyList()
        }
        return jsonExtensions.ifEmpty { getProtoBufExtensions(repoBaseUrl) }
    }

    private suspend fun getProtoBufExtensions(repoBaseUrl: String): List<AnimeExtension.Available> {
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.pb"))
                .awaitSuccess()
            val bytes = response.body!!.source().decompressIfGzipped().use { it.readByteArray() }
            val index = protoBuf.decodeFromByteArray(ExtensionIndexPbDto.serializer(), bytes)
            index.extensionList?.extensions.orEmpty().mapNotNull { ext ->
                val libVersion = ext.extensionLib.toDoubleOrNull()
                    ?: ext.extensionLib.substringBeforeLast('.').toDoubleOrNull()
                    ?: return@mapNotNull null
                if (libVersion < AnimeExtensionLoader.LIB_VERSION_MIN ||
                    libVersion > AnimeExtensionLoader.LIB_VERSION_MAX
                ) {
                    return@mapNotNull null
                }
                val apkUrl = ext.resources?.apkUrl.orEmpty()
                if (apkUrl.isEmpty()) return@mapNotNull null

                AnimeExtension.Available(
                    name = ext.name.substringAfter("Aniyomi: "),
                    pkgName = ext.packageName,
                    versionName = ext.versionName,
                    versionCode = ext.versionCode,
                    libVersion = libVersion,
                    lang = ext.packageName.substringAfterLast("extension.").substringBefore('.'),
                    isNsfw = ext.contentWarning == ExtensionIndexPbDto.ContentWarning.NSFW ||
                        ext.contentWarning == ExtensionIndexPbDto.ContentWarning.MIXED,
                    isTorrent = false,
                    sources = (ext.sources + ext.animeSources).map { source ->
                        AnimeExtension.Available.AnimeSource(
                            id = source.id,
                            lang = source.language,
                            name = source.name,
                            baseUrl = source.homeUrl,
                        )
                    },
                    apkName = apkUrl.substringAfterLast('/'),
                    iconUrl = ext.resources?.iconUrl?.takeIf { it.isNotEmpty() }
                        ?: "$repoBaseUrl/icon/${ext.packageName}.png",
                    repoUrl = repoBaseUrl,
                    apkUrl = apkUrl,
                )
            }
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG) { "Protobuf index unavailable for $repoBaseUrl (${e.message})" }
            emptyList()
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<AnimeExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            animeExtensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = AnimeExtensionLoader.loadExtensions(context)
            .filterIsInstance<AnimeLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<AnimeExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(
                names = extensionsWithUpdate.map { it.name },
                anime = true,
            )
        }

        return extensionsWithUpdate
    }

    private fun List<AnimeExtensionJsonObject>.toExtensions(repoUrl: String): List<AnimeExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= AnimeExtensionLoader.LIB_VERSION_MIN && libVersion <= AnimeExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                AnimeExtension.Available(
                    name = it.name.substringAfter("Aniyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    isTorrent = it.torrent == 1,
                    sources = it.sources?.map(extensionAnimeSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: AnimeExtension.Available): String {
        return extension.apkUrl ?: "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun AnimeExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class AnimeExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val torrent: Int = 0,
    val sources: List<AnimeExtensionSourceJsonObject>?,
)

@Serializable
private data class AnimeExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionAnimeSourceMapper: (AnimeExtensionSourceJsonObject) -> AnimeExtension.Available.AnimeSource = {
    AnimeExtension.Available.AnimeSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}
