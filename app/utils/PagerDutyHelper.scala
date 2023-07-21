/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

import utils.Logger.logger

object PagerDutyHelper {
  object PagerDutyKeys extends Enumeration {
    final val RECEIVED_4XX_FROM_SDES = Value
    final val RECEIVED_5XX_FROM_SDES = Value
    final val UNKNOWN_EXCEPTION_FROM_SDES = Value
    final val FAILED_TO_PROCESS_FILE_NOTIFICATION = Value
    final val UNKNOWN_PROCESSING_EXCEPTION = Value
    final val MONGO_LOCK_UNKNOWN_EXCEPTION = Value
    final val FAILED_TO_INSERT_SDES_NOTIFICATION = Value
    final val FAILED_TO_VALIDATE_REQUEST_AS_JSON = Value
    final val FAILED_TO_PARSE_REQUEST_TO_MODEL = Value
    final val FAILED_TO_INSERT_FILE_NOTIFICATION = Value
    final val NOTIFICATION_SET_TO_PERMANENT_FAILURE = Value
    final val NOTIFICATION_SET_TO_NOT_PROCESSED_PENDING_RETRY = Value
    final val NOTIFICATION_SET_TO_NOT_RECEIVED_IN_SDES_PENDING_RETRY = Value
  }

  def log(methodName: String, pagerDutyKey: PagerDutyKeys.Value): Unit = {
    logger.warn(s"$pagerDutyKey - $methodName")
  }

  def logStatusCode(methodName: String, code: Int)(keyOn4xx: Option[PagerDutyKeys.Value] = None, keyOn5xx: Option[PagerDutyKeys.Value] = None): Unit = {
    code match {
      case code if code >= 400 && code <= 499 => log(methodName, keyOn4xx.get)
      case code if code >= 500 => log(methodName, keyOn5xx.get)
      case _ =>
    }
  }
}
