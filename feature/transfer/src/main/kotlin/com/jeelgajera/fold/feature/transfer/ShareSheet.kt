package com.jeelgajera.fold.feature.transfer

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.chooser.ChooserAction
import androidx.core.content.FileProvider
import com.jeelgajera.fold.core.storage.mime.MimeResolver
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.FsScheme
import java.io.File

/**
 * Handing files to the rest of Android.
 *
 * ### On Quick Share
 *
 * There is no public API to invoke Quick Share directly. There is a component
 * name, it is undocumented, and it has changed between releases -- an app that
 * hardcodes it works until the month it does not, and then fails in a way the
 * user reads as the file being broken.
 *
 * So FOLD does the supported thing: `ACTION_SEND` through `createChooser`, with
 * a correctly resolved MIME type. Quick Share appears in that sheet alongside
 * everything else. The UI says this in one plain sentence rather than implying a
 * deeper integration, because a user who expects a one-tap Quick Share button and
 * gets a share sheet has been misled by the app, not by Android.
 *
 * ### Why the MIME type is the whole point
 *
 * Most share sheets filter targets by type, and an app that hands over
 * `application/octet-stream` for a `.md` file watches every note app disappear
 * from the sheet. FOLD resolves the type properly -- sniffing the content when the
 * extension is unknown -- which is what makes sharing work for exactly the files
 * other file managers cannot share.
 */
object ShareSheet {

    /**
     * Builds an `ACTION_SEND` chooser for one or more files.
     *
     * @return null when nothing shareable was passed, or when a path is not a raw
     *   file (FileProvider cannot wrap a SAF document URI -- those are shared
     *   directly, see [shareDocumentUris]).
     */
    fun forFiles(context: Context, paths: List<FsPath>): Intent? {
        val files = paths.filter { it.scheme == FsScheme.RAW }.map { File(it.value) }
        if (files.isEmpty()) return null

        val uris = files.mapNotNull { file ->
            runCatching {
                FileProvider.getUriForFile(context, authority(context), file)
            }.getOrNull()
        }
        if (uris.isEmpty()) return null

        // Sniff rather than guess: this is the call that decides whether the
        // receiving app appears in the sheet at all.
        val types = files.map { MimeResolver.resolve(it) }
        val commonType = commonMimeType(types)

        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = commonType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }

        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the grant to targets that read it from there rather
        // than from EXTRA_STREAM, which is most of them on modern Android.
        send.clipData = ClipData.newUri(context.contentResolver, files.first().name, uris.first())
            .also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }

        return Intent.createChooser(send, context.getString(R.string.transfer_chooser_title))
    }

    /** SAF documents are already content URIs, so they are shared as they are. */
    fun shareDocumentUris(context: Context, uris: List<Uri>, mimeType: String): Intent {
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.transfer_chooser_title))
    }

    /**
     * Adds FOLD's own LAN transfer to the system sheet on Android 14+.
     *
     * A custom [ChooserAction] is the supported way for an app to put itself in
     * the sheet it opened. Below API 34 the sheet has no such slot, and the app
     * simply does not appear there -- which is the correct outcome, not a
     * degraded one.
     */
    fun withLanAction(context: Context, chooser: Intent, lanIntent: PendingIntent): Intent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return chooser

        val action = ChooserAction.Builder(
            android.graphics.drawable.Icon.createWithResource(
                context,
                android.R.drawable.stat_sys_upload,
            ),
            context.getString(R.string.transfer_chooser_lan),
            lanIntent,
        ).build()

        return chooser.putExtra(
            Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS,
            arrayOf(action),
        ).putExtras(Bundle())
    }

    /**
     * The narrowest type that covers every file in the selection.
     *
     * Mixed images stay `image/*`; a mixed bag becomes `*/*`. Sending
     * `application/octet-stream` for a mixed selection would empty the sheet.
     */
    private fun commonMimeType(types: List<String>): String {
        if (types.isEmpty()) return "*/*"
        val distinct = types.distinct()
        if (distinct.size == 1) return distinct.first()

        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.first()}/*" else "*/*"
    }

    /** Must match the authority declared for the FileProvider in `:app`'s manifest. */
    private fun authority(context: Context): String = "${context.packageName}.files"
}
