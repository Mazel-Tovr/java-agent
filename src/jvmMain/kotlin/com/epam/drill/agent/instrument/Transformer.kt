@file:Suppress("unused", "UNUSED_PARAMETER")

package com.epam.drill.agent.instrument

import com.alibaba.ttl.internal.javassist.*
import com.epam.drill.agent.classloading.*
import java.io.*


object Transformer {
    private val classPool = ClassPool()

    fun transform(className: String, classfileBuffer: ByteArray, loader: ClassLoader): ByteArray? {
        return try {
            classPool.appendClassPath(LoaderClassPath(loader))
            classPool.makeClass(ByteArrayInputStream(classfileBuffer))?.run {
                if (interfaces.isNotEmpty() && interfaces.map { it.name }.contains("javax.servlet.ServletContextListener")) {
                    val qualifiedName = WebContainerSource::class.qualifiedName
                    val fillWeSourceMethodName = WebContainerSource::fillWebAppSource.name
                    declaredMethods.first { it.name == "contextInitialized" }.insertBefore(
                        "$qualifiedName.INSTANCE.$fillWeSourceMethodName(\$1.getServletContext().getRealPath(\"\"));"
                    )
                    return toBytecode()
                } else
                    null

            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}