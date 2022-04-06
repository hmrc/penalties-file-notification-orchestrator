
# penalties-file-notification-orchestrator

PENALTIES FILE NOTIFICATION ORCHESTRATOR has 2 endpoints

POST        /new-notifications                    controllers.OrchestratorController.receiveSDESNotifications
new-notifications is a new notification service for backend services.


POST        /sdes-callback                        controllers.SDESCallbackController.handleCallback
sde will pull fromm file upload, process the file and message is sent back with the status.


## Running

This application runs on port 9184.

You can use the ./run.sh to run the service.

The user must have an authenticated session and be enrolled in MTD VAT to access most pages of this service.

The service manager configuration name for this service is: PENALTIES_FILE_NOTIFICATION_ORCHESTRATOR

This service is dependent on other services, all dependent services can be started with sm --start PENALTIES_ALL (this will also start the penalties frontend microservice so you may need to stop it via sm --stop PENALTIES_FILE_NOTIFICATION_ORCHESTRATOR).

## Testing

This service can be tested with SBT via sbt test it:test

To run coverage and scalastyle, please run: sbt clean scalastyle coverage test it:test coverageReport

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").