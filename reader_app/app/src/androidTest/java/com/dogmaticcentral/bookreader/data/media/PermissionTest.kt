package com.dogmaticcentral.bookreader // Use your app's package name

import android.Manifest
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
// This test is only relevant for Android 13 (API 33) and above,
// where READ_MEDIA_AUDIO is required.
@SdkSuppress(minSdkVersion = 33)
class PermissionTest {

    /**
     * This is the key part we are testing. This rule should grant the
     * READ_MEDIA_AUDIO permission to the app before the test runs.
     */
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO
    )

    /**
     * This test attempts the most basic query against the MediaStore.
     * Its only purpose is to see if a SecurityException is thrown.
     */
    @Test
    fun readMediaAudioPermission_isGranted() {
        // Get the context of the app under test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contentResolver = context.contentResolver

        try {
            // Perform a minimal, simple query. We don't care about the result,
            // only that this line doesn't crash.
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID), // A minimal projection
                null,
                null,
                null
            )

            // It's good practice to close the cursor. If the query throws an
            // exception, this line will never be reached.
            cursor?.close()

            // If we get here, it means no SecurityException was thrown.
            // The test passes.
            println("SUCCESS: The query executed without a SecurityException.")

        } catch (e: SecurityException) {
            e.printStackTrace()
            // If we are in this catch block, the permission was NOT granted.
            // We explicitly fail the test with a clear message.
            fail("Test failed: A SecurityException was thrown. The READ_MEDIA_AUDIO permission was not successfully granted by the test rule.")
        }
    }
}