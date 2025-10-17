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

import org.qubership.atp.tdm.exceptions.internal.TdmValidateCronException;
import org.junit.Assert;
import org.junit.Test;

public class ValidateCronExpressionTest {

    @Test
    public void validateCronExpression_saveCronEverySecondUseStar_returnException() {
        String incorrectCron = "* 0 9 ? * *";
        try {
            ValidateCronExpression.validate(incorrectCron);
        } catch (Exception e) {
            String errorMessage = String.format(TdmValidateCronException.DEFAULT_MESSAGE, incorrectCron);
            Assert.assertEquals(errorMessage, e.getMessage());
        }
    }

    @Test
    public void validateCronExpression_saveCronEverySecondUseSlash_returnException() {
        String incorrectCron = "0/1 5 9 ? * *";
        try {
            ValidateCronExpression.validate(incorrectCron);
        } catch (Exception e) {
            String errorMessage = String.format(TdmValidateCronException.DEFAULT_MESSAGE, incorrectCron);
            Assert.assertEquals(errorMessage, e.getMessage());
        }
    }

    @Test
    public void validateCronExpression_saveCronEveryMinute_returnException() {
        String incorrectCron = "0 * 9 ? * *";
        try {
            ValidateCronExpression.validate(incorrectCron);
        } catch (Exception e) {
            String errorMessage = String.format(TdmValidateCronException.DEFAULT_MESSAGE, incorrectCron);
            Assert.assertEquals(errorMessage, e.getMessage());
        }
    }

}
