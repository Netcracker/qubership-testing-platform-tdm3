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

package org.qubership.atp.tdm.env.configurator.model.envgen;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ConnectionType {

    DB("DB"),
    DDRS("DDRS"),
    DIAMETER_SYNCHRONOUS("Diameter Synchronous"),
    FILE_OVER_FTP("File over FTP"),
    FILE_OVER_SFTP("File over SFTP"),
    FILE_OVER_SMB("File over SMB"),
    GIT("GIT"),
    HTTP("HTTP"),
    HTTP_CIP("HTTP-CIP"),
    HTTP_CONSUL("HTTP-Consul"),
    HTTP_KUBERNETE_PROJECT("HTTP-KubernetesProject"),
    HTTP_OPENSHIFT_PROJECT("HTTP-OpenShiftProject"),
    HTTP_OPENSHIFT_ROUT("HTTP-OpenShiftRout"),
    JMS_ASYNCHRONOUS("JMS Asynchronous"),
    LDAP("LDAP"),
    REST_OVER_HTTP("REST over HTTP"),
    REST_SYNCHRONOUS("REST Synchronous"),
    SOAP_OVER_HTTP_SYNCHRONOUS("SOAP Over HTTP Synchronous"),
    SOAP_OVER_JMS("SOAP Over JMS"),
    SS7_TRANSPORT("SS7 Transport"),
    SSH("SSH"),
    TA_ENGINES_PROVIDER("TA Engines Provider");

    private final String value;

    ConnectionType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    @JsonCreator
    public static ConnectionType fromValue(String value) {
        for (ConnectionType connectionType : ConnectionType.values()) {
            if (connectionType.toString().equalsIgnoreCase(value)) {
                return connectionType;
            }
        }
        throw new IllegalArgumentException("Unexpected enum reference regeneration class value '" + value + "'.");
    }
}
