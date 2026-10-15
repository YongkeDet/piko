package app.crimera.patches.newx.settings

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class SettingsAggregateValidationTest {
    @Test
    fun `every discovered NewX contribution is valid together`() {
        val repositoryRoot = NewXValidationInputs.repositoryRoot()
        val catalogs =
            NewXContributionDiscovery.discover() +
                NewXValidationInputs.builtInCatalog(repositoryRoot)

        assertTrue(catalogs.isNotEmpty())
        assertTrue(catalogs.any { catalog -> catalog.categories.any { it.children.isNotEmpty() } })
        SettingsAggregateValidator.validate(
            catalogs = catalogs,
            resourceNames = NewXValidationInputs.resourceNames(repositoryRoot),
            registryReads = NewXValidationInputs.registryReads(repositoryRoot),
        )
        // A complete settings catalog must also be usable in Chinese, including dynamically
        // formatted dialogs. Missing keys otherwise silently fall back to English at runtime.
        val resources = repositoryRoot.resolve("patches/src/main/resources/addresources")
        fun strings(path: Path): Map<String, Element> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile())
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) as Element }
                .associateBy { it.getAttribute("name") }
        }
        val placeholders = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]")
        for (family in listOf("newx", "shared")) {
            val source = strings(resources.resolve("values/$family/strings.xml"))
            val chinese = strings(resources.resolve("values-zh-rCN/$family/strings.xml"))
            source.filterValues { it.getAttribute("translatable") != "false" }.forEach { (name, original) ->
                val translation = chinese[name]
                assertTrue(translation != null && translation.textContent.isNotBlank(), "Missing Chinese text: $name")
                assertEquals(placeholders.findAll(original.textContent).map { it.value }.sorted().toList(),
                    placeholders.findAll(translation!!.textContent).map { it.value }.sorted().toList(),
                    "Chinese format arguments differ: $name")
            }
        }
    }
}
