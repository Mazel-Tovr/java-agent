package com.epam.drill.agent.instrument

import com.alibaba.ttl.internal.javassist.*
import com.epam.drill.kni.*
import com.epam.drill.logger.*
import com.epam.drill.request.*
import java.io.*
import kotlin.reflect.jvm.*

@Kni
actual object TomcatTransformer {
    private val logger = Logging.logger(Transformer::class.jvmName)

    /*
                                todo return:
                                drillResponse.addHeader(${idHeaderPair.first}, "${idHeaderPair.second});
                                drillResponse.addHeader("drill-admin-url", ${retrieveAdminUrl().toString()});

                                //drillResponse.addHeader(${idHeaderPair.first}, ${idHeaderPair.second});
     */
    actual fun transform(
        className: String,
        classfileBuffer: ByteArray,
        loader: Any?,
        retrieveAdminUrl: String,
        idHeaderKey: String,
        idHeaderValue: String
        //        idHeaderPair: Pair<String, String>,
        ): ByteArray? {
        return try {
            logger.warn { "start TomcatTransformer" }
//            val idHeaderPair = idHeaderPairFromConfig()
//            val idHeaderPair: Pair<String, String> = "drill-agent-id" to "Petclinic"
            ClassPool.getDefault().appendClassPath(LoaderClassPath(loader as? ClassLoader))
            ClassPool.getDefault().makeClass(ByteArrayInputStream(classfileBuffer))?.run {
                getMethod(
                    "doFilter",
                    "(Ljavax/servlet/ServletRequest;Ljavax/servlet/ServletResponse;)V"
                )?.insertBefore(//todo does instanceof cut needed requests or not?
                    """
                        if ($1 instanceof org.apache.catalina.connector.RequestFacade) { 
                            //java.lang.System.out.println(((org.apache.catalina.connector.RequestFacade)$1).getHeader("host"));
                            org.apache.catalina.connector.RequestFacade drillRequest = (org.apache.catalina.connector.RequestFacade)$1;
                            org.apache.catalina.connector.ResponseFacade drillResponse = (org.apache.catalina.connector.ResponseFacade)$2;
                            java.lang.System.out.println(drillRequest.getRequestURL());
                            java.util.Map/*<java.lang.String, java.lang.String>*/ drillHeaders = new java.util.HashMap();
                            java.util.Enumeration/*<String>*/ headerNames = drillRequest.getHeaderNames();
                            while (headerNames.hasMoreElements()) {
                                java.lang.String headerName = (java.lang.String) headerNames.nextElement();
                                java.lang.String header = drillRequest.getHeader(headerName);
                                //java.lang.System.out.println(headerName+" "+ header);
                                drillHeaders.put(headerName, header);
                                if (headerName.startsWith("drill-")) {
                                     drillResponse.addHeader(headerName, header);
                                     //todo is it need?
                                }
                            }
                            if (drillResponse.getHeader("drill-admin-url")!="localhost:8090") {
                                drillResponse.addHeader("drill-admin-url", "$retrieveAdminUrl");
                                drillResponse.addHeader("$idHeaderKey", "$idHeaderValue");
                            }
                            java.lang.System.out.println(drillResponse.getHeader("drill-admin-url"));
                            com.epam.drill.request.HttpRequest.INSTANCE.${HttpRequest::parse2.name}(drillHeaders);
                        }
                    """.trimIndent()
                ) ?: run {
                    return null
                }
                /**
                 * com.epam.drill.request.HttpRequest.INSTANCE.${HttpRequest::parse2.name}(((org.apache.catalina.connector.RequestFacade)$1).getHeader("drill-session-id"));
                 */
                return toBytecode()


            }
        } catch (e: Exception) {
            logger.warn(e) { "Instrumentation error" }
            null
        }
    }

//    actual external fun retrieveAdminUrl(): Any?

    //todo
//    actual external
    private fun idHeaderPairFromConfig(): Pair<String, String> = "drill-agent-id" to "Petclinic"
}
