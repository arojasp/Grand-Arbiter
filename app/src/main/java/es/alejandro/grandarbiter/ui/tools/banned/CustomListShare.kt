package es.alejandro.grandarbiter.ui.tools.banned

import android.content.Context
import android.content.Intent
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListText

/** Shares the list as plain text in the paste format, so whoever gets it can paste it back. */
internal fun shareCustomList(context: Context, list: CustomList) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, list.name.trim())
        putExtra(Intent.EXTRA_TEXT, CustomListText.format(list))
    }
    context.startActivity(Intent.createChooser(send, list.name.trim()))
}
