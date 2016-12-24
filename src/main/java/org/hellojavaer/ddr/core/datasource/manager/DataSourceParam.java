/*
 * Copyright 2016-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hellojavaer.ddr.core.datasource.manager;

import java.util.Set;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 19/11/2016.
 */
public class DataSourceParam {

    private Set<String> scNames;
    private boolean     readOnly;

    public Set<String> getScNames() {
        return scNames;
    }

    public void setScNames(Set<String> scNames) {
        this.scNames = scNames;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\"readOnly\":");
        sb.append(readOnly);
        sb.append(',');
        sb.append("\"scNames\":");
        if (scNames == null) {
            sb.append("null");
        } else {
            sb.append('[');
            for (String scName : scNames) {
                sb.append('\"');
                sb.append(scName);
                sb.append('\"');
                sb.append(',');
            }
            if (!scNames.isEmpty()) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }
}
