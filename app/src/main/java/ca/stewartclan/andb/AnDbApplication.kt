package ca.stewartclan.andb

import android.app.Application
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.RecordRepository
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

    /** For saves that must finish after the screen that started them is gone. */
    val appScope = CoroutineScope(SupervisorJob())
}
