/*
 * Project extension utilities
 * Provides: Version catalog accessor
 */

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Access the libs version catalog from the convention plugins. Internal so it does not shadow
 * the generated `libs` accessor in module build scripts.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
