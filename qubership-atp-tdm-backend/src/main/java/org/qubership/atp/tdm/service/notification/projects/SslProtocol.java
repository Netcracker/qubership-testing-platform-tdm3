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

package org.qubership.atp.tdm.service.notification.projects;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.gson.annotations.SerializedName;

public enum SslProtocol {
    @SerializedName("TLS")
    TLS("TLS"),
    @SerializedName("TLSv1")
    TLSV1("TLSv1"),
    @SerializedName("TLSv1.1")
    TLSV1_1("TLSv1.1"),
    @SerializedName("TLSv1.2")
    TLSV1_2("TLSv1.2"),
    @SerializedName("TLSv1.3")
    TLSV1_3("TLSv1.3"),
    @SerializedName("SSL")
    SSL("SSL"),
    @SerializedName("SSLv2")
    SSLV2("SSLv2"),
    @SerializedName("SSLv3")
    SSLV3("SSLv3");

    @JsonValue
    private String name;

    SslProtocol(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
