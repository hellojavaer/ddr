/*
 * Copyright 2016-2017 the original author or authors.
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
package org.hellojavaer.ddr.core.shard.exception;

import org.hellojavaer.ddr.core.sqlparse.exception.DDRSQLParseException;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/01/2017.
 */
public class ShardRoutingException extends DDRSQLParseException {

    public ShardRoutingException() {
    }

    public ShardRoutingException(String message) {
        super(message);
    }

    public ShardRoutingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ShardRoutingException(Throwable cause) {
        super(cause);
    }

    public ShardRoutingException(String message, Throwable cause, boolean enableSuppression,
                                 boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
