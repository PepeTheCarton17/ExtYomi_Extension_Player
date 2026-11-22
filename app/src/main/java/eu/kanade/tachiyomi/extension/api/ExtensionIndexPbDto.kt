package eu.kanade.tachiyomi.extension.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import okio.BufferedSource
import okio.buffer
import okio.gzip

/** Some repos serve `index.pb` as a gzip-compressed binary without a Content-Encoding header. */
fun BufferedSource.decompressIfGzipped(): BufferedSource {
    val isGzip = peek().use { peeked ->
        try {
            peeked.readShort().toInt() and 0xFFFF == 0x1F8B
        } catch (_: Exception) {
            false
        }
    }
    return if (isGzip) gzip().buffer() else this
}

/**
 * Protobuf schema used by Mihon-style `index.pb` extension repositories
 * (Keiyoushi, ProjectNox, ...). Field numbers must match the repository's `Index` message.
 */
@Serializable
data class ExtensionIndexPbDto(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(
        @ProtoNumber(1) val extensions: List<Extension> = emptyList(),
    )

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: Resources? = null,
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
        // Aniyomi repos (ProjectNox) store sources in field 9 instead of 8.
        @ProtoNumber(9) val animeSources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String = "",
        @ProtoNumber(2) val iconUrl: String = "",
        // Extra fields from newer repos are ignored by kotlinx protobuf; declare the common one.
        @ProtoNumber(501) val jarUrl: String = "",
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long = 0,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val language: String = "",
        @ProtoNumber(4) val homeUrl: String = "",
    )

    @Serializable
    enum class ContentWarning {
        @ProtoNumber(0)
        UNSPECIFIED,

        @ProtoNumber(1)
        SAFE,

        @ProtoNumber(2)
        MIXED,

        @ProtoNumber(3)
        NSFW,
    }
}
