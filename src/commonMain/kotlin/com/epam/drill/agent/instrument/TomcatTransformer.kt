package com.epam.drill.agent.instrument

expect object TomcatTransformer {
    fun transform(
        className: String,
        classfileBuffer: ByteArray,
        loader: Any?,
        retrieveAdminUrl: String,
        idHeaderKey: String,
        idHeaderValue: String
//        idHeaderPair: Pair<String, String>,
    ): ByteArray?
//todo ?
//    fun retrieveAdminUrl(): Any?
//    fun idHeaderPairFromConfig(): Pair<String, String>
}
