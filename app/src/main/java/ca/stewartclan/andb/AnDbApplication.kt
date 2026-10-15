package ca.stewartclan.andb

import android.app.Application
import ca.stewartclan.andb.data.DatabaseRepository
import java.io.File

class AnDbApplication : Application() {
    /**
     * Databases live under the app's `databases/` directory, which the backup rules keep out of
     * cloud backup.
     */
    val repository: DatabaseRepository by lazy {
        DatabaseRepository(File(getDatabasePath("andb").parentFile, "andb"))
    }
}
