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

package org.qubership.atp.tdm.utils;

import org.quartz.CronExpression;
import org.qubership.atp.tdm.exceptions.internal.TdmValidateCronException;

public class ValidateCronExpression {

    /**
     * Validate cron.
     * @param expression expression
     */
    public static void validate(String expression) {
        try {
            if (expression != null) {
                CronExpression.validateExpression(expression);
                if (!isValidateCrone(expression)) {
                    throw new RuntimeException();
                }
            } else {
                throw new RuntimeException();
            }
        } catch (Exception e) {
            throw new TdmValidateCronException(expression);
        }
    }

    private static boolean isValidateCrone(String cron) {
        String[] cronValues = cron.trim().replaceAll("[\\s]+", " ").split(" ");
        return !cronValues[0].equals("*") && cronValues[0].length() < 3
                && !cronValues[1].equals("*");
    }
}
