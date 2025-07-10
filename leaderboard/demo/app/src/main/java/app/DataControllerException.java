/*
 * Copyright 2025 Google LLC
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

package app;

/**
 * Exception thrown when a service operation fails.
 */
class DataControllerException extends RuntimeException {

  /**
   * Constructs a new DataControllerException with the specified detail message.
   *
   * @param message the detail message
   */
  DataControllerException(final String message) {
    super(message);
  }

  /**
   * Constructs a new DataControllerException
   * with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause   the cause of the exception
   */
  DataControllerException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
