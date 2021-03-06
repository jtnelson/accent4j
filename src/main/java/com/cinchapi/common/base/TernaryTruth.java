/*
 * Copyright (c) 2016 Cinchapi Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cinchapi.common.base;

/**
 * An enum to encapsulate the possible truth values in a ternary (three-valued)
 * system as well as some of the possible operations.
 * 
 * @author Jeff Nelson
 */
public enum TernaryTruth {
    TRUE, FALSE, UNSURE;

    /**
     * Return the boolean value that for this ternary truth value once converted
     * to two-valued logic.
     * 
     * @return the two-valued logic analog for this
     */
    public boolean boolValue() {
        if(this == TRUE) {
            return true;
        }
        else {
            return false;
        }
    }

}
