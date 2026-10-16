package ca.stewartclan.andb.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoredPhotoTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository
    private lateinit var records: RecordRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "photo-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
        records = RecordRepository(repository)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Bigger than a cursor can carry at once, so it must be read in pieces. */
    private val big = Random(1).nextBytes(3 * 1024 * 1024 + 7)

    private fun pending(jpeg: ByteArray) = LinkValue(StoredPhoto.PENDING, "label.jpg", byteArrayOf(1, 2), jpeg)

    private suspend fun labels(): Pair<String, FieldDef> {
        val id = repository.create("Wines")
        return id to repository.addField(id, "Label", FieldType.LINK, LinkProps(LinkSource.PHOTO, copyPhotos = true))
    }

    private fun photoCount(id: String): Long =
        SQLiteDatabase.openDatabase(File(dir, "$id.db").path, null, SQLiteDatabase.OPEN_READONLY).use {
            DatabaseUtils.longForQuery(it, "SELECT COUNT(*) FROM andb_photo", null)
        }

    private suspend fun label(id: String, recordId: Long, field: FieldDef) =
        records.record(id, recordId)!!.values[field.id] as LinkValue

    @Test
    fun aCopiedPhotoIsStoredWithTheRecordAndReadBackWhole() = runBlocking {
        val (id, field) = labels()

        val r = records.insert(id, mapOf(field.id to pending(big)), emptyMap())

        val link = label(id, r, field)
        assertTrue(StoredPhoto.isStored(link.uri))
        assertEquals("label.jpg", link.name)
        assertNull(link.photo)
        assertArrayEquals(big, records.storedPhoto(id, link.uri))
    }

    @Test
    fun replacingOrRemovingAPhotoDeletesTheOldOne() = runBlocking {
        val (id, field) = labels()
        val r = records.insert(id, mapOf(field.id to pending(big)), emptyMap())

        records.save(id, r, mapOf(field.id to pending(byteArrayOf(9, 9))), emptyMap())
        assertEquals(1, photoCount(id))
        assertArrayEquals(byteArrayOf(9, 9), records.storedPhoto(id, label(id, r, field).uri))

        records.save(id, r, mapOf(field.id to null), emptyMap())
        assertEquals(0, photoCount(id))
    }

    @Test
    fun aDuplicatedRecordSharesThePhotoUntilBothAreGone() = runBlocking {
        val (id, field) = labels()
        val r = records.insert(id, mapOf(field.id to pending(big)), emptyMap())
        val copy = records.duplicate(id, r)
        val uri = label(id, r, field).uri
        assertEquals(uri, label(id, copy, field).uri)

        records.delete(id, r)
        assertArrayEquals(big, records.storedPhoto(id, uri))

        records.delete(id, copy)
        assertEquals(0, photoCount(id))
    }

    @Test
    fun photosTravelWithExportsButNotWithDesignOnlyCopies() = runBlocking {
        val (id, field) = labels()
        val r = records.insert(id, mapOf(field.id to pending(big)), emptyMap())
        val transfer = DatabaseTransfer(repository)

        val imported = transfer.import(transfer.export(id, withRecords = true))
        val importedLink = records.records(imported).single().values[field.id] as LinkValue
        assertArrayEquals(big, records.storedPhoto(imported, importedLink.uri))

        val empty = repository.duplicate(id, "Empty", withRecords = false)
        assertEquals(0, photoCount(empty))
        assertArrayEquals(big, records.storedPhoto(id, label(id, r, field).uri))
    }
}
