package com.epam.drill.agent.instrument

import com.epam.drill.*

actual object TomcatTransformer {

    actual fun transform(
        className: String,
        classfileBuffer: ByteArray,
        loader: Any?,
        retrieveAdminUrl: String,
        idHeaderKey: String,
        idHeaderValue: String
//        idHeaderPair: Pair<String, String>
    ): ByteArray? {
        return TomcatTransformerStub.transform(
            className, classfileBuffer, loader, retrieveAdminUrl, idHeaderKey, idHeaderValue
        )
    }

    //todo remove duplication this functions



    //todo
//    actual
    fun idHeaderPairFromConfig(): Pair<String, String> =
        when (val groupId = agentConfig.serviceGroupId) {
            "" -> "drill-agent-id" to agentConfig.id
            else -> "drill-group-id" to groupId
        }

    fun retrieveAdminUrl(): Any? {//todo string?
        return if (secureAdminAddress != null) {
            secureAdminAddress?.toUrlString(false).toString()
        } else adminAddress?.toUrlString(false).toString()

    }
}
