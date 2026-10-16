package ca.stewartclan.andb.ui.design

import androidx.core.net.toUri
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.StoredPhoto
import ca.stewartclan.andb.ui.common.PhotoAccess

/** Whether saving Link field [new] over [old] turns on copying its photos into the database. */
fun startsCopying(old: FieldDef, new: FieldDef): Boolean = new.copiesPhotos && !old.copiesPhotos

private val FieldDef.copiesPhotos: Boolean
    get() = (props as? LinkProps)?.let { it.source == LinkSource.PHOTO && it.copyPhotos } == true

/** Copies the photos a Link field already links to into its database, once it starts copying. */
class PhotoCopier(private val records: RecordRepository, private val photos: PhotoAccess) {
    /**
     * Copies each photo Link [field] of database [id] links to in the gallery into the database,
     * one at a time; photos that can't be read any more stay links, shown unavailable.
     */
    suspend fun copyLinked(id: String, field: FieldDef) {
        records.records(id).forEach { record ->
            val link = record.values[field.id] as? LinkValue ?: return@forEach
            if (StoredPhoto.isStored(link.uri)) return@forEach
            val copy = photos.copy(link.uri.toUri()) ?: return@forEach
            // Keep the name and thumbnail it was linked with.
            val stored = LinkValue(copy.uri, link.name, link.thumbnail, copy.photo)
            records.replaceLinks(id, field.id, mapOf(record.id to stored))
        }
    }
}
