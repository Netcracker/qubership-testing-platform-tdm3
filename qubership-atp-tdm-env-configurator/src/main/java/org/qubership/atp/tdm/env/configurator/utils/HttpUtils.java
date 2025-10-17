/*
 *  Copyright 2024-2025 NetCracker Technology Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.qubership.atp.tdm.env.configurator.utils;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;

public class HttpUtils {

    /**
     * Build http client.
     *
     * @return http client builder.
     */
    public static org.apache.hc.client5.http.impl.classic.HttpClientBuilder createTrustAllHttpClientBuilder() {
        try {
            org.apache.hc.core5.ssl.SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, (chain, authType) -> true);
            org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory sslsf = new
                    SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
            PoolingHttpClientConnectionManager connectionManager =
                    PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(sslsf).build();
            return HttpClients.custom().setConnectionManager(connectionManager);
        } catch (Exception e) {
            return HttpClientBuilder.create();
        }
    }
}
