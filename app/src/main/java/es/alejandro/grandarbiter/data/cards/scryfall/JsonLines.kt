package es.alejandro.grandarbiter.data.cards.scryfall

import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Streams a gzipped Scryfall bulk file one object per line, without loading it in memory.
 * Accepts JSON Lines and Scryfall's older one-object-per-line JSON array ("[", "{...},", "]").
 */
inline fun InputStream.forEachJsonLine(gzipped: Boolean = true, action: (String) -> Unit) {
    val input = if (gzipped) GZIPInputStream(this, 64 * 1024) else this
    BufferedReader(input.reader(Charsets.UTF_8), 64 * 1024).use { reader ->
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim().removeSuffix(",")
            if (line.isEmpty() || line == "[" || line == "]") continue
            action(line)
        }
    }
}

/** Gunzips if the stream starts with the gzip magic bytes, so a source works whether or not the
 *  server (or OkHttp) already decompressed it. */
fun InputStream.decompressedIfGzip(): InputStream {
    val buffered = java.io.BufferedInputStream(this, 64 * 1024)
    buffered.mark(2)
    val b1 = buffered.read()
    val b2 = buffered.read()
    buffered.reset()
    return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered
}
