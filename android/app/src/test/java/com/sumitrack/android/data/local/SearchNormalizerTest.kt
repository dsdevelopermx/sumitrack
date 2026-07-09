package com.sumitrack.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormalizerTest {

    @Test
    fun `normalize strips accents and lowercases`() {
        assertEquals("lopez", SearchNormalizer.normalize("López"))
        assertEquals("ferreteria el clavo", SearchNormalizer.normalize("Ferretería El Clavo"))
        assertEquals("nino", SearchNormalizer.normalize("Niño"))
    }

    @Test
    fun `normalize is a no-op for plain ascii text`() {
        assertEquals("bernardo ruiz", SearchNormalizer.normalize("Bernardo Ruiz"))
    }

    @Test
    fun `toLikePattern escapes SQL LIKE wildcards`() {
        assertEquals("100\\% garantizado", SearchNormalizer.toLikePattern("100% Garantizado"))
        assertEquals("a\\_b", SearchNormalizer.toLikePattern("A_B"))
        assertEquals("a\\\\b", SearchNormalizer.toLikePattern("A\\B"))
    }

    @Test
    fun `toLikePattern normalizes accents before escaping`() {
        assertEquals("lopez", SearchNormalizer.toLikePattern("López"))
    }
}
