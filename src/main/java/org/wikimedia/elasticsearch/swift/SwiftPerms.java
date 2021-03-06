/*
 * Copyright 2017 Wikimedia and BigData Boutique
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

package org.wikimedia.elasticsearch.swift;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.elasticsearch.SpecialPermission;

/**
 * FIXME: remove this boiler plate code if
 * https://github.com/javaswift/joss/pull/106 is merged
 */
public class SwiftPerms {
    public static <T> T exec(final PrivilegedAction<T> callable) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
            return AccessController.<T>doPrivileged(callable);
        }
        return callable.run();
    }
}
