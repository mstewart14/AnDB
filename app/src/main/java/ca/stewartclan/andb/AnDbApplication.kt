package ca.stewartclan.andb

import android.app.Application
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FilterRepository
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.ViewRepository
import ca.stewartclan.andb.ui.common.BrowseOrder
import ca.stewartclan.andb.ui.common.DeviceLinks
import ca.stewartclan.andb.ui.common.FileAccess
import ca.stewartclan.andb.ui.common.PhotoAccess
import ca.stewartclan.andb.ui.common.RecordTitles
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AnDbApplication : Application() {
    /**
     * Databases live under the app's `databases/` directory, which the backup rules keep out of
     * cloud backup.
     */
    val repository: DatabaseRepository by lazy {
        DatabaseRepository(File(getDatabasePath("andb").parentFile, "andb"))
    }

    val records: RecordRepository by lazy { RecordRepository(repository) }

    val views: ViewRepository by lazy { ViewRepository(repository, getString(R.string.view_default_name)) }

    val filters: FilterRepository by lazy { FilterRepository(repository) }

    val browseOrder = BrowseOrder()

    val photos: PhotoAccess by lazy { PhotoAccess(contentResolver, records) }

    val deviceLinks: DeviceLinks by lazy { DeviceLinks(contentResolver) }

    val recordTitles: RecordTitles by lazy { RecordTitles(repository, records) }

    val files: FileAccess by lazy { FileAccess(contentResolver) }

    /** For saves that must finish after the screen that started them is gone. */
    val appScope = CoroutineScope(SupervisorJob())
}
