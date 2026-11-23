package app.edroid.data.install

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConflictingPackageTest {

    @Test
    fun `a non-conflict failure names no package`() {
        assertNull(conflictingPackage(PackageInstaller.STATUS_FAILURE, "org.other", "org.target") { true })
    }

    @Test
    fun `a conflict with no other package names the target`() {
        assertEquals("org.target", conflictingPackage(PackageInstaller.STATUS_FAILURE_CONFLICT, null, "org.target") { true })
    }

    @Test
    fun `a conflict with an other package names that package`() {
        assertEquals("org.other", conflictingPackage(PackageInstaller.STATUS_FAILURE_CONFLICT, "org.other", "org.target") { true })
    }

    @Test
    fun `a conflict with a package that is not installed names no package`() {
        assertNull(conflictingPackage(PackageInstaller.STATUS_FAILURE_CONFLICT, null, "org.target") { false })
    }
}
