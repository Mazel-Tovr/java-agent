package com.epam.drill.agent

import kotlin.test.*
//import com.epam.drill.core.*
import com.epam.drill.*
//import com.epam.drill.core

class StringsTest {
    @Test
    fun `prefix matching`() {
        val prefixes = listOf("foo/bar", "!foo/bar/Bar")
        assertTrue { "foo/bar/Baz.class".matches(prefixes) }
        assertTrue { "Lfoo/bar/Baz.class".matches(prefixes, 1) }
        assertFalse { "foo/baz/Baz.class".matches(prefixes) }
        assertFalse { "Lfoo/baz/Baz.class".matches(prefixes, 1) }
        assertFalse { "foo/bar/Bar.class".matches(prefixes) }
        assertFalse { "Lfoo/bar/Bar.class".matches(prefixes, 1) }
    }

    @Test
    fun `lol ` (){
        DRILL_PACKAGE
        val str = "configPath=myVersion/configFile.properties"
//        val asAgentParams = str.asAgentParams()
//        println(asAgentParams)
    }
}
