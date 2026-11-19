package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.content.Context
import android.content.Intent
import es.alejandro.grandarbiter.data.draft.DraftPresetShareCodec
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset

/** Shares the preset as its one-line JSON, so whoever gets it can paste it into Import preset. */
internal fun shareDraftPreset(context: Context, preset: UserDraftPreset) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, preset.name)
        putExtra(Intent.EXTRA_TEXT, DraftPresetShareCodec.encode(preset))
    }
    context.startActivity(Intent.createChooser(send, preset.name))
}
