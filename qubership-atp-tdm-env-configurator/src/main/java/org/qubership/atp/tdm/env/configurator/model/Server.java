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

package org.qubership.atp.tdm.env.configurator.model;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {
    private static final int MILLISECONDS = 1000;

    private final Connection connection;
    private final String name;

    /**
     * Class constructor.
     *
     * @param connection - connection.
     * @param name       - name.
     */
    public Server(Connection connection, String name) {
        this.name = name;
        this.connection = connection;
    }

    public String getName() {
        return name;
    }

    public String getUser() {
        return getProperty(name + "_login");
    }

    public String getPass() {
        return getProperty(name + "_password");
    }

    public String getKey() {
        return getProperty(name + "_key");
    }

    public String getPassPhrase() {
        return getProperty(name + "_passphrase");
    }

    /**
     * Get pty from environment. If not defined or incorrect value then 'true'.
     *
     * @return pty from environment. {@code true} if not defined or incorrect value
     */
    public boolean getPty() {
        final String pty = getProperty(name + "_pty");
        if (pty != null) {
            try {
                return Boolean.parseBoolean(pty);
            } catch (Exception e) {
                //nothing
            }
        }
        return true;
    }

    /**
     * Get timeout connect from environment. If not defined or incorrect value then 1 minute.
     *
     * @return timeout connect from environment. 1 minute if not defined or incorrect value
     */
    public int getTimeoutConnect() {
        return getTimeout("connect", 60 * MILLISECONDS);
    }

    /**
     * Get timeout execute from environment. If not defined or incorrect value then 60 minute.
     *
     * @return timeout execute from environment. 60 minute if not defined or incorrect value
     */
    public int getTimeoutExecute() {
        return getTimeout("execute", 60 * 60 * MILLISECONDS);
    }

    public String getProperty(String key) {
        return connection.getParameters().get(key);
    }

    public Map<String, String> getProperties() {
        return connection.getParameters();
    }

    public String getHostFull() {
        return getProperty(name + "_host");
    }

    /**
     * Gets host from property.
     *
     * @return host, NullPointerException otherwise
     */
    public String getHost() {
        Pattern pattern = Pattern.compile("([^:^]*)(:\\d*)?(.*)?");
        Matcher matcher = pattern.matcher(getHostFull());
        matcher.find();
        String host = matcher.group(1);
        return host;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Server server = (Server) o;
        return Objects.equals(connection, server.connection)
                && Objects.equals(name, server.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection, name);
    }

    /**
     * Get timeout from environment. If not defined or incorrect value then {@code defaultValue}.
     *
     * @param timeoutPostfix postfix of timeout
     * @param defaultValue   default value to return
     * @return timeout from environment. {@code defaultValue} if not defined or incorrect value
     */
    private int getTimeout(String timeoutPostfix, int defaultValue) {
        final String timeout = getProperty(name + "_timeout_" + timeoutPostfix);
        if (timeout != null) {
            try {
                return Integer.parseInt(timeout);
            } catch (Exception e) {
                //nothing
            }
        }
        return defaultValue;
    }

}
