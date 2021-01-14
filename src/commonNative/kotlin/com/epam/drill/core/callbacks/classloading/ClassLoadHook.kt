@file:Suppress("UNUSED_VARIABLE")

package com.epam.drill.core.callbacks.classloading

import com.epam.drill.*
import com.epam.drill.agent.*
import com.epam.drill.agent.instrument.*
import com.epam.drill.agent.instrument.SSLTransformer.SSL_ENGINE_CLASS_NAME
import com.epam.drill.core.plugin.loader.*
import com.epam.drill.jvmapi.gen.*
import com.epam.drill.logger.*
import io.ktor.utils.io.bits.*
import kotlinx.cinterop.*
import org.objectweb.asm.*

@SharedImmutable
private val logger = Logging.logger("jvmtiEventClassFileLoadHookEvent")

@Suppress("unused", "UNUSED_PARAMETER")
fun classLoadEvent(
    jvmtiEnv: CPointer<jvmtiEnvVar>?,
    jniEnv: CPointer<JNIEnvVar>?,
    classBeingRedefined: jclass?,
    loader: jobject?,
    clsName: CPointer<ByteVar>?,
    protection_domain: jobject?,
    classDataLen: jint,
    classData: CPointer<UByteVar>?,
    newClassDataLen: CPointer<jintVar>?,
    newData: CPointer<CPointerVar<UByteVar>>?
) {
    initRuntimeIfNeeded()
    if (isBootstrapClassLoading(loader, protection_domain) && !config.isTlsApp) return
    val kClassName = clsName?.toKString()
    if (kClassName == null || classData == null || kClassName.startsWith(DRILL_PACKAGE)) return
//    logger.warn { "classLoadEvent for class name:$kClassName" }
    try {
        val classBytes = ByteArray(classDataLen).apply {
            Memory.of(classData, classDataLen).loadByteArray(0, this)
        }
        val transformers = mutableListOf<(ByteArray) -> ByteArray?>()
        if (config.isAsyncApp || config.isWebApp) {
            val classReader = ClassReader(classBytes)
            if (
                config.isAsyncApp &&
                (kClassName in TTLTransformer.directTtlClasses ||
                        kClassName != TTLTransformer.timerTaskClass) &&
                (TTLTransformer.runnableInterface in classReader.interfaces ||
                        classReader.superName == TTLTransformer.poolExecutor)
            ) {
                transformers += { bytes ->
                    TTLTransformer.transform(
                        loader,
                        kClassName,
                        classBeingRedefined,
                        bytes
                    )
                }
            }
            if (config.isWebApp && Transformer.servletListener in classReader.interfaces) {
                transformers += { bytes -> Transformer.transform(kClassName, bytes, loader) }
            } else {
                if (classReader.superName == SSL_ENGINE_CLASS_NAME) {
                    transformers += { bytes -> SSLTransformer.transform(kClassName, bytes, loader) }
                }
            }
        }
        if ('$' !in kClassName && kClassName.matches(state.packagePrefixes)) {
            pstorage.values.filterIsInstance<InstrumentationNativePlugin>().forEach { plugin ->
                transformers += { bytes -> plugin.instrument(kClassName, bytes) }
            }
        }

        val isTomcat = kClassName.startsWith("org/apache/catalina/core/ApplicationFilterChain")
        if (isTomcat) {
            val retrieveAdminUrl = retrieveAdminUrl()
            val idHeaderPair = idHeaderPairFromConfig()
            logger.warn { "$isTomcat kClassName $kClassName. $retrieveAdminUrl; $idHeaderPair" }
            val idHeaderKey = idHeaderPair.first
            val idHeaderValue = idHeaderPair.second
            transformers += { bytes ->
                TomcatTransformer.transform(
                    kClassName, bytes, loader, retrieveAdminUrl, idHeaderKey, idHeaderValue
                )
            }
        }
        if (kClassName.startsWith("javax/servlet/FilterChain")) {
            logger.warn { "kClassName FilterChain $kClassName" }

        }
        val classReader = ClassReader(classBytes)
        if (classReader.superName == "javax/servlet/FilterChain") {
            logger.warn { "kClassName $kClassName" } //todo remove
        }
        if (transformers.any()) {
            transformers.fold(classBytes) { bytes, transformer ->
                transformer(bytes) ?: bytes
            }.takeIf { it !== classBytes }?.let { newBytes ->
                logger.trace { "$kClassName transformed" }
                convertToNativePointers(newBytes, newData, newClassDataLen)
            }
        }
    } catch (throwable: Throwable) {
        logger.error(throwable) {
            "Can't retransform class: $kClassName, ${classData.readBytes(classDataLen).contentToString()}"
        }
    }
}

fun idHeaderPairFromConfig(): Pair<String, String> =
    when (val groupId = agentConfig.serviceGroupId) {
        "" -> "drill-agent-id" to agentConfig.id
        else -> "drill-group-id" to groupId
    }

fun retrieveAdminUrl(): String {//todo string?
    return if (secureAdminAddress != null) {
        secureAdminAddress?.toUrlString(false).toString()
    } else adminAddress?.toUrlString(false).toString()

}

private fun convertToNativePointers(
    instrumentedBytes: ByteArray,
    newData: CPointer<CPointerVar<UByteVar>>?,
    newClassDataLen: CPointer<jintVar>?
) {
    val instrumentedSize = instrumentedBytes.size
    Allocate(instrumentedSize.toLong(), newData)
    instrumentedBytes.forEachIndexed { index, byte ->
        val innerValue = newData!!.pointed.value!!
        innerValue[index] = byte.toUByte()
    }
    newClassDataLen!!.pointed.value = instrumentedSize
}

private fun isBootstrapClassLoading(loader: jobject?, protection_domain: jobject?) =
    loader == null || protection_domain == null

