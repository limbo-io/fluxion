/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.common.utils;

import java.util.Locale;

public final class PropertyUtils {

    public static final String PREFIX_IS = "is";
    public static final String PREFIX_GET = "get";
    public static final String PREFIX_SET = "set";

    public static String methodToProperty(String name) {
        if (name.startsWith(PREFIX_IS)) {
            name = name.substring(2);
        } else if (name.startsWith(PREFIX_GET) || name.startsWith(PREFIX_SET)) {
            name = name.substring(3);
        } else {
            throw new RuntimeException(
                "Error parsing property name '" + name + "'.  Didn't start with '" + PREFIX_IS + "', '" + PREFIX_GET + "' or '" + PREFIX_SET + "'.");
        }

        if (name.length() == 1 || name.length() > 1 && !Character.isUpperCase(name.charAt(1))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }

        return name;
    }

    public static boolean isProperty(String name) {
        return isGetter(name) || isSetter(name);
    }

    public static boolean isGetter(String name) {
        return name.startsWith(PREFIX_GET) && name.length() > 3 || name.startsWith(PREFIX_IS) && name.length() > 2;
    }

    public static boolean isSetter(String name) {
        return name.startsWith(PREFIX_SET) && name.length() > 3;
    }

}