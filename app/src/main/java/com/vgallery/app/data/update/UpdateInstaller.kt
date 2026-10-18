package com.vgallery.app.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a release APK, proves it really is a newer build of *this* app, and hands it to the
 * system installer.
 *
 * The proof is the point: the APK is installed over the user's gallery, so it must carry the same
 * package name, a higher version code and — above all — the same release signature. A reply from
 * the host is never enough on its own; a swapped file is refused with a message instead.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Whether Android will let this app open the package installer (the user grants it per app). */
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** "Allow VGallery to install unknown apps" — where the user grants the above. */
    fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Hands a verified APK to the system installer; false when no installer could be opened. */
    fun install(file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess

    /**
     * Streams [release]'s APK into app-private cache storage — no storage permission, and out of
     * reach of other apps — reporting 0..1 progress (or -1 while the host has not declared a size).
     * The returned file has passed both the checksum and the signature checks. Anything wrong is an
     * [IOException] whose message is safe to show to the user.
     */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val url = release.apkUrl ?: throw IOException("This release has no download.")
            if (!isTrustedDownloadUrl(url)) {
                throw IOException("Refused a download from an untrusted host.")
            }

            val destination = updateFile(release.versionName)
            destination.parentFile?.let { dir ->
                dir.mkdirs()
                // One update at a time: a previous APK in here is never needed again.
                dir.listFiles()?.forEach { if (it.name != destination.name) it.delete() }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            var received = 0L
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("The download failed (HTTP $code).")
                val total = connection.contentLengthLong
                onProgress(if (total > 0L) 0f else -1f)
                connection.inputStream.use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (received + read > MAX_APK_BYTES) {
                                throw IOException("The download is larger than an APK should be.")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            received += read
                            onProgress(
                                if (total > 0L) {
                                    (received.toFloat() / total).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                }
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                destination.delete()
                throw e
            } finally {
                connection.disconnect()
            }

            if (received <= 0L) {
                destination.delete()
                throw IOException("The download was empty.")
            }

            val expected = release.apkSha256
            if (expected != null && !expected.equals(digest.digest().toHex(), ignoreCase = true)) {
                destination.delete()
                throw IOException("The download did not match the published checksum.")
            }

            verifyApk(destination)?.let { problem ->
                destination.delete()
                throw IOException(problem)
            }

            destination
        }

    /** The app-private cache file an update for [versionName] is written to. */
    fun updateFile(versionName: String): File =
        File(File(context.cacheDir, DIRECTORY_NAME), "vgallery_v$versionName.apk")

    /**
     * Null when [file] is a genuine, newer build of this app; otherwise the reason to refuse it.
     * Exposed (rather than private) so the checks can be exercised directly.
     */
    fun verifyApk(file: File): String? {
        if (!file.isFile || file.length() <= 0L) return "The downloaded file was empty."
        val archive = archiveInfo(file)
            ?: return "The downloaded file is not an installable app."
        if (archive.packageName != context.packageName) {
            return "The download is for a different app (${archive.packageName})."
        }
        if (versionCodeOf(archive) <= versionCodeOf(installedInfo())) {
            return "The download is not newer than the installed build."
        }
        val ours = signatures(installedInfo())
        val theirs = signatures(archive)
        if (ours.isEmpty() || theirs.isEmpty() || ours != theirs) {
            return "The download is not signed with VGallery's release key."
        }
        return null
    }

    private fun archiveInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    private fun installedInfo(): PackageInfo =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException("VGallery is not installed?", e)
        }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }

    /** The signing certificates of a package, normalised so two sets can be compared directly. */
    private fun signatures(info: PackageInfo): Set<List<Byte>> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().map { it.toByteArray().toList() }.toSet()
    }

    private fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt()
            builder.append(HEX[(value shr 4) and 0xF])
            builder.append(HEX[value and 0xF])
        }
        return builder.toString()
    }

    private companion object {
        const val DIRECTORY_NAME = "updates"
        const val TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_APK_BYTES = 200L * 1024L * 1024L
        const val USER_AGENT = "VGallery-Android"
        const val HEX = "0123456789abcdef"
    }
}
