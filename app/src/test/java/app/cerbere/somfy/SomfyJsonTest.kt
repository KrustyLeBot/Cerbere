package app.cerbere.somfy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SomfyJsonTest {
    @Test
    fun `sites are read from the items list`() {
        val sites = SomfyJson.sites("""{"items":[{"site_id":"s1","label":"Maison","security_level":"partial"}]}""")
        assertEquals(listOf(Site("s1", "Maison", Level.PARTIAL)), sites)
    }

    @Test
    fun `a bare array of sites is accepted too`() {
        assertEquals("s2", SomfyJson.sites("""[{"site_id":"s2","name":"Chalet","security_level":"armed"}]""").single().id)
    }

    @Test
    fun `an unknown level stays unknown rather than guessed`() {
        assertNull(SomfyJson.sites("""{"items":[{"site_id":"s1","security_level":"weird"}]}""").single().level)
    }

    @Test
    fun `a level change event gives the site and the level`() {
        val change = SomfyJson.levelChange("""{"key":"security.level.change","site_id":"s1","security_level":"disarmed"}""")
        assertEquals("s1" to Level.DISARMED, change)
    }

    @Test
    fun `other events and garbage are ignored`() {
        assertNull(SomfyJson.levelChange("""{"key":"video.stream","site_id":"s1"}"""))
        assertNull(SomfyJson.levelChange("not json"))
        assertNull(SomfyJson.levelChange("""{"key":"security.level.change","security_level":"armed"}"""))
    }

    @Test
    fun `the token error is recognized`() {
        assertTrue(SomfyJson.isTokenError("""{"key":"websocket.error.token"}"""))
    }
}
