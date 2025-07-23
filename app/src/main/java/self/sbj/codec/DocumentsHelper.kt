package self.sbj.codec

import android.database.Cursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsProvider

class DocumentsHelper : DocumentsProvider() {
    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        TODO("openDocument - Not yet implemented")
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        TODO("queryChildDocuments - Not yet implemented")
    }

    override fun queryDocument(
        documentId: String?,
        projection: Array<out String?>?
    ): Cursor? {
        TODO("queryDocument - Not yet implemented")
    }

    override fun queryRoots(projection: Array<out String?>?): Cursor? {
        TODO("queryRoots - Not yet implemented")
    }

    override fun onCreate(): Boolean {
        TODO("onCreate - Not yet implemented")
    }
}