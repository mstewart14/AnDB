package ca.stewartclan.andb.data

/** A record change was refused because required [fields] (by name) would be empty. */
class RequiredFieldsMissingException(val fields: List<String>) :
    IllegalStateException("Required fields are empty: ${fields.joinToString()}")
