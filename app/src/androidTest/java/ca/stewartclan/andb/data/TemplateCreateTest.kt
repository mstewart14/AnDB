package ca.stewartclan.andb.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateCreateTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "template-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun eachTemplateMakesADatabaseWithItsFields() = runBlocking {
        Templates.all.forEach { template ->
            val id = repository.create("My ${template.name}", template)
            val fields = repository.fields(id)
            assertEquals(template.fields.map { it.name }, fields.map { it.name })
            assertEquals(template.fields.map { it.type }, fields.map { it.type })
            assertEquals(template.fields.map { it.props }, fields.map { it.props })
            assertEquals(template.fields.map { it.required }, fields.map { it.required })
        }
        assertEquals(Templates.all.size, repository.readSummaries().size)
    }
}
