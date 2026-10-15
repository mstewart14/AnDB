package ca.stewartclan.andb

import android.app.Application
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FilterRepository
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.ViewRepository
import ca.stewartclan.andb.ui.common.BrowseOrder
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

    /** For saves that must finish after the screen that started them is gone. */
    val appScope = CoroutineScope(SupervisorJob())
}
