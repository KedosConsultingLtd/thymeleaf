/*
 * =============================================================================
 *
 *   Copyright (c) 2026 Kedos Consulting Limited
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * =============================================================================
 */
package org.thymeleaf.exceptions;

/**
 * <p>
 *   Thrown by an {@link org.thymeleaf.messageresolver.IMessageResolver} to signal that a message key could not be
 *   found and that the template should fail rather than render an <em>absent message representation</em>
 *   (e.g. {@code ??key_en_GB??}).
 * </p>
 * <p>
 *   The engine treats it as "not found" first: remaining message resolvers are still tried, and lookups that ask
 *   for {@code null} when a message is missing (e.g. {@code #messages.msgOrNull(...)}) return {@code null}. The
 *   exception only propagates when no resolver finds the message and an absent message representation would
 *   otherwise have been produced (e.g. {@code #{...}} or {@code #messages.msg(...)}).
 * </p>
 */
public class MessageNotFoundException extends TemplateProcessingException {

    private static final long serialVersionUID = 6302861853267094751L;


    public MessageNotFoundException(final String message) {
        super(message);
    }

    public MessageNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
