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

package scheduler.jobs

import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import scheduler.ScheduledJob
import scheduler.SchedulingActor.SendFileNotificationsToSDESMessage
import services.SendFileNotificationsToSDESService

import javax.inject.Inject

class SendFileNotificationsToSDESJob @Inject()(val config: Configuration,
                                               val sendFileNotificationsToSDESService: SendFileNotificationsToSDESService,
                                               val applicationLifecycle: ApplicationLifecycle
                                              ) extends ScheduledJob {

  val jobName: String           = "SendFileNotificationsToSDESJob"
  val actorSystem: ActorSystem  = ActorSystem(jobName)
  val scheduledMessage          = SendFileNotificationsToSDESMessage(sendFileNotificationsToSDESService)

  schedule
}
