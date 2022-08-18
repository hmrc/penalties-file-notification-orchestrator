
# Penalties File Notification Orchestrator

This service accepts notifications to be sent to downstream services and processes batches of notifications at timed intervals, whilst handling the processing outcome of those notifications via callbacks.

The service has 2 endpoints

POST        /new-notifications                    controllers.OrchestratorController.receiveSDESNotifications
This endpoint accepts new notifications to be processed by the batch job and sent to SDES.


POST        /sdes-callback                        controllers.SDESCallbackController.handleCallback
SDES will pull from file upload, process the file and message is sent back with the status.


## Running

This application runs on port 9184.

You can use the ./run.sh to run the service.

The service manager configuration name for this service is: PENALTIES_FILE_NOTIFICATION_ORCH

This service is dependent on other services, all dependent services can be started with `sm --start PENALTIES_ALL` (this will also start the penalties file notification orchestrator microservice so you may need to stop it via `sm --stop PENALTIES_FILE_NOTIFICATION_ORCHESTRATOR`).

## Testing

This service can be tested with SBT via `sbt test it:test`

To run coverage and scalastyle, please run: `sbt clean scalastyle coverage test it:test coverageReport`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").