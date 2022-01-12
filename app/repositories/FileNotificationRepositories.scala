
package repositories

import javax.inject.Inject
import models.notification.SDESNotification
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class FileNotificationRepositories @Inject()() {

  def storeFileNotifications(notifications: Seq[SDESNotification]): Future[HttpResponse] = {
    ???
  }
}
