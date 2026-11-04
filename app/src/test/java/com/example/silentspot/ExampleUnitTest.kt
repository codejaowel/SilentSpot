package com.example.silentspot

import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.data.model.User
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.NoiseLevelUtils
import com.example.silentspot.utils.PasswordUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPasswordHashingAndVerification() {
        val rawPassword = "StudentPassword2026"
        val hash = PasswordUtils.hash(rawPassword)

        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length) // SHA-256 is 64 hex characters

        // Correct password verifies against hash
        assertTrue(PasswordUtils.verify(rawPassword, hash))

        // Incorrect password fails
        assertFalse(PasswordUtils.verify("WrongPassword", hash))

        // Legacy plain-text fallback verifies
        assertTrue(PasswordUtils.verify("plainText123", "plainText123"))
    }

    @Test
    fun testNoiseLevelClassification() {
        assertEquals(Constants.STATUS_QUIET, NoiseLevelUtils.classify(25.0))
        assertEquals(Constants.STATUS_QUIET, NoiseLevelUtils.classify(Constants.QUIET_MAX_DB))

        assertEquals(Constants.STATUS_MODERATE, NoiseLevelUtils.classify(40.1))
        assertEquals(Constants.STATUS_MODERATE, NoiseLevelUtils.classify(Constants.MODERATE_MAX_DB))

        assertEquals(Constants.STATUS_NOISY, NoiseLevelUtils.classify(60.1))
        assertEquals(Constants.STATUS_NOISY, NoiseLevelUtils.classify(85.0))
    }

    @Test
    fun testFindQuietestStudySpace() {
        val spaces = listOf(
            StudySpace(id = "1", name = "Noisy Hall", noiseLevel = 72.0, isAvailable = true),
            StudySpace(id = "2", name = "Quiet Pod", noiseLevel = 32.5, isAvailable = true),
            StudySpace(id = "3", name = "Silent Room (Closed)", noiseLevel = 22.0, isAvailable = false),
            StudySpace(id = "4", name = "Library Main", noiseLevel = 45.0, isAvailable = true)
        )

        val quietest = NoiseLevelUtils.findQuietest(spaces)
        assertNotNull(quietest)
        assertEquals("Quiet Pod", quietest?.name)
        assertEquals(32.5, quietest?.noiseLevel ?: 0.0, 0.01)

        // If no spaces are available, return null
        val unavailableOnly = listOf(StudySpace(id = "5", isAvailable = false))
        assertNull(NoiseLevelUtils.findQuietest(unavailableOnly))
    }

    @Test
    fun testFormatDb() {
        assertEquals("35", NoiseLevelUtils.formatDb(35.0))
        assertEquals("35.5", NoiseLevelUtils.formatDb(35.5))
    }

    @Test
    fun testUserRoleAndAdminPrivileges() {
        val student = User(email = "student@silentspot.edu", role = Constants.ROLE_STUDENT)
        assertFalse("Students must not have admin access", student.isAdmin)

        val defaultUser = User(email = "random@silentspot.edu")
        assertFalse("Default role must not have admin access", defaultUser.isAdmin)

        val admin = User(email = "admin@silentspot.edu", role = Constants.ROLE_ADMIN)
        assertTrue("Admins must have admin access", admin.isAdmin)

        val upperCaseAdmin = User(email = "admin@silentspot.edu", role = "ADMIN")
        assertTrue("Admin role check should be case-insensitive", upperCaseAdmin.isAdmin)
    }
}
