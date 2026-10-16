package ca.stewartclan.andb.data

/** One field of a [Template]. */
data class TemplateField(
    val name: String,
    val type: FieldType,
    val props: FieldProps = FieldProps.default(type),
    val required: Boolean = false
)

/** A ready-made design to start a database from: [fields] in order. [id] is stable, for saved choices. */
data class Template(val id: String, val name: String, val fields: List<TemplateField>)

/** The built-in templates. Photos in them are copied into the database, so they stay with it. */
object Templates {
    private val photo = LinkProps(LinkSource.PHOTO, copyPhotos = true)

    val all: List<Template> = listOf(
        Template(
            "wine",
            "Wine tasting",
            listOf(
                TemplateField("Name", FieldType.STRING, required = true),
                TemplateField("Winery", FieldType.STRING),
                TemplateField("Vintage", FieldType.INTEGER),
                TemplateField(
                    "Colour",
                    FieldType.LIST,
                    ListProps(listOf("Red", "White", "Rosé", "Sparkling", "Dessert", "Fortified"))
                ),
                TemplateField("Rating", FieldType.RATING),
                TemplateField("Tasted", FieldType.DATE),
                TemplateField("Label", FieldType.LINK, photo),
                TemplateField("Notes", FieldType.NOTE)
            )
        ),
        Template(
            "books",
            "Books",
            listOf(
                TemplateField("Title", FieldType.STRING, required = true),
                TemplateField("Author", FieldType.STRING),
                TemplateField("Read", FieldType.BOOLEAN),
                TemplateField("Rating", FieldType.RATING),
                TemplateField("Finished", FieldType.DATE),
                TemplateField("Cover", FieldType.LINK, photo),
                TemplateField("Notes", FieldType.NOTE)
            )
        ),
        Template(
            "recipes",
            "Recipes",
            listOf(
                TemplateField("Name", FieldType.STRING, required = true),
                TemplateField(
                    "Category",
                    FieldType.LIST,
                    ListProps(listOf("Breakfast", "Main", "Side", "Dessert", "Baking", "Drink"))
                ),
                TemplateField("Serves", FieldType.INTEGER),
                TemplateField("Time (minutes)", FieldType.INTEGER),
                TemplateField("Ingredients", FieldType.NOTE),
                TemplateField("Method", FieldType.NOTE),
                TemplateField("Photo", FieldType.LINK, photo)
            )
        ),
        Template(
            "inventory",
            "Home inventory",
            listOf(
                TemplateField("Item", FieldType.STRING, required = true),
                TemplateField(
                    "Room",
                    FieldType.LIST,
                    ListProps(listOf("Kitchen", "Living room", "Bedroom", "Bathroom", "Office", "Garage", "Other"))
                ),
                TemplateField("Value", FieldType.FLOAT),
                TemplateField("Bought", FieldType.DATE),
                TemplateField("Receipt", FieldType.LINK, LinkProps(LinkSource.FILE)),
                TemplateField("Photo", FieldType.LINK, photo),
                TemplateField("Notes", FieldType.NOTE)
            )
        )
    )

    fun find(id: String): Template? = all.find { it.id == id }
}

/** Creates a database named [name] with [template]'s fields and returns its id. */
suspend fun DatabaseRepository.create(name: String, template: Template): String {
    val id = create(name)
    template.fields.forEach { addField(id, it.name, it.type, it.props, it.required) }
    return id
}
